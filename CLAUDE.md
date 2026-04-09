# CLAUDE.md

## Project Overview

**agent-hooks** is a portable hook API for steering agent behavior at the tool-call boundary. Write your hook once, and it works across any agent runtime that has an adapter.

- **agent-hooks-core**: Pure Java 17 API — events, inputs, decisions, registry. Zero dependencies.
- **agent-hooks-spring**: Spring AI adapter — wraps `ToolCallback` with hook dispatch, auto-configures via Spring Boot.
- **agent-hooks-claude**: Claude Agent SDK adapter — bridges `AgentHookProvider` implementations to Claude CLI hooks via `AgentHookBridge`.

**Group ID**: `io.github.markpollack` (candidate for `org.springaicommunity` once validated)

## Build Commands

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
```

## Key Design Decisions

1. **Core has zero dependencies** — portable across Spring AI, Claude SDK, any Java agent runtime
2. **Open event hierarchy** (v0.2) — `HookEvent` is unsealed, event IS the input (no parallel hierarchies)
3. **Generic `AgentHook<E>`** — type-safe registration: `registry.on(BeforeToolCall.class, event -> ...)`
4. **Block short-circuits** — security hooks can't be overridden by later hooks
5. **Modify chains** — subsequent hooks see modified input
6. **Reverse priority for AfterToolCall** — cleanup ordering (highest priority last)
7. **Runtime enforcement** — Block/Modify/Retry on non-ToolEvent → treated as Proceed
8. **HookContext** (not ToolContext) for session state — ToolContext is immutable in Spring AI
9. **Model-call events** deferred to Spring adapter only — not portable across CLIs

## Integration Context

| Project | Relationship |
|---------|-------------|
| Spring AI | Wraps ToolCallback — no core changes needed |
| claude-agent-sdk-java | Claude SDK provides hook types; agent-hooks-claude bridges to our registry |
| agent-journal | Bridge: hook provider that logs events to a journal Run |
| agent-harness | ChatClientStep gets hooks for free via Spring auto-config |

## Version Alignment

- **Spring AI**: 2.0.0-M3 (aligned with workshop `art-of-building-agents`)
- **Spring Boot**: 4.1.0-M2
- **License**: BSL 1.1 (Change Date: 2029-04-01 → Apache 2.0)

## Quality Standards

- AssertJ for assertions
- BDD-style test naming: `methodShouldBehaviorWhenCondition()`
- Coverage targets: 80% core, 70% spring, 70% claude
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
- `AgentHooksAutoConfiguration`: creates registry from AgentHookProvider beans + default HookContext
- Build from reactor root (`./mvnw test`), not `-pl agent-hooks-spring` alone

## Claude Adapter Summary

- `AgentHookBridge`: registers 6 callbacks into Claude SDK `HookRegistry` — converts `HookInput` → core/Claude events → dispatches through `AgentHookRegistry` → maps `HookDecision` → `HookOutput`
- `DecisionMapper`: Proceed→allow, Block→block+deny, Modify→allow+modifyMap, Retry→warn+allow
- 4 Claude-specific events: `UserPromptSubmit`, `AgentStop`, `SubagentStop`, `PreCompact` — all observation-only
- Duration tracking: `ConcurrentHashMap<toolUseId, Instant>` — pre-hook captures start, post-hook computes delta
- Session isolation: `ConcurrentHashMap<sessionId, HookContext>` — one HookContext per Claude session
- Claude SDK dependency is `provided` scope — users bring `claude-code-sdk` at runtime
- Cross-adapter proof: same `AgentHookProvider` works on both Claude and Spring paths
- 58 tests total (22 core + 13 spring + 23 claude)

## Session Behavior

Follow ROADMAP steps. Write tests before implementation. Create learnings after each step.
