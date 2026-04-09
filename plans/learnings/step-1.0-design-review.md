# Step 1.0: Design Review + Version Alignment

## What was done
- Updated POM versions: Spring AI 1.0.0-M7 → 2.0.0-M3, Spring Boot 3.4.3 → 4.1.0-M2
- Switched license from Apache 2.0 to BSL 1.1 (matching agent-journal pattern)
- Created LICENSE file (BSL 1.1, Change Date: 2029-04-01)
- Updated VISION.md and DESIGN.md with new versions and license
- Verified scaffold compiles with updated dependencies

## Key findings
- Spring AI 2.0.0-M3 ToolCallback API confirmed: `call(String toolInput)` and `call(String toolInput, @Nullable ToolContext toolContext)`
- `MethodToolCallbackProvider` is in `spring-ai-model` (transitive dep of `spring-ai-client-chat`) — accessible from agent-hooks-spring
- `ToolContext` is immutable (Collections.unmodifiableMap) — confirms DD-3 (HookContext as separate mutable state)
- `spring-ai-client-chat` artifact name unchanged in 2.0.0-M3

## Decisions
- Align with workshop versions exactly (Spring AI 2.0.0-M3, Boot 4.1.0-M2) to avoid version conflicts in Stage 3
