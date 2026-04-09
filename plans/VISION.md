# Vision: agent-hooks

> **Created**: 2026-03-23T10:00-04:00
> **Last updated**: 2026-04-09T19:00-04:00
> **Status**: Active (v0.1 complete, v0.2 refactoring planned)

## Problem Statement

Agent frameworks have converged on hook systems for steering agent behavior — but every framework defines its own event taxonomy, input shapes, and decision types:

| Framework | Events | Model | Extensibility |
|-----------|--------|-------|---------------|
| Claude Code CLI | 23+ | Config-driven (JSON) | Fixed enum |
| Claude Agent SDK (Java) | 6 | Sealed HookInput + HookOutput record | Fixed enum |
| Strands SDK (Python) | 8 single-agent + 5 multi-agent | Dataclass hierarchy + field mutation | Open class hierarchy |
| Spring AI ToolCallAdvisor | 8 template methods | Subclass override | Open (subclassing) |
| OpenAI Agents SDK | 4 | RunHooks callbacks | Fixed |
| CrewAI | 2 | before/after tool use | Fixed |

Java developers building agents with Spring AI, Claude Agent SDK, or other frameworks have **no unified way to write hook logic once and have it work across systems**.

### Design Insight: Event Taxonomies Grow

Claude Code went from a handful to 23+. Strands has single-agent and multi-agent events. Spring AI's ToolCallAdvisor keeps adding template methods. A closed enum is fighting gravity.

The v0.1 implementation used a 4-value enum (`AgentHookEvent`) paired with a sealed `HookInput` interface — two parallel hierarchies that must stay in sync. Adding an event means touching both. The enum is closed to adapters.

The industry pattern (proven by Strands SDK in production) is: **the event IS the input**. A single class hierarchy where each event type carries its own data. Adapters extend it freely. No enums, no parallel types.

## Success Criteria

1. A developer can write a single `AgentHookProvider` that blocks dangerous tool calls, and it works on both the Spring AI ChatClient path and the Claude CLI subprocess path without code changes
2. The event model is **open** — adapters define new events by adding records, no core changes needed
3. Core defines the **portable events** (tool + session); adapters define runtime-specific events (model-call, compaction, sub-agent)
4. Hook dispatch adds < 1ms overhead per tool call (no async, no serialization in the hot path)
5. The core API has zero framework dependencies (pure Java 17)
6. At least one concrete adapter (Spring AI) is functional and auto-configures

## Scope

### In Scope

- Core hook API: `HookEvent` (unsealed interface), `ToolEvent` sub-interface, 4 portable event records, `HookDecision` (sealed), registry with type-based dispatch
- Spring AI adapter: `HookedToolCallback` wrapper, auto-configuration, `HookedTools` utility
- Tool name pattern matching (regex) via `ToolEvent` marker interface
- Hook ordering (priority) with reverse ordering for "after" events
- Observation-only semantics for non-tool events (runtime enforcement)
- Integration point for agent-journal (hook provider that logs to a journal `Run`)

### Out of Scope

- Claude Agent SDK adapter (lives in that repo, imports core API)
- Gemini CLI / Strands SDK adapters (future, separate repos)
- Multi-agent orchestration events (Strands-specific, v2+)
- Async/non-blocking hook execution (v1 is synchronous)
- Hook configuration via YAML/properties files (programmatic only for v1)
- Human-in-the-loop interrupt mechanism (Strands has this; evaluate for v2)
- Steering ledger / trajectory recording (defer — agent-journal covers this)

## Resolved Questions

1. **ToolCallback wrapping**: Yes, `HookedToolCallback` wraps `ToolCallback` transparently. Verified in 9 tests.
2. **ToolContext immutability**: Solved with `HookContext` — a separate mutable session state object. ThreadLocal rejected (breaks virtual threads).
3. **Hook ordering**: Priority-based (lower = first). Block short-circuits. Modify chains. Exception = Proceed.
4. **Extensibility model (DD-6)**: Open class hierarchy (Option B). The event IS the input. `HookEvent` is unsealed. Adapters add events by adding records. Decided via review.md analysis.
5. **Decision model for non-tool events (DD-7)**: Uniform with runtime enforcement. One hook interface, one registry API. Block/Modify/Retry on non-tool events → logged warning, treated as Proceed.

## Assumptions

1. Spring AI's `ToolCallback.call(String, ToolContext)` remains the stable interception point (Spring AI 2.0.0-M3+)
2. `ToolContext` will remain immutable — hook state flows through `HookContext`
3. The claude-agent-sdk-java team will adopt the core API as a dependency and implement their own adapter
4. Hook providers are typically few (2-5 per application), not hundreds

## Constraints

- **Technology**: Java 17+, no framework dependencies in core. Spring Boot 4.1.0-M2 / Spring AI 2.0.0-M3 for the Spring adapter.
- **Compatibility**: Must not require changes to Spring AI core. Decoration/wrapping only.
- **Licensing**: BSL 1.1 (Change Date: 2029-04-01, converts to Apache 2.0)

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-23T10:00-04:00 | Initial draft | Forge project bootstrap |
| 2026-04-09T18:00-04:00 | Landscape analysis, abstraction gap, 3 options for DD-6 | Industry research |
| 2026-04-09T19:00-04:00 | Decided Option B (open event hierarchy), DD-6 and DD-7 resolved | review.md analysis |
