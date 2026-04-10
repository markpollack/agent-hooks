# Learnings — agent-hooks

## Stage 1: Foundation + Core API

### Version alignment (Step 1.0)
- Spring AI 2.0.0-M3 + Spring Boot 4.1.0-M2 — matches workshop `art-of-building-agents`
- `spring-ai-client-chat` artifact name unchanged in 2.0.0-M3
- `MethodToolCallbackProvider` in `spring-ai-model` (transitive dep) — accessible from agent-hooks-spring
- ToolContext is immutable — confirms need for separate HookContext

### Core API (Steps 1.1-1.2)
- 8 source files in agent-hooks-core: AgentHookEvent, HookDecision (sealed, 4 variants), ToolCallRecord, HookContext, HookInput (sealed, 4 variants), AgentHook, AgentHookProvider, AgentHookRegistry
- AfterToolCall includes hookContext (addition to original DESIGN.md)
- Registry dispatch: priority ordering → Block short-circuits → Modify chains → exception = Proceed
- Default priority: 100. Tool pattern matching via regex. Validation: Retry only for AFTER_TOOL_CALL, Block/Modify only for BEFORE_TOOL_CALL

## Stage 2: Spring AI Adapter

### HookedToolCallback (Step 2.1)
- Wraps ToolCallback: BEFORE_TOOL_CALL dispatch → delegate.call() → AFTER_TOOL_CALL dispatch
- Block returns reason as tool result, delegate never called; blocked calls still recorded in history
- Modify passes modified input to delegate
- Exception in delegate captured in AfterToolCall, then re-thrown
- Build from reactor root (`./mvnw test`), not `-pl agent-hooks-spring` alone (core not in local repo)

### Provider + Auto-Config (Step 2.2)
- HookedToolCallbackProvider wraps ToolCallbackProvider — each ToolCallback becomes HookedToolCallback
- HookedTools.wrap(registry, hookContext, toolObjects...) — main entry point for workshop
- AgentHooksAutoConfiguration: @AutoConfiguration @ConditionalOnClass(ToolCallback.class)
  - Creates registry from all AgentHookProvider beans
  - Creates default HookContext bean
  - Both @ConditionalOnMissingBean — user can override
- ApplicationContextRunner for testing auto-config (fast, no full Spring context)

### Test count
- 35 total: 22 core + 9 HookedToolCallback + 4 auto-config

## Stage 3: Event Hierarchy Refactoring (v0.1 → v0.2)

### Design decision
- Option B (open event hierarchy) chosen over closed enum — matches Strands SDK pattern
- Event IS the input — eliminated parallel `AgentHookEvent` enum + `HookInput` sealed interface
- `HookEvent` (unsealed) → `ToolEvent` (sub-interface) → `BeforeToolCall`/`AfterToolCall` records
- Non-tool events: `SessionStart`, `SessionEnd` implement `HookEvent` directly

### Registry changes
- Type-based dispatch: `Map<Class<?>, List<PrioritizedHook<?>>>` with unchecked cast at dispatch (safe via public API pairing)
- Single-arg `dispatch(HookEvent)` — event carries its own data
- `AfterToolCall` dispatches in reverse priority order (cleanup ordering)
- Non-ToolEvent decisions (Block/Modify/Retry) → treated as Proceed (runtime enforcement)

### Migration pattern
- `AgentHook<E extends HookEvent>` — generic functional interface, lambdas capture type from Class<E>
- Registration: `registry.on(BeforeToolCall.class, event -> ...)` (typed, no casts at call site)
- Spring adapter: minimal changes — just import updates and constructor calls

## Stage 5: Claude Agent SDK Adapter

### AgentHookBridge pattern
- `AgentHookBridge.registerInto(HookRegistry)` — registers all 6 Claude hook callbacks unconditionally
- Converts Claude SDK types (`HookInput` variants) to core events (`BeforeToolCall`/`AfterToolCall`) and Claude-specific events
- `DecisionMapper` maps `HookDecision` → `HookOutput` (Proceed→allow, Block→block+deny, Modify→allow+modifyMap, Retry→warn+allow)
- Duration tracking via `ConcurrentHashMap<String, Instant>` keyed by `toolUseId` — pre-hook captures start, post-hook computes delta
- Per-session `HookContext` via `ConcurrentHashMap<String, HookContext>` keyed by `sessionId`

