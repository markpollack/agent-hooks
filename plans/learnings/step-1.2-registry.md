# Step 1.2: Registry + Dispatch Engine

## What was done
- Implemented full AgentHookRegistry: on(), onTool(), register(), dispatch()
- PrioritizedHook internal record with priority, optional tool pattern, hook
- CopyOnWriteArrayList per event for thread-safe registration
- 10 registry tests covering all dispatch semantics

## Key semantics
- Default priority: 100
- Block short-circuits immediately
- Modify chains: creates new BeforeToolCall with modified input for subsequent hooks
- Retry validates event type (only AFTER_TOOL_CALL)
- Block/Modify validate event type (only BEFORE_TOOL_CALL)
- Exception in hook → catch and return Proceed (fail-open)
- Tool pattern hooks only fire when tool name matches regex; non-tool events (session) skip pattern hooks

## Test coverage
- 22 total tests: HookDecisionTest (6), HookContextTest (6), AgentHookRegistryTest (10)
