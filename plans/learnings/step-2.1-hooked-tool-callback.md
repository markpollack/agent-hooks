# Step 2.1: HookedToolCallback

## What was done
- Implemented HookedToolCallback wrapping ToolCallback with before/after dispatch
- Block returns reason as tool result, delegate never called
- Modify passes modified input to delegate
- Exception in delegate captured in AfterToolCall, then re-thrown
- Blocked calls also recorded in HookContext history
- 9 tests with Mockito mock ToolCallback

## Pitfall
- Building spring module alone (`-pl agent-hooks-spring`) fails — core not installed to local repo
- Must build from reactor root (`./mvnw test`) or install core first

## API notes
- ToolDefinition.builder().name().description().inputSchema() — all required for mock setup
- ToolMetadata.builder().build() — returns default (returnDirect=false)
- ToolCallback.call(String, @Nullable ToolContext) — the method to intercept