### Claude-specific events
- 4 new event records: `UserPromptSubmit`, `AgentStop`, `SubagentStop`, `PreCompact`
- All implement `HookEvent` (not `ToolEvent`) — observation-only, Block/Modify/Retry treated as Proceed
- `PreCompact.trigger()` and `PreCompact.customInstructions()` are `@Nullable`

### Type conversions
- `PreToolUseInput.toolInput()` is `Map<String,Object>` → serialized to JSON string via Jackson for `BeforeToolCall.toolInput()`
- `HookDecision.Modify.modifiedInput()` (JSON string) → parsed back to `Map<String,Object>` for `HookSpecificOutput.preToolUseModify()`
- `PostToolUseInput.toolResponse()` is `Object` → converted to String (pass-through if already String, else Jackson serialize)

### Cross-adapter proof
- Same `AgentHookProvider` (blocks "Bash") tested on both Claude and Spring paths — the value proposition
- `agent-hooks-spring` is a test-only dependency in `agent-hooks-claude` — no runtime coupling

### Java 17 compatibility
- Switch pattern matching not available in Java 17 — use if/instanceof chains in DecisionMapper
- `HookRegistry.registerSubagentStop()` and `registerPreCompact()` don't exist — use `register(HookEvent, toolPattern, callback)`

### Test count
- 23 tests in agent-hooks-claude (6 event + 11 bridge + 4 cross-adapter + 2 architecture)
- 58 total across all modules (22 core + 13 spring + 23 claude)

## Stage 6: Gemini CLI Adapter (Stateless)

### Execution model difference
- Claude adapter (Model B): bidirectional streaming, our code is the parent process, HookContext persists across events
- Gemini adapter (Model C): subprocess per event, Gemini CLI is the parent, our code is ephemeral
- HookContext is fresh per invocation — stateless hooks (security gates, audit logging) work; stateful hooks (budget tracking, tool history) silently degrade

### GeminiHookDispatcher pattern
- `GeminiHookDispatcher.create(providers...)` — factory builds registry from providers
- `run()` reads stdin, dispatches, writes stdout — production entry point for `main()`
- `dispatch(String inputJson)` — testable core: JSON in → JSON out
- Routes by `hook_event_name` field in JSON to correct event constructor
- 4 core events reused: `BeforeToolCall`, `AfterToolCall`, `SessionStart`, `SessionEnd`
- 7 Gemini-specific events: agent lifecycle, model invocation, tool selection, notifications, compression

### Decision mapping differences from Claude
- Gemini BeforeTool **cannot modify tool inputs** — only allow or block
- `HookDecision.Modify` → warning logged to stderr + `{"decision":"allow"}` (same downgrade pattern as `Retry` on Claude)
- `HookDecision.Retry` → warning logged to stderr + `{"decision":"allow"}`
- Non-tool events always return `{}` (empty JSON object)

### Stateless constraints
- `AfterToolCall.duration()` is always `Duration.ZERO` (no pre-hook timing reference)
- `AfterToolCall.exception()` is always `null`
- `SessionEnd.totalDuration()` is always `Duration.ZERO`
- `context.history()` is always empty
- All logging to stderr (stdout reserved for JSON response)
- Malformed JSON → `{}` to stdout, error to stderr, exit 0 (never block the agent)

### JSON handling
- `tool_input` and `tool_response` (JSON objects in stdin) → serialized to JSON string for core event records
- Opaque JSON fields (`llm_request`, `llm_response`, `details`) passed as raw JSON strings — consumers parse themselves
- Jackson is compile scope (no SDK provides it) unlike Claude adapter where it's provided

### Cross-adapter proof — 3 runtimes
- Same `bashBlocker` AgentHookProvider blocks via `GeminiHookDispatcher` (Gemini), `AgentHookBridge` (Claude), `HookedToolCallback` (Spring)
- `agent-hooks-gemini` is a test-only dependency in `agent-hooks-claude` for the cross-adapter test
- 6 cross-adapter tests: block + allow × 3 runtimes

### Test count
- 28 tests in agent-hooks-gemini (9 event + 16 dispatcher + 3 architecture)
- 102 total across all modules (30 core + 19 spring + 25 claude + 28 gemini)
