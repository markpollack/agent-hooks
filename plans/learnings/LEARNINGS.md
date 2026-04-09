# Learnings — agent-hooks

## Stage 1: Foundation + Core API

### Version alignment (Step 1.0)
- Spring AI 2.0.0-M3 + Spring Boot 4.1.0-M2 — matches workshop `art-of-building-agents`
- `spring-ai-client-chat` artifact name unchanged in 2.0.0-M3
- `MethodToolCallbackProvider` in `spring-ai-model` (transitive dep) — accessible from agent-hooks-spring
- ToolContext is immutable — confirms need for separate HookContext

### Core API (Steps 1.1-1.2)
- 8 source files in agent-hooks-core: AgentHookEvent, HookDecision (sealed, 4 variants), ToolCallRecord, HookContext, HookInput (sealed, 4 variants), AgentHook, AgentHookProvider, AgentHookRegistry
- AfterToolCall includes hookContext (addition to original DESIGN.md)
- Registry dispatch: priority ordering → Block short-circuits → Modify chains → exception = Proceed
- Default priority: 100. Tool pattern matching via regex. Validation: Retry only for AFTER_TOOL_CALL, Block/Modify only for BEFORE_TOOL_CALL
- 22 tests, all passing
