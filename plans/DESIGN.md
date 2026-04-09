# Design: agent-hooks

> **Created**: 2026-03-23T10:00-04:00
> **Last updated**: 2026-03-23T10:00-04:00
> **Vision version**: 2026-03-23T10:00-04:00

## Overview

agent-hooks provides a portable hook API for steering agent behavior at the tool-call boundary. The core module defines events, inputs, decisions, and a registry — pure Java, no framework dependencies. Adapter modules wire the core API into specific agent runtimes (Spring AI, Claude Agent SDK, etc.). Hook providers implement the core API once and work across any runtime that has an adapter.

## Build Coordinates

| Field | Value |
|-------|-------|
| **Group ID** | `io.github.markpollack` |
| **Artifact ID** | `agent-hooks-parent` |
| **Version** | `0.1.0-SNAPSHOT` |
| **Packaging** | `pom` (multi-module) |
| **Java version** | 17 |
| **Spring AI** | `2.0.0-M3` |
| **Spring Boot** | `4.1.0-M2` |
| **License** | BSL 1.1 (Change Date: 2029-04-01, converts to Apache 2.0) |
| **Base package** | `io.github.markpollack.hooks` |

### Module Structure

```
agent-hooks/
├── pom.xml                      # Aggregator parent
├── agent-hooks-core/            # Pure Java API: events, inputs, decisions, registry
│   └── No framework dependencies
└── agent-hooks-spring/          # Spring AI adapter: HookedToolCallback, auto-config
    └── Depends on spring-ai-client-chat + agent-hooks-core
```

### Key Dependencies

| Dependency | Module | Scope | Purpose |
|------------|--------|-------|---------|
| None (pure Java) | core | — | No external dependencies |
| `org.springframework.ai:spring-ai-client-chat` | spring | compile | ToolCallback, ToolContext, ToolCallbackProvider |
| `org.springframework.boot:spring-boot-autoconfigure` | spring | compile | Auto-configuration |
| `org.junit.jupiter:junit-jupiter` | both | test | Testing |
| `org.assertj:assertj-core` | both | test | Fluent assertions |
| `org.mockito:mockito-core` | spring | test | Mocking Spring AI types |

## Architecture

### Components

| Component | Module | Responsibility | Public API |
|-----------|--------|---------------|------------|
| `AgentHookEvent` | core | Enumerate portable hook events | Enum: `BEFORE_TOOL_CALL`, `AFTER_TOOL_CALL`, `SESSION_START`, `SESSION_END` |
| `HookInput` | core | Sealed input types per event | `BeforeToolCall`, `AfterToolCall`, `SessionStart`, `SessionEnd` |
| `HookDecision` | core | Sealed decision types | `Proceed`, `Block(reason)`, `Modify(input)`, `Retry(reason)` |
| `AgentHook` | core | Functional hook callback | `HookDecision handle(HookInput)` |
| `AgentHookProvider` | core | Multi-event hook registration | `void registerHooks(AgentHookRegistry)` |
| `AgentHookRegistry` | core | Registry + dispatcher | `on()`, `onTool()`, `register()`, `dispatch()` |
| `HookContext` | core | Mutable state shared across hooks within a session | `get(key)`, `put(key, value)`, `history()` |
| `HookedToolCallback` | spring | Wraps ToolCallback with hook dispatch | Implements `ToolCallback` |
| `HookedToolCallbackProvider` | spring | Wraps all ToolCallbackProviders | Implements `ToolCallbackProvider` |
| `AgentHooksAutoConfiguration` | spring | Spring Boot auto-config | `@AutoConfiguration` |

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  agent-hooks-core (pure Java)                                │
│                                                              │
│  AgentHookProvider ──registers──▶ AgentHookRegistry          │
│       │                              │                       │
│       │                         dispatch(event, input)       │
│       │                              │                       │
│       ▼                              ▼                       │
│  AgentHook ◀──────────────── HookInput (sealed)             │
│       │                                                      │
│       ▼                                                      │
│  HookDecision (sealed): Proceed | Block | Modify | Retry    │
│                                                              │
│  HookContext: mutable session state for hook coordination    │
└──────────────────────────────┬──────────────────────────────┘
                               │ depends on
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  agent-hooks-spring (Spring AI adapter)                      │
│                                                              │
│  HookedToolCallback ──wraps──▶ ToolCallback                  │
│       │                            │                         │
│       │ before: dispatch(BEFORE_TOOL_CALL, input)            │
│       │ after:  dispatch(AFTER_TOOL_CALL, input)             │
│       │                            │                         │
│  HookedToolCallbackProvider ──wraps──▶ ToolCallbackProvider  │
│                                                              │
│  AgentHooksAutoConfiguration                                 │
│       ├── creates AgentHookRegistry bean                     │
│       └── wraps all ToolCallbackProvider beans               │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

