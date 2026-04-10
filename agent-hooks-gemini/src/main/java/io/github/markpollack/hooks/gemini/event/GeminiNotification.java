package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;
import org.jspecify.annotations.Nullable;

/**
 * Event fired when the Gemini CLI sends a notification. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * @param notificationType the type of notification (e.g. "info", "warning")
 * @param message the notification message
 * @param detailsJson additional details as raw JSON (nullable — not all notifications include details)
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiNotification(String notificationType, String message, @Nullable String detailsJson,
		String sessionId, HookContext context) implements HookEvent {
}
