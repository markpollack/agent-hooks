# CLAUDE.md

## Project Overview

**agent-hooks** is a portable hook API for steering agent behavior at the tool-call boundary. Write your hook once, and it works across any agent runtime that has an adapter.

- **agent-hooks-core**: Pure Java 17 API — events, inputs, decisions, registry. No framework dependency; its only compile dependency is the `org.jspecify` nullness annotations.
- **agent-hooks-spring**: Spring AI adapter — wraps `ToolCallback` with hook dispatch, auto-configures via Spring Boot.
- **agent-hooks-claude**: Claude Agent SDK adapter — bridges `AgentHookProvider` implementations to Claude CLI hooks via `AgentHookBridge`.
- **agent-hooks-gemini**: Gemini CLI adapter — stateless stdin/stdout dispatcher, maps Gemini JSON protocol to core events.

**Group ID**: `io.github.markpollack` — the published, settled coordinate for every module. There is no pending move to `org.springaicommunity`.

## Build Commands

Build from the reactor root. Vulnerability scanning is not run in GitHub Actions; use
`scripts/security-scan.sh` against a validated, frozen offline database.

```bash
# Build all modules
./mvnw compile

# Run all tests
./mvnw test

# Run tests with coverage
./mvnw test
# Reports: */target/site/jacoco/index.html

# Build specific module
./mvnw compile -pl agent-hooks-core
./mvnw compile -pl agent-hooks-spring

# Run a single test
./mvnw test -pl agent-hooks-core -Dtest=AgentHookRegistryTest
```

## Source Material Routing

| Document | Path | Read when... |
|----------|------|-------------|
| VISION.md | `plans/VISION.md` | Always read first |
| DESIGN.md | `plans/DESIGN.md` | Before implementation |
| ROADMAP.md | `plans/ROADMAP.md` | Before starting any step |

## Architecture (v0.2)

```
agent-hooks-core (pure Java)
├── event/
│   ├── HookEvent            — Unsealed interface (context(): HookContext)
│   ├── ToolEvent            — Sub-interface (toolName(), toolInput())
│   ├── BeforeToolCall       — record implements ToolEvent
│   ├── AfterToolCall        — record implements ToolEvent (+result, duration, exception)
│   ├── SessionStart         — record implements HookEvent (observation-only)
│   └── SessionEnd           — record implements HookEvent (observation-only)
├── decision/
│   ├── HookDecision         — sealed: Proceed, Block, Modify, Retry
│   ├── HookContext          — Mutable session state + tool call history
│   └── ToolCallRecord       — Immutable record of a tool execution
├── spi/
│   ├── AgentHook<E>         — @FunctionalInterface: HookDecision handle(E event)
│   └── AgentHookProvider    — registerHooks(AgentHookRegistry)
└── registry/
    └── AgentHookRegistry    — on(Class<E>, hook), onTool(), dispatch(HookEvent)

agent-hooks-spring (Spring AI adapter)
├── HookedToolCallback       — Wraps ToolCallback with before/after hook dispatch
├── HookedToolCallbackProvider — Wraps all ToolCallbackProviders
├── HookedTools              — Static utility: wrap(registry, context, toolObjects...)
└── AgentHooksAutoConfiguration — Spring Boot auto-config

agent-hooks-claude (Claude Agent SDK adapter)
├── event/
│   ├── UserPromptSubmit     — record implements HookEvent (observation-only)
│   ├── AgentStop            — record implements HookEvent (observation-only)
│   ├── SubagentStop         — record implements HookEvent (observation-only)
│   └── PreCompact           — record implements HookEvent (observation-only)
└── bridge/
    ├── AgentHookBridge      — registerInto(HookRegistry) — registers 6 callbacks
    └── DecisionMapper       — HookDecision → HookOutput (package-private)

agent-hooks-gemini (Gemini CLI adapter — stateless)
├── event/
│   ├── GeminiBeforeAgent    — record implements HookEvent (observation-only)
│   ├── GeminiAfterAgent     — record implements HookEvent (observation-only)
│   ├── GeminiBeforeModel    — record implements HookEvent (opaque llmRequestJson)
│   ├── GeminiAfterModel     — record implements HookEvent (opaque request/response)
│   ├── GeminiBeforeToolSelection — record implements HookEvent (observation-only)
│   ├── GeminiNotification   — record implements HookEvent (@Nullable detailsJson)
│   └── GeminiPreCompress    — record implements HookEvent (observation-only)
└── dispatcher/
    ├── GeminiHookDispatcher — create(providers...), run(), dispatch(json)
    └── GeminiDecisionMapper — HookDecision → Gemini JSON (package-private)
```