```
1. Application defines AgentHookProvider beans
2. Auto-config collects providers → builds AgentHookRegistry
3. Auto-config wraps all ToolCallbackProviders with HookedToolCallbackProvider
4. ChatClient calls toolCallback.call(input, context)
5. HookedToolCallback intercepts:
   a. Builds HookInput.BeforeToolCall from tool name, input, HookContext
   b. Dispatches to registry → collects HookDecision
   c. If Proceed/Modify: delegates to original ToolCallback
   d. If Block: returns block reason as tool result (agent sees it)
   e. Builds HookInput.AfterToolCall with result/duration/exception
   f. Dispatches after-hook (observe only — decision ignored for after-hooks)
```

## Interfaces

### AgentHook

```java
@FunctionalInterface
public interface AgentHook {
    HookDecision handle(HookInput input);
}
```

**Contract**:
- Must return a non-null `HookDecision`
- Must not throw checked exceptions (unchecked exceptions are caught and logged, defaulting to `Proceed`)
- Must complete synchronously within a reasonable time (no blocking I/O)
- Must be thread-safe if the agent runtime is multi-threaded

### AgentHookProvider

```java
public interface AgentHookProvider {
    void registerHooks(AgentHookRegistry registry);
}
```

**Contract**:
- Called once during initialization
- Must register all hooks via `registry.on()` or `registry.onTool()`
- Must not store a reference to the registry for later mutation

### AgentHookRegistry

```java
public class AgentHookRegistry {
    /** Register a hook for a specific event. */
    public void on(AgentHookEvent event, AgentHook hook) { ... }

    /** Register a hook for a specific event with priority (lower = earlier). */
    public void on(AgentHookEvent event, int priority, AgentHook hook) { ... }

    /** Register a hook for tools matching a name pattern (regex). */
    public void onTool(String toolNamePattern, AgentHookEvent event, AgentHook hook) { ... }

    /** Register a provider that self-registers for multiple events. */
    public void register(AgentHookProvider provider) { ... }

    /** Dispatch an event to all matching hooks. Returns aggregate decision. */
    public HookDecision dispatch(AgentHookEvent event, HookInput input) { ... }
}
```

**Contract**:
- `dispatch()` invokes hooks in priority order (lower priority value = invoked first)
- `dispatch()` short-circuits on `Block` — remaining hooks are not called
- `dispatch()` applies `Modify` — subsequent hooks see the modified input
- `Retry` is only valid for `AFTER_TOOL_CALL` — `dispatch()` throws `IllegalStateException` if returned for other events
- Hooks registered with `onTool()` are only invoked when the tool name matches the regex pattern
- Thread-safe: registration and dispatch can happen concurrently

**Error handling**: If a hook throws an unchecked exception, the registry logs the error and treats it as `Proceed()`.

### HookContext

```java
public class HookContext {
    /** Get a value by key. */
    public <T> Optional<T> get(String key, Class<T> type) { ... }

    /** Put a value. */
    public void put(String key, Object value) { ... }

    /** Get the tool call history for this session. */
    public List<ToolCallRecord> history() { ... }

    /** Record a completed tool call (called by the adapter after each tool execution). */
    public void recordToolCall(ToolCallRecord record) { ... }
}
```

**Contract**:
- One `HookContext` per agent session (created by the adapter, passed via `HookInput`)
- Mutable — hooks can read and write state
- `history()` returns an unmodifiable view of all tool calls in the session so far
- Thread-safe (ConcurrentHashMap internally)

---

## Data Models

### HookInput (sealed)

| Variant | Fields | Description |
|---------|--------|-------------|
| `BeforeToolCall` | `toolName: String`, `toolInput: String`, `hookContext: HookContext` | Before a tool executes. Can block or modify input. |
| `AfterToolCall` | `toolName: String`, `toolInput: String`, `toolResult: String`, `duration: Duration`, `exception: Exception` (nullable) | After a tool completes. Can observe or trigger retry. |
| `SessionStart` | `sessionId: String`, `hookContext: HookContext` | Agent session begins. |
| `SessionEnd` | `sessionId: String`, `hookContext: HookContext`, `totalDuration: Duration` | Agent session ends. |

### HookDecision (sealed)

| Variant | Fields | Valid For | Description |
|---------|--------|-----------|-------------|
| `Proceed` | (none) | All events | Continue unchanged |
| `Block` | `reason: String` | `BEFORE_TOOL_CALL` | Block execution; agent sees the reason |
| `Modify` | `modifiedInput: String` | `BEFORE_TOOL_CALL` | Replace the tool input |
| `Retry` | `reason: String` | `AFTER_TOOL_CALL` | Retry the tool call |

### ToolCallRecord

