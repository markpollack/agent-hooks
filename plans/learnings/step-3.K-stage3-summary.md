# Step 3.K: Stage 3 Summary — Event Hierarchy Refactoring

## What changed (v0.1 → v0.2)

### Core type system
- **Deleted**: `AgentHookEvent` enum (4 values), `HookInput` sealed interface (4 nested records)
- **Created**: Open event hierarchy — `HookEvent` (unsealed interface), `ToolEvent` (sub-interface with `toolName()` + `toolInput()`), 4 record implementations: `BeforeToolCall`, `AfterToolCall`, `SessionStart`, `SessionEnd`
- **Modified**: `AgentHook<E extends HookEvent>` — now generic (was raw `AgentHook`)

### Registry
- Storage: `Map<Class<?>, CopyOnWriteArrayList<PrioritizedHook<?>>>` (was `Map<AgentHookEvent, ...>`)
- Registration: `on(Class<E> eventType, AgentHook<E> hook)` — type token pairs event class with typed hook
- Dispatch: single-arg `dispatch(HookEvent event)` — event IS the input (was two-arg `dispatch(enum, HookInput)`)
- **Reverse priority ordering**: `AfterToolCall` dispatches hooks in descending priority (cleanup ordering)
- **Runtime enforcement**: non-ToolEvent decisions (Block/Modify/Retry) → treated as Proceed
- **Unchecked cast**: `((AgentHook<HookEvent>) ph.hook()).handle(currentEvent)` — safe because public API enforces Class<E> ↔ AgentHook<E> pairing (same pattern as Guava TypeToInstanceMap)

### Spring adapter
- `HookedToolCallback.call()`: constructs `BeforeToolCall`/`AfterToolCall` directly (was `HookInput.BeforeToolCall` + `AgentHookEvent.BEFORE_TOOL_CALL`)
- Tests: `registry.on(BeforeToolCall.class, event -> ...)` (was `registry.on(AgentHookEvent.BEFORE_TOOL_CALL, input -> ...)`)

## Key decisions
- **Option B chosen**: Open class hierarchy (Strands pattern) over closed enum (Claude SDK pattern)
- Event IS the input — no parallel `enum` + `sealed interface` hierarchies
- `ToolEvent` marker enables compile-time safety on `onTool()` registration
- Non-tool events are observation-only (runtime enforcement, not type-level)

## Test results
- 15 core tests (10 ported + 5 new v0.2 tests)
- Spring adapter tests unchanged in count, all migrated to v0.2 API
- ArchUnit tests: no changes needed (reference packages not classes)
- `./mvnw verify` green, `./mvnw clean install` successful

## Files changed
- Created: 6 (event types)
- Deleted: 2 (AgentHookEvent.java, HookInput.java)
- Modified: 6 (AgentHook, AgentHookRegistry, 4 test files)
- Unchanged: HookDecision, HookContext, ToolCallRecord, HookedToolCallbackProvider, HookedTools, auto-config, package-info files
