# Design: agent-hooks

> **Created**: 2026-03-23T10:00-04:00
> **Last updated**: 2026-04-10T10:00-04:00
> **Vision version**: 2026-04-10T10:00-04:00

## Overview

agent-hooks provides a portable hook API for steering agent behavior at lifecycle boundaries. The core module defines an open event hierarchy, decisions, and a type-based dispatch registry — pure Java, no framework dependencies. Adapter modules wire the core API into specific agent runtimes and can define their own event types without modifying core. Hook providers implement the core API once and work across any runtime that has an adapter.

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
├── agent-hooks-core/            # Pure Java API: events, decisions, registry
│   └── No framework dependencies
├── agent-hooks-spring/          # Spring AI adapter: HookedToolCallback, auto-config
│   └── Depends on spring-ai-client-chat + agent-hooks-core
├── agent-hooks-claude/          # Claude Agent SDK adapter: AgentHookBridge
│   └── Depends on agent-hooks-core (compile), claude-code-sdk (provided)
└── agent-hooks-gemini/          # Gemini CLI adapter: stateless stdin/stdout dispatcher
    └── Depends on agent-hooks-core (compile), jackson-databind (compile)
```

### Key Dependencies

| Dependency | Module | Scope | Purpose |
|------------|--------|-------|---------|
| None (pure Java) | core | — | No external dependencies |
| `org.jspecify:jspecify` | both | compile | Null safety annotations |
| `org.springframework.ai:spring-ai-client-chat` | spring | compile | ToolCallback, ToolContext, ToolCallbackProvider |
| `org.springframework.boot:spring-boot-autoconfigure` | spring | compile | Auto-configuration |
| `org.junit.jupiter:junit-jupiter` | both | test | Testing |
| `org.assertj:assertj-core` | both | test | Fluent assertions |
| `org.springaicommunity:claude-code-sdk` | claude | provided | Claude SDK hook types (HookRegistry, HookInput, HookOutput) |
| `com.fasterxml.jackson.core:jackson-databind` | claude | provided | Map↔JSON conversion for tool inputs |
| `com.fasterxml.jackson.core:jackson-databind` | gemini | compile | JSON stdin/stdout parsing (no SDK provides it) |
| `org.mockito:mockito-core` | spring, claude | test | Mocking Spring AI / Claude SDK types |
| `com.tngtech.archunit:archunit-junit5` | all | test | Architecture rules |

### Quality Standards

| Check | Target | Tool |
|-------|--------|------|
| Line coverage | 80% core, 70% spring, 70% claude, 70% gemini | JaCoCo |
| Null safety | @NullMarked all packages | JSpecify |
| Architecture | ArchUnit rules (core→spring isolation, package deps) | ArchUnit |
| Dependency vulnerabilities | OWASP check (in pluginManagement) | dependency-check-maven |
| Javadoc | All public types | javadoc plugin |

---

## Architecture (v0.2 — target)

### Type System

```
HookEvent (unsealed interface)
  └── context(): HookContext

ToolEvent (extends HookEvent)
  └── toolName(): String
  └── toolInput(): String

Core event records (agent-hooks-core):
  BeforeToolCall  implements ToolEvent    — steerable (full decision algebra)
  AfterToolCall   implements ToolEvent    — steerable (full decision algebra)
  SessionStart    implements HookEvent    — observable (Proceed only, runtime enforced)
  SessionEnd      implements HookEvent    — observable (Proceed only, runtime enforced)

Adapter event records (agent-hooks-spring, future):
  BeforeModelCall implements HookEvent    — adapter-specific
  AfterModelCall  implements HookEvent    — adapter-specific

Claude adapter events (agent-hooks-claude):
  UserPromptSubmit implements HookEvent   — observation-only
  AgentStop        implements HookEvent   — observation-only
  SubagentStop     implements HookEvent   — observation-only
  PreCompact       implements HookEvent   — observation-only (@Nullable trigger, customInstructions)