## Key Design Decisions

1. **Core carries no framework dependency** — portable across Spring AI, Claude SDK, any Java agent
   runtime. `org.jspecify:jspecify` (annotations only) is its single compile dependency; logging uses
   `java.util.logging` from the JDK rather than adding SLF4J.
2. **Open event hierarchy** (v0.2) — `HookEvent` is unsealed, event IS the input (no parallel hierarchies)
3. **Generic `AgentHook<E>`** — type-safe registration: `registry.on(BeforeToolCall.class, event -> ...)`
4. **Block short-circuits** — security hooks can't be overridden by later hooks. A hook that
   *throws*, however, degrades to Proceed (logged at WARNING) — it does not fail closed.
5. **Modify chains** — subsequent hooks see modified input
6. **Reverse priority for AfterToolCall** — cleanup ordering (highest priority last)
7. **Runtime enforcement** — Block/Modify/Retry on non-ToolEvent → logged at WARNING and treated as
   Proceed. `Retry` from a `BeforeToolCall` hook throws `IllegalStateException` out of `dispatch`;
   adapters that must not fail the agent are responsible for containing it (the Gemini dispatcher
   does). Dispatch matches the event's **exact runtime class** — a hook registered on a supertype
   such as `ToolEvent.class` compiles but never fires.
8. **HookContext** (not ToolContext) for session state — ToolContext is immutable in Spring AI
9. **Model-call events** deferred to Spring adapter only — not portable across CLIs

## Integration Context

No published AgentWorks artifact depends on agent-hooks today; the AgentWorks BOM manages the four
modules but nothing consumes them. Verified against every artifact the BOM manages. Keep the
distinction below between shipped adapters and intended integrations.

| Project | Relationship | Status |
|---------|-------------|--------|
| Spring AI | Wraps ToolCallback — no core changes needed | shipped (`agent-hooks-spring`) |
| claude-agent-sdk-java | Claude SDK provides hook types; agent-hooks-claude bridges to our registry | shipped (`agent-hooks-claude`) |
| Gemini CLI | Stateless stdin/stdout dispatcher | shipped (`agent-hooks-gemini`) |
| agent-journal | Bridge: hook provider that logs events to a journal Run | **intended; not built** |
| agent-harness | ChatClientStep gets hooks for free via Spring auto-config | **intended; not built** |

## Version Alignment

- **Spring AI**: 2.0.0 GA
- **Spring Boot**: 4.0.7
- **Claude Agent SDK**: `claude-code-sdk` 1.4.0, `provided` scope — matches the AgentWorks BOM pin
- **Jackson**: 2.21.6 (`com.fasterxml`) and 3.1.6 (`tools.jackson`), the AgentWorks suite floors
- **Java**: 17 (`maven.compiler.release`), verified as class-file major version 61
- **Latest release**: 0.6.4. `0.7.0-SNAPSHOT` is unreleased development.
- **License**: BSL 1.1 (Change Date: 2029-04-01 → Apache 2.0). BSL from the first commit — this
  project never shipped under Apache 2.0, so there is no historical Apache boundary to preserve.
- **Resolution**: Maven Central only. The parent declares no `<repositories>`; a published POM must
  never hand a consumer extra milestone or snapshot repositories.

## Quality Standards

- AssertJ for assertions
- BDD-style test naming: `methodShouldBehaviorWhenCondition()`
- Coverage targets: 80% core, 70% spring, 70% claude, 70% gemini
- BSL 1.1 license

## Not Covered

- Model-call events in core API (Spring-specific, lives in spring adapter)
- Async hook execution (v1 is synchronous)
- YAML/properties configuration (programmatic only)
- Hook persistence or replay

## Core API Summary

