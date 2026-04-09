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

## Stage 2: Spring AI Adapter

### HookedToolCallback (Step 2.1)
- Wraps ToolCallback: BEFORE_TOOL_CALL dispatch → delegate.call() → AFTER_TOOL_CALL dispatch
- Block returns reason as tool result, delegate never called; blocked calls still recorded in history
- Modify passes modified input to delegate
- Exception in delegate captured in AfterToolCall, then re-thrown
- Build from reactor root (`./mvnw test`), not `-pl agent-hooks-spring` alone (core not in local repo)

### Provider + Auto-Config (Step 2.2)
- HookedToolCallbackProvider wraps ToolCallbackProvider — each ToolCallback becomes HookedToolCallback
- HookedTools.wrap(registry, hookContext, toolObjects...) — main entry point for workshop
- AgentHooksAutoConfiguration: @AutoConfiguration @ConditionalOnClass(ToolCallback.class)
  - Creates registry from all AgentHookProvider beans
  - Creates default HookContext bean
  - Both @ConditionalOnMissingBean — user can override
- ApplicationContextRunner for testing auto-config (fast, no full Spring context)

### Test count
- 35 total: 22 core + 9 HookedToolCallback + 4 auto-config