Gemini adapter events (agent-hooks-gemini):
  GeminiBeforeAgent       implements HookEvent — observation-only
  GeminiAfterAgent        implements HookEvent — observation-only (stopHookActive informational)
  GeminiBeforeModel       implements HookEvent — observation-only (opaque llmRequestJson)
  GeminiAfterModel        implements HookEvent — observation-only (opaque request/response JSON)
  GeminiBeforeToolSelection implements HookEvent — observation-only
  GeminiNotification      implements HookEvent — observation-only (@Nullable detailsJson)
  GeminiPreCompress       implements HookEvent — observation-only

External adapter events (future):
  BeforeNodeCall  implements HookEvent    — Strands adapter

HookDecision    sealed: Proceed | Block(reason) | Modify(modifiedInput) | Retry(reason)
AgentHook<E>    functional: E → HookDecision
AgentHookRegistry   dispatch by Class<E>, reverse order for "after" events
```

### Key Insight: The Event IS the Input

v0.1 had two parallel hierarchies (`AgentHookEvent` enum + `HookInput` sealed interface) that had to stay in sync. v0.2 collapses them: each event record carries its own data. Adding an event = adding one record. No enum to modify, no parallel variant to keep in sync.

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  agent-hooks-core (pure Java)                               │
│                                                             │
│  HookEvent (unsealed interface)                             │
│  ├── ToolEvent (sub-interface: toolName, toolInput)         │
│  │   ├── BeforeToolCall (record)                            │
│  │   └── AfterToolCall  (record)                            │
│  ├── SessionStart (record)                                  │
│  └── SessionEnd   (record)                                  │
│                                                             │
│  HookDecision (sealed): Proceed | Block | Modify | Retry   │
│                                                             │
│  AgentHook<E extends HookEvent>  @FunctionalInterface       │
│       │                                                     │
│       ▼                                                     │
│  AgentHookRegistry                                          │
│    on(Class<E>, hook)         — type-based registration     │
│    onTool(pattern, Class<E extends ToolEvent>, hook)        │
│    dispatch(HookEvent)        — routes by event.getClass()  │
│                                                             │
│  AgentHookProvider → registerHooks(registry)                │
│  HookContext       → mutable session state + tool history   │
│  ToolCallRecord    → tool call audit record                 │
└──────────────────────────────┬──────────────────────────────┘
                               │ depends on
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  agent-hooks-spring (Spring AI adapter)                     │
│                                                             │
│  HookedToolCallback ──wraps──▶ ToolCallback                 │
│       │ before: dispatch(new BeforeToolCall(...))            │
│       │ after:  dispatch(new AfterToolCall(...))             │
│                                                             │
│  HookedToolCallbackProvider ──wraps──▶ ToolCallbackProvider │
│  HookedTools              — static utility                  │
│  AgentHooksAutoConfiguration — Spring Boot auto-config      │
│                                                             │
│  Future: BeforeModelCall, AfterModelCall (adapter-specific) │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  agent-hooks-claude (Claude Agent SDK adapter)              │
│                                                             │
│  AgentHookBridge ──registers into──▶ HookRegistry (SDK)     │
│       │ pre:  HookInput.PreToolUse → BeforeToolCall         │
│       │ post: HookInput.PostToolUse → AfterToolCall         │
│       │ 4 observation events → UserPromptSubmit, AgentStop, │
│       │   SubagentStop, PreCompact                          │
│                                                             │
│  DecisionMapper (package-private)                           │
│       │ HookDecision → HookOutput                           │
│                                                             │
│  Duration: ConcurrentHashMap<toolUseId, Instant>            │
│  Sessions: ConcurrentHashMap<sessionId, HookContext>        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  agent-hooks-gemini (Gemini CLI adapter — stateless)        │
│                                                             │
│  GeminiHookDispatcher ──reads──▶ stdin (JSON event)         │
│       │ BeforeTool → BeforeToolCall (core)                  │
│       │ AfterTool  → AfterToolCall  (core)                  │
│       │ SessionStart/End → core events                      │
│       │ 7 Gemini-specific → GeminiBeforeAgent, etc.         │
│       │ dispatch → JSON response → stdout                   │
│                                                             │
│  GeminiDecisionMapper (package-private)                     │
│       │ Proceed → {"decision":"allow"}                      │
│       │ Block   → {"decision":"block","reason":"..."}       │
│       │ Modify  → warn + {"decision":"allow"}               │
│       │ Retry   → warn + {"decision":"allow"}               │
│                                                             │
│  Stateless: HookContext fresh per invocation                │
│  No SDK dependency (stdin/stdout JSON protocol)             │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

```
1. Application defines AgentHookProvider beans
2. Auto-config collects providers → builds AgentHookRegistry
3. HookedToolCallbackProvider wraps all ToolCallbacks with HookedToolCallback
4. ChatClient calls toolCallback.call(input, context)
5. HookedToolCallback intercepts:
   a. Constructs new BeforeToolCall(toolName, input, hookContext)
   b. Calls registry.dispatch(event) → returns HookDecision
   c. If Proceed/Modify: delegates to original ToolCallback
   d. If Block: returns block reason as tool result
   e. Constructs new AfterToolCall(toolName, input, result, duration, exception, hookContext)
   f. Calls registry.dispatch(afterEvent) — observation only
   g. Records ToolCallRecord in HookContext.history()
