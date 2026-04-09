# CLAUDE.md

## Project Overview

**agent-hooks** is a portable hook API for steering agent behavior at the tool-call boundary. Write your hook once, and it works across any agent runtime that has an adapter.

- **agent-hooks-core**: Pure Java 17 API — events, inputs, decisions, registry. Zero dependencies.
- **agent-hooks-spring**: Spring AI adapter — wraps `ToolCallback` with hook dispatch, auto-configures via Spring Boot.

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

## Architecture

```
agent-hooks-core (pure Java)
├── AgentHookEvent          — BEFORE_TOOL_CALL, AFTER_TOOL_CALL, SESSION_START, SESSION_END
├── HookInput (sealed)      — BeforeToolCall, AfterToolCall, SessionStart, SessionEnd
├── HookDecision (sealed)   — Proceed, Block, Modify, Retry
├── AgentHook               — @FunctionalInterface: HookDecision handle(HookInput)
├── AgentHookProvider       — registerHooks(AgentHookRegistry)
├── AgentHookRegistry       — on(), onTool(), dispatch() with priority + short-circuit
└── HookContext             — Mutable session state + tool call history

agent-hooks-spring (Spring AI adapter)
├── HookedToolCallback      — Wraps ToolCallback with before/after hook dispatch
├── HookedToolCallbackProvider — Wraps all ToolCallbackProviders
└── AgentHooksAutoConfiguration — Spring Boot auto-config
```

## Key Design Decisions

1. **Core has zero dependencies** — portable across Spring AI, Claude SDK, any Java agent runtime
2. **Sealed interfaces** for HookInput and HookDecision — exhaustive, type-safe dispatch
3. **Block short-circuits** — security hooks can't be overridden by later hooks
4. **Modify chains** — subsequent hooks see modified input
5. **HookContext** (not ToolContext) for session state — ToolContext is immutable in Spring AI
6. **Model-call events** deferred to Spring adapter only — not portable across CLIs

## Integration Context

| Project | Relationship |
|---------|-------------|
| Spring AI | Wraps ToolCallback — no core changes needed |
| claude-agent-sdk-java | Adapter lives in that repo; imports agent-hooks-core |
| agent-journal | Bridge: hook provider that logs events to a journal Run |
| agent-harness | ChatClientStep gets hooks for free via Spring auto-config |

## Version Alignment

- **Spring AI**: 2.0.0-M3 (aligned with workshop `art-of-building-agents`)
- **Spring Boot**: 4.1.0-M2
- **License**: BSL 1.1 (Change Date: 2029-04-01 → Apache 2.0)

## Quality Standards

- AssertJ for assertions
- BDD-style test naming: `methodShouldBehaviorWhenCondition()`
- Coverage targets: 80% core, 70% spring
- BSL 1.1 license

## Not Covered

- Model-call events in core API (Spring-specific, lives in spring adapter)
- Async hook execution (v1 is synchronous)
- YAML/properties configuration (programmatic only)
- Hook persistence or replay

## Core API Summary

- 8 source files in `agent-hooks-core`: AgentHookEvent, HookDecision (sealed), ToolCallRecord, HookContext, HookInput (sealed), AgentHook, AgentHookProvider, AgentHookRegistry
- Registry dispatch: priority ordering → Block short-circuits → Modify chains → exception = Proceed
- Default priority: 100. Tool pattern via regex. Retry only for AFTER_TOOL_CALL.

## Spring Adapter Summary

- `HookedToolCallback`: wraps ToolCallback with BEFORE/AFTER dispatch. Block returns reason as result. Modify passes modified input.
- `HookedToolCallbackProvider`: wraps ToolCallbackProvider — each callback becomes HookedToolCallback
- `HookedTools.wrap(registry, hookContext, toolObjects...)`: main entry point for workshop usage
- `AgentHooksAutoConfiguration`: creates registry from AgentHookProvider beans + default HookContext
- Build from reactor root (`./mvnw test`), not `-pl agent-hooks-spring` alone
- 35 tests total (22 core + 13 spring)

## Session Behavior

Follow ROADMAP steps. Write tests before implementation. Create learnings after each step.
