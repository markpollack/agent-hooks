The real question is: **what's the right abstraction**, full stop?

Let me reconsider Option B more seriously now that backward compatibility is off the table.

**The case for Option B (open class hierarchy):**

The pattern across the industry is clear — event taxonomies grow. Claude Code went from a handful to 23+. Strands has single-agent and multi-agent events. Spring AI's ToolCallAdvisor keeps adding template methods. A closed enum is fighting gravity.

But pure string-based events (like Node.js EventEmitter) lose type safety and make the `HookInput` shape ambiguous — what data does a hook get for event `"foo"`? Strands solves this with a class hierarchy where the event *is* the input:

```java
// The event type IS the input — no separate enum + input pairing
sealed interface HookEvent permits
    BeforeToolCall, AfterToolCall,
    SessionStart, SessionEnd { }

record BeforeToolCall(String toolName, String toolInput, 
                      HookContext context) implements HookEvent { }

record AfterToolCall(String toolName, String toolInput, 
                     String result, Duration duration,
                     HookContext context) implements HookEvent { }
```

This collapses your current `AgentHookEvent` enum + `HookInput` sealed interface into a single hierarchy. The event *is* the input. Adding a new event means adding a new record — no enum to modify, no parallel `HookInput` variant to keep in sync.

**The decision model question resolves cleanly too.** Instead of "what decisions are valid for this event type?" enforced at runtime, you type it at the API level:

```java
@FunctionalInterface
interface AgentHook<E extends HookEvent> {
    HookDecision handle(E event);
}
```

Registry registration becomes:

```java
registry.on(BeforeToolCall.class, event -> {
    if (event.toolName().equals("bookTable")) 
        return HookDecision.block("Over budget");
    return HookDecision.proceed();
});
```

Dispatch is by class — no enum switch, no string matching. Adapters add events by adding records:

```java
// In claude-agent-sdk adapter module
record PreCompact(HookContext context) implements HookEvent { }

// In strands adapter
record BeforeNodeCall(String nodeName, HookContext context) 
    implements HookEvent { }
```

**The sealed vs. unsealed question:** Make `HookEvent` an **unsealed interface**. The core module defines the portable events. Adapters define their own. Pattern matching still works — you just don't get exhaustiveness checks, which you don't want anyway since adapters add events the core doesn't know about.

**On the decision model per event type:** Two options here:

1. **Uniform** — every hook returns `HookDecision`, but the registry ignores Block/Modify for observation-only events (runtime enforcement)
2. **Split** — observation events use a different hook interface that returns `void`

I'd lean toward uniform with runtime enforcement. It keeps one hook interface, one registry API, and the constraint is documented. A hook that returns `Block` on `SessionStart` gets a logged warning and treated as `Proceed`. Simple, discoverable.

**What I'd actually build:**

1. `HookEvent` — unsealed interface, core defines 4 records (tool + session)
2. `HookDecision` — keep as-is, it's proven
3. `AgentHook<E extends HookEvent>` — generic functional interface
4. `AgentHookRegistry` — registration by `Class<E>`, dispatch by event instance class, priority ordering, reverse ordering for "after" events
5. `AgentHookProvider` — registers hooks against the registry (unchanged contract)
6. Tool pattern matching moves to a convenience method: `registry.onTool("book.*", BeforeToolCall.class, hook)`

The workshop code barely changes — you register against `BeforeToolCall.class` instead of `AgentHookEvent.BEFORE_TOOL_CALL`. Actually reads better.


Good call. Let me lay out the full type system.

---

## Current v0.1 (two parallel hierarchies)

```
AgentHookEvent (enum)          HookInput (sealed interface)
├── BEFORE_TOOL_CALL           ├── BeforeToolCall(toolName, input, context)
├── AFTER_TOOL_CALL            ├── AfterToolCall(toolName, input, result, duration, context)
├── SESSION_START              ├── SessionStart(context)
└── SESSION_END                └── SessionEnd(context)

AgentHook: HookDecision handle(HookInput input)
Registry:  on(AgentHookEvent, AgentHook)
```

Problem: enum and sealed interface must stay in sync. Adding an event means touching both. Closed to adapters.

---

## Proposed v0.2 (unified event hierarchy)

```
HookEvent (unsealed interface)
│
├── core (agent-hooks-core)
│   ├── BeforeToolCall(toolName, toolInput, context)
│   ├── AfterToolCall(toolName, toolInput, result, duration, exception, context)
│   ├── SessionStart(sessionId, context)
│   └── SessionEnd(sessionId, context)
│
├── spring adapter (agent-hooks-spring) — future
│   ├── BeforeModelCall(request, context)
│   └── AfterModelCall(request, response, duration, context)
│
└── claude adapter (separate repo) — future
    ├── PreCompact(context)
    └── SubagentStop(agentName, context)
```

One hierarchy. The event *is* the input. No parallel enum to maintain.

---

## Core types (5 files instead of 7)