```

---

## Interfaces (v0.2 — target)

### HookEvent

```java
/**
 * Base interface for all hook events. Unsealed — adapters define new events
 * by implementing this interface.
 */
public interface HookEvent {
    /** The session context shared across all hooks. */
    HookContext context();
}
```

### ToolEvent

```java
/**
 * Marker interface for tool-related events. Enables type-safe tool name
 * pattern matching via {@link AgentHookRegistry#onTool}.
 */
public interface ToolEvent extends HookEvent {
    String toolName();
    String toolInput();
}
```

### Core Event Records

```java
record BeforeToolCall(String toolName, String toolInput,
        HookContext context) implements ToolEvent {}

record AfterToolCall(String toolName, String toolInput,
        @Nullable String toolResult, Duration duration,
        @Nullable Exception exception, HookContext context) implements ToolEvent {}

record SessionStart(String sessionId, HookContext context) implements HookEvent {}

record SessionEnd(String sessionId, HookContext context,
        Duration totalDuration) implements HookEvent {}
```

### AgentHook

```java
@FunctionalInterface
public interface AgentHook<E extends HookEvent> {
    HookDecision handle(E event);
}
```

### AgentHookProvider

```java
public interface AgentHookProvider {
    void registerHooks(AgentHookRegistry registry);
}
```

### AgentHookRegistry

```java
public class AgentHookRegistry {
    /** Register a hook for an event type. */
    public <E extends HookEvent> void on(Class<E> eventType, AgentHook<E> hook) { ... }

    /** Register a hook with priority (lower = earlier). */
    public <E extends HookEvent> void on(Class<E> eventType, int priority, AgentHook<E> hook) { ... }

    /** Register a hook for tool events matching a name pattern (regex). */
    public <E extends ToolEvent> void onTool(String toolNamePattern,
            Class<E> eventType, AgentHook<E> hook) { ... }

    /** Register a provider that self-registers for multiple events. */
    public void register(AgentHookProvider provider) { ... }

