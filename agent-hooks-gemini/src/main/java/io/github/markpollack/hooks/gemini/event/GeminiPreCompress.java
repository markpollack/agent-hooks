package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired before context compression in the Gemini CLI. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * @param trigger how the compression was triggered ({@code "manual"} or {@code "auto"})
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiPreCompress(String trigger, String sessionId, HookContext context) implements HookEvent {
}
