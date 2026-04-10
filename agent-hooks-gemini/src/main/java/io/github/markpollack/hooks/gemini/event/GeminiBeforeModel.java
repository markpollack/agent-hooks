package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired before a model invocation in the Gemini CLI. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * <p>{@code llmRequestJson} is intentionally opaque (raw JSON string). Consumers
 * who need structured access should parse it themselves — the schema is Gemini-internal
 * and subject to change.
 *
 * @param llmRequestJson the raw JSON of the LLM request (opaque — parse at your own risk)
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiBeforeModel(String llmRequestJson, String sessionId, HookContext context) implements HookEvent {
}