    /** Dispatch an event to all matching hooks. Returns aggregate decision. */
    public HookDecision dispatch(HookEvent event) { ... }
}
```

**Implementation note — generic erasure in internal storage**: The public API is type-safe (`on(Class<E>, AgentHook<E>)` — the generic flows from the class literal through to the lambda). But internally the registry stores hooks for different event types in the same map:

```java
// Internal — type safety is at the public API boundary, not here
Map<Class<?>, List<PrioritizedHook<?>>> hooksByEventType;
```

Dispatch performs an unchecked cast when invoking the hook. This is the standard pattern (Guava `TypeToInstanceMap`, Spring `ResolvableType`). The cast is safe because registration enforces the `Class<E>` ↔ `AgentHook<E>` pairing.

### HookDecision (unchanged from v0.1)

```java
public sealed interface HookDecision permits
        HookDecision.Proceed, HookDecision.Block,
        HookDecision.Modify, HookDecision.Retry {
    record Proceed() implements HookDecision {}
    record Block(String reason) implements HookDecision {}
    record Modify(String modifiedInput) implements HookDecision {}
    record Retry(String reason) implements HookDecision {}
}
```

---

## Dispatch Semantics

### Tool events (BeforeToolCall, AfterToolCall)

- Full decision algebra: Proceed, Block, Modify, Retry
- Block short-circuits remaining hooks
- Modify chains input to next hook
- Retry only valid on AfterToolCall (IllegalStateException on BeforeToolCall)
- AfterToolCall dispatches in **reverse** priority order (cleanup ordering — matches Strands pattern)
- BeforeToolCall dispatches in forward priority order

### Session and custom events (everything else)

- Observation-only: Block/Modify/Retry → logged warning, treated as Proceed
- Useful for metrics, tracing, journal recording
- Forward priority ordering

### Tool pattern matching

`onTool()` only accepts `Class<E extends ToolEvent>` — compile-time guarantee that pattern matching only applies to events with a tool name. The `ToolEvent` interface provides `toolName()` for the regex match.

---

## Registration Comparison (v0.1 → v0.2)

```java
// v0.1 — cast required, enum + separate input type
registry.on(BEFORE_TOOL_CALL, input -> {
    var before = (HookInput.BeforeToolCall) input;
    if (before.toolName().equals("bookTable"))
        return HookDecision.block("Over budget");
    return HookDecision.proceed();
});

// v0.2 — no cast, generic carries the type
registry.on(BeforeToolCall.class, event -> {
    if (event.toolName().equals("bookTable"))
        return HookDecision.block("Over budget");
    return HookDecision.proceed();
});
```

---

## Design Decisions

### DD-1: Core API has no framework dependencies

**Decision**: `agent-hooks-core` depends only on JDK 17. No Spring, no SLF4J, no Jackson.
**Rationale**: Maximum portability. The core is a contract, not a framework.

### DD-2: Open event hierarchy (replaces sealed HookInput)

**Context**: v0.1 used a sealed `HookInput` interface paired with an `AgentHookEvent` enum — two parallel hierarchies. Adding events required modifying both. The enum was closed to adapters.

**Decision**: Replace with an unsealed `HookEvent` interface. The event IS the input. Core defines 4 portable event records. Adapters define their own by implementing `HookEvent`.

**Rationale**: Event taxonomies grow (Claude Code: 23+, Strands: 13+). An open hierarchy eliminates the "which events belong in core" decision. Proven in Strands SDK production.

### DD-3: HookContext as mutable session state (not ToolContext)

**Decision**: `HookContext` is a mutable, thread-safe context object. Spring AI's `ToolContext` is immutable.
**Rationale**: Portable and explicit (no ThreadLocal).

### DD-4: Block short-circuits, Modify chains

**Decision**: Block stops dispatch. Modify updates input for subsequent hooks. Priority ordering.
**Rationale**: Security hooks can't be overridden. Transformations compose.

### DD-5: ToolEvent marker interface for pattern matching

**Decision**: `ToolEvent extends HookEvent` with `toolName()` method. `onTool()` only accepts `Class<E extends ToolEvent>`.
**Rationale**: Compile-time safety — pattern matching only applies to events that carry a tool name.

### DD-6: Reverse ordering for "after" events

**Decision**: AfterToolCall dispatches hooks in reverse priority order (highest priority last).
**Rationale**: Cleanup ordering — matches Strands pattern. Setup hooks run first, cleanup hooks run in reverse.

### DD-7: Uniform decision model with runtime enforcement

**Decision**: All hooks return `HookDecision`. For non-tool events (session, custom), Block/Modify/Retry are logged as warnings and treated as Proceed.
**Rationale**: One hook interface, one registry API, one mental model. No need to split the type system.

---

## Delta from v0.1

```
Removed:
  - AgentHookEvent enum
  - HookInput sealed interface + 4 records

