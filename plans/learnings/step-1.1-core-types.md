# Step 1.1: Core Types

## What was done
- Implemented 7 core type files + 1 stub (AgentHookRegistry) + 2 test files
- AgentHookEvent enum (4 events)
- HookDecision sealed interface with 4 record variants + static factory methods
- ToolCallRecord record (6 fields)
- HookContext class (ConcurrentHashMap state, CopyOnWriteArrayList history)
- HookInput sealed interface with 4 record variants (all include hookContext)
- AgentHook @FunctionalInterface
- AgentHookProvider interface
- AgentHookRegistry stub (methods throw UnsupportedOperationException — Step 1.2)

## Key decisions
- AfterToolCall includes hookContext (added vs original DESIGN.md which omitted it) — hooks need context for stateful after-call logic
- AgentHookRegistry created as stub so AgentHookProvider can reference it without circular compilation issues
- HookContext.history() returns Collections.unmodifiableList (not defensive copy) — CopyOnWriteArrayList snapshot semantics are sufficient

## Test coverage
- 12 tests passing: HookDecisionTest (6), HookContextTest (6)
- Covers: factory methods, sealed exhaustiveness, get/put, type mismatch, history immutability, ordering
