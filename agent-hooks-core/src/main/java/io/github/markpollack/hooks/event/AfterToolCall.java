package io.github.markpollack.hooks.event;

import java.time.Duration;

import io.github.markpollack.hooks.decision.HookContext;
import org.jspecify.annotations.Nullable;

/**
 * Event fired after a tool completes. Hooks can observe results or trigger retry.
 *
 * <p>
 * Valid decisions: {@link io.github.markpollack.hooks.decision.HookDecision.Proceed Proceed},
 * {@link io.github.markpollack.hooks.decision.HookDecision.Retry Retry}.
 *
 * <p>
 * Dispatched in <strong>reverse</strong> priority order (cleanup ordering).
 *
 * @param toolName the name of the tool that was called
 * @param toolInput the JSON input arguments that were passed
 * @param toolResult the tool result (null if the tool threw an exception)
 * @param duration how long the tool execution took
 * @param exception the exception thrown by the tool (null on success)
 * @param context mutable session state shared across hooks
 */
public record AfterToolCall(String toolName, String toolInput, @Nullable String toolResult, Duration duration,
		@Nullable Exception exception, HookContext context) implements ToolEvent {
}