| Field | Type | Description |
|-------|------|-------------|
| `toolName` | `String` | Name of the tool |
| `toolInput` | `String` | Input arguments (JSON) |
| `toolResult` | `String` | Result (null if failed) |
| `duration` | `Duration` | Execution time |
| `decision` | `HookDecision` | What the before-hook decided |
| `timestamp` | `Instant` | When the call started |

---

## Design Decisions

### DD-1: Core API has no framework dependencies

**Context**: The hook API must work across Spring AI, Claude Agent SDK, and potentially non-Java systems via adapters.

**Decision**: `agent-hooks-core` depends only on JDK 17. No Spring, no SLF4J, no Jackson.

**Alternatives considered**:
1. SLF4J for logging — rejected because it forces a dependency on consumers who may use different logging. Adapters can bridge to their runtime's logging.

**Rationale**: Maximum portability. The core is a contract, not a framework.

### DD-2: Sealed interfaces for HookInput and HookDecision

**Context**: Hook inputs and decisions are a closed set of types. New events require a design decision, not arbitrary extension.

**Decision**: Use Java 17 sealed interfaces with records for each variant.

**Alternatives considered**:
1. Visitor pattern — rejected; more verbose, no pattern matching benefit
2. Open interface hierarchy — rejected; makes dispatch harder to reason about

**Rationale**: Sealed interfaces + pattern matching (Java 21 switch) give exhaustiveness checking. Java 17 consumers use `instanceof` checks.

### DD-3: HookContext as mutable session state (not ToolContext)

**Context**: Spring AI's `ToolContext` is immutable (`Collections.unmodifiableMap`). The brief proposed using ToolContext for a steering ledger, but this won't work.

**Decision**: Introduce `HookContext` in the core API as a mutable, thread-safe context object. The Spring adapter creates one per session and passes it through `HookInput`. `HookContext` also maintains the tool call history (what the brief called "steering ledger").

**Alternatives considered**:
1. ThreadLocal — rejected; breaks in async/virtual-thread scenarios
2. Wrapping ToolContext with a mutable layer — rejected; ToolContext is final and immutable by design

**Rationale**: A first-class context object is portable (works in non-Spring adapters too) and explicit (no magic ThreadLocal lookups).

### DD-4: Block short-circuits, Modify chains

**Context**: When multiple hooks are registered for the same event+tool, we need clear semantics for conflicting decisions.

**Decision**: Hooks execute in priority order. `Block` immediately stops dispatch — remaining hooks are not called. `Modify` updates the input — subsequent hooks see the modified input. `Proceed` continues to the next hook.

**Alternatives considered**:
1. Collect all decisions and merge — rejected; ambiguous when one hook blocks and another modifies
2. Last-writer-wins — rejected; non-deterministic

**Rationale**: Short-circuit on Block matches the security use case (a security hook should not be overridden). Modify chaining allows composable transformations.

### DD-5: Model-call events only in the Spring adapter

**Context**: `BEFORE_MODEL_CALL`/`AFTER_MODEL_CALL` are useful for Spring AI users (intercept LLM requests) but not portable. Claude CLI subprocess doesn't expose model invocation boundaries.

**Decision**: Model-call events are defined in `agent-hooks-spring` as Spring-specific extensions, not in the core API.

**Alternatives considered**:
1. Include in core as optional events — rejected; an adapter that can never implement them violates the contract
2. Separate sealed hierarchy in core — rejected; overcomplicates the core for a single adapter's benefit

**Rationale**: The core API should only contain events that every adapter can implement. Model-call events are a Spring AI concern.

---

## Error Handling Strategy

- **Hook throws unchecked exception**: Registry catches it, logs (via adapter-provided logger or stderr in core), and treats it as `Proceed()`. A failing hook must not break the agent.
- **Tool execution fails after hooks**: `AfterToolCall` input receives the exception. Hooks can observe it or return `Retry`.
- **Invalid decision for event**: Registry throws `IllegalStateException` (e.g., `Retry` for `BEFORE_TOOL_CALL`). This is a programming error, not a runtime error.

## Testing Strategy

- **Core module**: Unit tests for registry dispatch logic — priority ordering, Block short-circuit, Modify chaining, pattern matching, error handling. No mocks needed (pure Java).
- **Spring module**: Unit tests with mock `ToolCallback` verifying `HookedToolCallback` dispatch. Integration test with a real Spring context verifying auto-configuration wires everything.
- **Cross-module**: A test that defines a hook provider in core, wires it via the Spring adapter, and verifies it receives events from a mock ChatClient tool call.

## Open Questions

1. Should `HookContext.history()` include blocked calls (tool was never executed)? Leaning yes — security auditing needs this.
2. Should the Spring adapter support model-call hooks in v1 or defer to v2? Leaning defer.
3. How should the agent-journal bridge module be structured — as a third module in this repo, or as a module in agent-journal that depends on agent-hooks-core?

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-23T10:00-04:00 | Initial draft | Forge project bootstrap |
