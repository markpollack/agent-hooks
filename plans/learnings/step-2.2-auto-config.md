# Step 2.2: Provider, Utility, and Auto-Configuration

## What was done
- HookedToolCallbackProvider: wraps ToolCallbackProvider, delegates to HookedToolCallback per callback
- HookedTools: static utility with two wrap() overloads — toolObjects (uses MethodToolCallbackProvider) and ToolCallback[]
- AgentHooksAutoConfiguration: @AutoConfiguration, creates registry from providers, creates default HookContext
- META-INF/spring/AutoConfiguration.imports registered
- 4 auto-configuration tests using ApplicationContextRunner

## Key patterns
- ApplicationContextRunner for testing auto-config (no full Spring context startup)
- @ConditionalOnMissingBean on both registry and hookContext — allows user override
- @ConditionalOnClass(ToolCallback.class) — only activates when Spring AI is on classpath
- HookedTools.wrap(registry, hookContext, toolObjects...) — the main entry point for workshop usage

## Test count
- 35 total: 22 core + 9 HookedToolCallback + 4 auto-config