Added:
  - HookEvent unsealed interface (with context() method)
  - ToolEvent sub-interface (toolName, toolInput)

Changed:
  - AgentHook: handle(HookInput) → handle(E extends HookEvent)   // generic
  - Registry: on(enum, hook) → on(Class<E>, hook)                // type-based
  - Registry: dispatch(enum, input) → dispatch(HookEvent)        // single arg
  - BeforeToolCall/AfterToolCall: HookInput records → HookEvent records
  - AfterToolCall dispatch: forward → reverse priority order
  - onTool: accepts Class<E extends ToolEvent> instead of enum

Unchanged:
  - HookDecision (sealed, 4 variants + factory methods)
  - HookContext (mutable state + history)
  - ToolCallRecord
  - AgentHookProvider (registerHooks contract)
  - HookedToolCallback (minor signature updates)
  - HookedToolCallbackProvider
  - HookedTools
  - AgentHooksAutoConfiguration
```

Net: fewer types, more extensible, better ergonomics at the registration site.

---

## DD-8: Shell-Hook Adapters for Agentic CLIs (Gemini, Codex)

> **Status**: Resolved
> **Date**: 2026-04-10

### Problem

We have two working adapters: Spring AI (in-process) and Claude Agent SDK (bidirectional JSON streaming). Both share one trait — **our Java code is the parent process**. The registry, HookContext, and providers all live in long-running memory.

The next two adapter candidates — Gemini CLI and OpenAI Codex CLI — use a fundamentally different execution model: **the CLI is the parent and our hook code runs as a child subprocess**, spawned fresh per hook event.

### Landscape

| CLI | Events | Protocol | Can Block | Can Modify | Maturity |
|-----|--------|----------|-----------|------------|----------|
| Claude Code | 23+ | Bidirectional JSON stream (SDK wraps) | Yes | Yes | Production |
| Gemini CLI | 11 | Subprocess per event, JSON stdin/stdout | Yes | No (tool inputs) | Production (Go) — **adapter shipped** |
| Codex CLI | 5 | Subprocess per event, JSON stdin/stdout | Yes | No | Experimental (Rust) |

Gemini CLI BeforeTool can only allow or block — it cannot modify tool inputs. Our `HookDecision.Modify` maps to a warning + allow. Codex is experimental with Bash-only tool scope and no Modify support.

### Three Execution Models

```
Model A — In-Process (Spring AI)
  Application ──► HookedToolCallback ──► AgentHookRegistry ──► hooks
  Registry, HookContext, providers all live in application heap.

Model B — Bidirectional Streaming (Claude SDK)
  Claude CLI ◄──JSON──► Java parent process (AgentHookBridge)
  Bridge lives in the parent. HookContext persists across events.
  Our code is the long-lived process; CLI connects to us.

Model C — Subprocess per Event (Gemini, Codex)
  CLI ──fork──► java -jar hook.jar (stdin: event JSON, stdout: decision JSON)
  Fresh JVM per event. Process exits after responding.
  CLI is the long-lived process; our code is ephemeral.