- 10 source files in `agent-hooks-core`: HookEvent, ToolEvent, BeforeToolCall, AfterToolCall, SessionStart, SessionEnd, HookDecision (sealed), HookContext, AgentHook<E>, AgentHookProvider, AgentHookRegistry
- Registry: `Map<Class<?>, List<PrioritizedHook<?>>>` — type-based dispatch with unchecked cast (safe via public API pairing)
- Registration: `on(Class<E>, hook)`, `on(Class<E>, priority, hook)`, `onTool(pattern, Class<E extends ToolEvent>, hook)`
- Dispatch: `dispatch(HookEvent)` → priority ordering → Block short-circuits → Modify chains → exception = Proceed
- AfterToolCall: reverse priority order. Default priority: 100. Tool pattern via regex. Retry only for AfterToolCall.

## Spring Adapter Summary

- `HookedToolCallback`: wraps ToolCallback with BEFORE/AFTER dispatch. Block returns reason as result. Modify passes modified input.
- `HookedToolCallbackProvider`: wraps ToolCallbackProvider — each callback becomes HookedToolCallback
- `HookedTools.wrap(registry, hookContext, toolObjects...)`: main entry point for workshop usage
- `AgentHooksAutoConfiguration`: creates registry from AgentHookProvider beans + default HookContext.
  That default HookContext is an **application-wide singleton** — correct for a single-user CLI,
  wrong for a multi-user server, where you supply your own request/session-scoped bean
- Build from reactor root (`./mvnw test`), not `-pl agent-hooks-spring` alone

## Claude Adapter Summary

- `AgentHookBridge`: registers 6 callbacks into Claude SDK `HookRegistry` — converts `HookInput` → core/Claude events → dispatches through `AgentHookRegistry` → maps `HookDecision` → `HookOutput`
- `DecisionMapper`: Proceed→allow, Block→block+deny, Modify→allow+modifyMap, Retry→warn+allow
- 4 Claude-specific events: `UserPromptSubmit`, `AgentStop`, `SubagentStop`, `PreCompact` — all observation-only
- Duration tracking: `ConcurrentHashMap<toolUseId, Instant>` — pre-hook captures start, post-hook computes delta
- Session isolation: `ConcurrentHashMap<sessionId, HookContext>` — one HookContext per Claude session.
  Nothing evicts it automatically; `evictSession(String)` / `activeSessionCount()` exist for a
  long-lived bridge shared across sessions.
- Claude SDK dependency is `provided` scope — users bring `claude-code-sdk` at runtime
- Cross-adapter proof: same `AgentHookProvider` works on Claude, Spring, and Gemini paths
- 105 tests total (30 core + 19 spring + 26 claude + 30 gemini)

## Gemini Adapter Summary

- `GeminiHookDispatcher`: stateless stdin/stdout dispatcher — reads JSON from stdin, maps `hook_event_name` to core/Gemini events, dispatches through `AgentHookRegistry`, writes JSON response to stdout
- `GeminiDecisionMapper`: Proceed→`{"decision":"allow"}`, Block→`{"decision":"block","reason":"..."}`, Modify→warn+allow, Retry→warn+allow
- 7 Gemini-specific events: `GeminiBeforeAgent`, `GeminiAfterAgent`, `GeminiBeforeModel`, `GeminiAfterModel`, `GeminiBeforeToolSelection`, `GeminiNotification`, `GeminiPreCompress` — all observation-only
- 4 core events reused: `BeforeToolCall` (block-only, no Modify), `AfterToolCall`, `SessionStart`, `SessionEnd`
- Stateless: `HookContext` is fresh per invocation (Gemini spawns a new process per hook event)
- Jackson `compile` scope (no SDK provides it — raw JSON protocol)
- All logging to stderr (stdout reserved for JSON response)
- Every invocation writes exactly one JSON object to stdout. Malformed JSON, a missing/unknown
  `hook_event_name`, and any unchecked failure in a hook or the registry all yield `{}` on stdout
  plus a stderr diagnostic — a broken hook degrades to "no opinion" instead of wedging the agent
- Warning/exception messages never embed tool-input payloads (they routinely carry credentials)

## Session Behavior

Follow ROADMAP steps. Write tests before implementation. Create learnings after each step.