```
HookEvent.java           — unsealed marker interface
  method: HookContext context()

HookDecision.java        — sealed interface + 4 records (unchanged)
  ├── Proceed
  ├── Block(reason)
  ├── Modify(modifiedInput)
  └── Retry(reason)

AgentHook.java           — @FunctionalInterface
  HookDecision handle(E event)    // generic: <E extends HookEvent>

AgentHookProvider.java   — interface (unchanged)
  void registerHooks(AgentHookRegistry registry)

AgentHookRegistry.java   — dispatch engine
  on(Class<E>, AgentHook<E>)
  on(Class<E>, int priority, AgentHook<E>)
  onTool(String pattern, Class<E>, AgentHook<E>)   // E must be tool event
  register(AgentHookProvider)
  dispatch(HookEvent)    // routes by event.getClass()
```

`HookContext` and `ToolCallRecord` stay the same — they're state containers, not event types.

---

## Registration comparison

```
v0.1:
  registry.on(BEFORE_TOOL_CALL, input -> {
      var before = (BeforeToolCall) input;    // cast needed
      if (before.toolName().equals("bookTable"))
          return HookDecision.block("Over budget");
      return HookDecision.proceed();
  });

v0.2:
  registry.on(BeforeToolCall.class, event -> {
      if (event.toolName().equals("bookTable"))
          return HookDecision.block("Over budget");
      return HookDecision.proceed();
  });
```

No cast. The generic carries the type through.

---

## Dispatch behavior by event category

```
Tool events (BeforeToolCall, AfterToolCall):
  - Full decision algebra: Proceed, Block, Modify, Retry
  - Block short-circuits remaining hooks
  - Modify chains input to next hook
  - Retry only valid on AfterToolCall
  - AfterToolCall dispatches in REVERSE priority order

Session/Custom events (everything else):
  - Observation-only: Block/Modify/Retry → logged warning, treated as Proceed
  - Useful for metrics, tracing, journal recording
  - Same priority ordering
```

Runtime enforcement, not type-level. One hook interface, one registry API. The constraint is documented and logged, not a separate type system to learn.

---

## Tool pattern matching

The `onTool` convenience method only makes sense for events that carry a `toolName`. Two ways to handle this:

**Option 1: Marker interface**

```
ToolEvent extends HookEvent
  method: String toolName()

BeforeToolCall implements ToolEvent
AfterToolCall implements ToolEvent

registry.onTool(String pattern, Class<E extends ToolEvent>, hook)
```

**Option 2: Just convention** — `onTool` checks at runtime that the event class has a `toolName`. Simpler but less type-safe.

I'd go with Option 1. `ToolEvent` is a natural grouping that already exists implicitly in v0.1. Making it explicit costs one interface and gives you compile-time safety on `onTool`.

---

## What this means for the Spring adapter

`HookedToolCallback` barely changes — it constructs `BeforeToolCall` and `AfterToolCall` records and calls `registry.dispatch(event)` instead of `registry.dispatch(BEFORE_TOOL_CALL, input)`. The return type is still `HookDecision`.

`HookedTools.wrap()` is identical.

The auto-configuration can optionally fire `SessionStart`/`SessionEnd` if it has lifecycle hooks, but that's not required for the workshop.

---

## Summary of the delta from v0.1

```
Removed:
  - AgentHookEvent enum
  - HookInput sealed interface + 4 records

Added:
  - HookEvent unsealed interface
  - ToolEvent sub-interface (toolName carrier)

Changed:
  - AgentHook: handle(HookInput) → handle(E extends HookEvent)
  - Registry: on(enum, hook) → on(Class<E>, hook)
  - Registry: dispatch(enum, input) → dispatch(HookEvent)
  - BeforeToolCall/AfterToolCall: were HookInput records → now HookEvent records
  - AfterToolCall dispatch: forward order → reverse priority order

Unchanged:
  - HookDecision (sealed, 4 variants)
  - HookContext
  - ToolCallRecord
  - AgentHookProvider
  - All Spring adapter classes (minor signature updates)
```

Net effect: fewer types, more extensible, better ergonomics at the registration site. The workshop code reads cleaner.

---

Runtime enforcement is the right call. One interface, one registry, one mental model. If someone returns `Block` from a `SessionStart` hook, log a warning and proceed. They'll read the javadoc and fix it. No need to split the type system over it.

So the design is:

```
HookEvent (unsealed interface)
  └── context(): HookContext

ToolEvent (extends HookEvent)
  └── toolName(): String
  └── toolInput(): String

BeforeToolCall  implements ToolEvent    — steerable (full decision algebra)
AfterToolCall   implements ToolEvent    — steerable (full decision algebra)
SessionStart    implements HookEvent    — observable (Proceed only, runtime enforced)
SessionEnd      implements HookEvent    — observable (Proceed only, runtime enforced)

HookDecision    sealed: Proceed | Block(reason) | Modify(modifiedInput) | Retry(reason)
AgentHook<E>    functional: E → HookDecision
AgentHookRegistry   dispatch by Class<E>, reverse order for "after" events
```

Adapters add records, implement `HookEvent`, done. No core changes needed.