```

Models A and B work today because `HookContext`, tool-call history, and duration tracking live in process memory that survives across events. Model C breaks this assumption.

### What Works Without State (Stateless Hooks)

Stateless hooks — the highest-value production use cases — work fine under Model C:

- **Block dangerous tools**: `if toolName matches "Bash" → Block("not permitted")` — pure function of the event
- **Log to external system**: write to a file, HTTP POST to an observability backend — side effect, no cross-event memory needed
- **Input sanitization**: rewrite tool inputs based on the current event alone

The same `AgentHookProvider` works — it gets a fresh `HookContext` each invocation. Stateless providers don't notice.

### What Breaks Without State (Stateful Hooks)

Features that require cross-event memory do not work under Model C:

| Feature | Requires | Why |
|---------|----------|-----|
| `HookContext` session state | Shared mutable object across events | Fresh JVM = empty context every time |
| Tool-call history | `HookContext.history()` accumulation | History resets each invocation |
| Duration tracking | `ConcurrentHashMap<toolUseId, Instant>` from pre-hook to post-hook | Pre-hook and post-hook are different processes |
| Stateful policies (rate limiting, budget caps) | Counter/accumulator across events | Counter resets each invocation |

These are documented limitations. Users who need stateful hooks use the Claude SDK or Spring AI adapters.

### Decisions

**1. Scope: Option 1 — Stateless-only adapter.**

Ship a stateless adapter first. `HookContext` is fresh per invocation. Security gates, audit logging, and input sanitization are the highest-value production hooks and they're all stateless predicates. Document the limitation clearly.

A persistent daemon (Option 2) is the right eventual answer for full state parity, but it's a different project — a sidecar process manager with health checks, startup ordering, graceful shutdown, and port allocation. That's `agent-client` territory if it ever gets built.

**2. Location: `agent-hooks` repo.**

The adapter is a type mapping layer: Gemini CLI JSON schema → `HookEvent` records → `HookDecision` → JSON response on stdout. Same structural role as `AgentHookBridge`. The subprocess lifecycle (spawning the Java process) is the CLI's responsibility, not ours — our code is the thing that gets spawned.

**3. Priority: After workshop stage (Stage 5).**

Two adapters already prove the abstraction. The cross-adapter test with Spring + Claude is the proof point. Adding Gemini stateless-only is ~1 day of work once the workshop is stable — it's JSON mapping with no SDK dependency (parsing stdin, not importing a library).

### Latency

JVM startup adds ~200-500ms per hook event. LLM tool calls take 10-60+ seconds. The agent isn't waiting for the hook — the startup overhead is noise. No need for GraalVM native images or other startup optimizations.

---

## Error Handling Strategy

- **Hook throws unchecked exception**: Registry catches it, logs, treats as Proceed. A failing hook must not break the agent.
- **Invalid decision for event type**: Logged warning, treated as Proceed (runtime enforcement for non-tool events). IllegalStateException for Retry on BeforeToolCall (always invalid).
- **Tool execution fails after hooks**: AfterToolCall carries the exception. Hooks can observe it or return Retry.

## Testing Strategy

- **Core module**: Unit tests for registry type-based dispatch, priority ordering, reverse ordering for after events, Block short-circuit, Modify chaining, pattern matching, runtime enforcement of non-tool decisions, provider registration.
- **Spring module**: Unit tests with mock ToolCallback. Auto-config context tests. Provider/utility tests.
- **Claude module**: Unit tests with mock HookRegistry. Bridge callback tests (6 events), decision mapping, duration tracking, session isolation, cross-adapter proof (same provider on Claude, Spring, and Gemini paths).
- **Gemini module**: Unit tests with JSON fixtures for all 11 event types. Decision mapping tests (allow, block, modify-downgrade). Error handling (malformed JSON, unknown events). Cross-adapter proof (3 runtimes).
- **Architecture**: ArchUnit rules in all modules.
- **Coverage**: JaCoCo check goals (80% core, 70% spring, 70% claude, 70% gemini).

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-23T10:00-04:00 | Initial draft | Forge project bootstrap |
| 2026-04-09T18:00-04:00 | Landscape analysis, DD-6 options A/B/C, DD-7 options | Industry research |
| 2026-04-09T19:00-04:00 | Decided Option B. Full redesign: open event hierarchy, ToolEvent marker, reverse ordering, runtime enforcement. Rewrote all interfaces and delta. | review.md analysis |
| 2026-04-10T10:00-04:00 | Claude adapter: added module structure, component diagram box, key dependencies, testing strategy, quality standards. DD-8: Shell-hook adapter brief for Gemini/Codex CLIs. | Stage 4 completion + doc review |
