package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired before tool selection in the Gemini CLI. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * <p>{@code llmRequestJson} is intentionally opaque (raw JSON string).
 *
 * @param llmRequestJson the raw JSON of the LLM request (opaque)
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiBeforeToolSelection(String llmRequestJson, String sessionId,
		HookContext context) implements HookEvent {
}
