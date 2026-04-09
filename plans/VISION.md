# Vision: agent-hooks

> **Created**: 2026-03-23T10:00-04:00
> **Last updated**: 2026-03-23T10:00-04:00
> **Status**: Draft

## Problem Statement

Three major agent CLIs (Claude Code, Gemini CLI, Strands SDK) now have hook systems for steering agent behavior at the tool-call boundary. Java developers building agents with Spring AI, Claude Agent SDK, or other frameworks have **no unified way to write hook logic once and have it work across systems**.

Spring AI's `ToolCallAdvisor` intercepts at the model call level but not per-tool-call. `ToolCallListener` observes but can't steer (block/modify). The `claude-agent-sdk-java` has hooks for the Claude CLI subprocess, but nothing for the `ChatClient` path. Each system has its own hook API with different event names, input shapes, and decision types.

Developers who want consistent guardrails, observability, or steering across agent backends must write and maintain separate hook implementations for each system.

## Success Criteria

1. A developer can write a single `AgentHookProvider` that blocks dangerous tool calls, and it works on both the Spring AI ChatClient path and the Claude CLI subprocess path without code changes
2. Hook dispatch adds < 1ms overhead per tool call (no async, no serialization in the hot path)
3. The core API has zero framework dependencies (pure Java 17)
4. At least one concrete adapter (Spring AI) is functional and auto-configures

## Scope

### In Scope

- Core hook API: events, inputs, decisions, registry, provider interface (pure Java)
- Spring AI adapter: `HookedToolCallback` wrapper, auto-configuration, `ToolCallbackProvider` wrapping
- Spring AI-specific model-call events (`BEFORE_MODEL_CALL`, `AFTER_MODEL_CALL`) in the Spring adapter only
- Tool name pattern matching (regex) for selective hook registration
- Hook ordering (priority)
- Integration point for agent-journal (hook provider that logs to a journal `Run`)

### Out of Scope

- Claude Agent SDK adapter (lives in that repo, imports core API)
- Gemini CLI / Strands SDK adapters (future, separate repos)
- Async/non-blocking hook execution (v1 is synchronous)
- Hook configuration via YAML/properties files (programmatic only for v1)
- Steering ledger / trajectory recording (defer — agent-journal covers this)
- UI or dashboard for hook activity

## Unknowns and Research Questions

1. Can `HookedToolCallback` wrap `ToolCallback` transparently without breaking Spring AI's `DefaultToolCallingManager` contract? (ToolContext is immutable — how do we pass hook state between calls?)
2. Does `ToolCallbackProvider` ordering matter for auto-configuration? Can we reliably wrap all providers before they reach the ChatClient?
3. How should hook priority/ordering work when multiple providers register for the same event+tool pattern?

## Assumptions

1. Spring AI's `ToolCallback.call(String, ToolContext)` remains the stable interception point (Spring AI 2.0.0-M3+)
2. `ToolContext` will remain immutable — hook state must flow through a side-channel (e.g., ThreadLocal or a mutable wrapper)
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
