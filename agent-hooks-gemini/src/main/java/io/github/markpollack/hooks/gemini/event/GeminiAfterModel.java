package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired after a model invocation completes in the Gemini CLI. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * <p>Both JSON parameters are intentionally opaque (raw JSON strings). Consumers
 * who need structured access should parse them — the schema is Gemini-internal
 * and subject to change.
 *
 * @param llmRequestJson the raw JSON of the LLM request (opaque)
 * @param llmResponseJson the raw JSON of the LLM response (opaque)
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiAfterModel(String llmRequestJson, String llmResponseJson, String sessionId,
		HookContext context) implements HookEvent {
}
