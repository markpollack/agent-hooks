# Step 2.0: Stage 2 Entry

## Core API review
- All types stable, no changes needed
- AfterToolCall already includes hookContext (added in 1.1)

## Spring AI integration approach
- `HookedToolCallback` wraps `ToolCallback`, delegates `getToolDefinition()` and `getToolMetadata()`
- Intercept: `call(String toolInput, ToolContext toolContext)` — dispatch BEFORE, delegate, dispatch AFTER
- Block: return reason string as tool result (agent sees it), never call delegate
- Modify: pass modified input to delegate's `call()`
- Record ToolCallRecord in HookContext after each call

## MethodToolCallbackProvider
- In `spring-ai-model` (transitive dep of `spring-ai-client-chat`)
- Builder API: `MethodToolCallbackProvider.builder().toolObjects(...).build()`
- `HookedTools.wrap()` will use this to wrap @Tool-annotated objects

## Auto-configuration
- `@AutoConfiguration @ConditionalOnClass(ToolCallback.class)`
- Collect all `AgentHookProvider` beans → build registry
- Create `HookContext` bean (session-scoped in production, but prototype/singleton for workshop)
