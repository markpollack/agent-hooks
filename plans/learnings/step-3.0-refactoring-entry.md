# Step 3.0: Refactoring Entry — Design Verification

## Migration Plan

### Files to DELETE (core)
- `event/AgentHookEvent.java` — enum replaced by class-based dispatch
- `event/HookInput.java` — sealed interface; records move to top-level files

### Files to CREATE (core)
- `event/HookEvent.java` — unsealed interface, `context()` method
- `event/ToolEvent.java` — sub-interface, `toolName()` + `toolInput()`
- `event/BeforeToolCall.java` — record implementing ToolEvent
- `event/AfterToolCall.java` — record implementing ToolEvent
- `event/SessionStart.java` — record implementing HookEvent
- `event/SessionEnd.java` — record implementing HookEvent

### Files to MODIFY (core)
- `spi/AgentHook.java` — add generic `<E extends HookEvent>`, change handle signature
- `registry/AgentHookRegistry.java` — full rewrite: type-based dispatch, `Map<Class<?>, List<PrioritizedHook<?>>>`, reverse ordering for after events, runtime enforcement

### Files to MODIFY (core tests)
- `AgentHookRegistryTest.java` — rewrite all tests for new API + add reverse ordering + runtime enforcement tests
- `ArchitectureTest.java` — update rules (remove AgentHookEvent references, add ToolEvent checks)
- `HookContextTest.java` — no changes needed

### Files to MODIFY (spring)
- `HookedToolCallback.java` — construct BeforeToolCall/AfterToolCall, call dispatch(event)
- `HookedToolCallbackTest.java` — update event construction
- Spring ArchitectureTest — may need rule updates

### Files UNCHANGED
- `decision/HookDecision.java` — sealed, 4 variants, proven
- `decision/HookContext.java` — state container, not an event type
- `decision/ToolCallRecord.java` — audit record
- `spi/AgentHookProvider.java` — contract unchanged
- `HookedToolCallbackProvider.java` — delegates, no event references
- `HookedTools.java` — static utility, no event references
- `AgentHooksAutoConfiguration.java` — no event references
- All `package-info.java` — @NullMarked stays

## Key Implementation Notes

1. Registry internal storage: `Map<Class<?>, List<PrioritizedHook<?>>>` with unchecked cast at dispatch — safe because `on(Class<E>, AgentHook<E>)` enforces the pairing at the public API.
2. Reverse ordering for AfterToolCall: check `event.getClass()` at dispatch time, reverse sort if it's an "after" event.
3. Runtime enforcement: `validateDecision()` changes from throwing to logging+proceeding for non-ToolEvent types. Still throws for Retry on BeforeToolCall (always invalid).
4. Event records move from nested types in `HookInput` to top-level files in the same `event` package.
