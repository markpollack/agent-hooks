package io.github.markpollack.hooks.decision;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Immutable record of a completed (or blocked) tool call. Stored in
 * {@link HookContext#history()}.
 *
 * @param toolName the name of the tool
 * @param toolInput the JSON input arguments
 * @param toolResult the tool result (null if the tool threw an exception or was blocked)
 * @param duration execution time
 * @param decision what the before-hook decided (Proceed, Block, or Modify)
 * @param timestamp when the call started
 */
public record ToolCallRecord(String toolName, String toolInput, @Nullable String toolResult, Duration duration,
		HookDecision decision, Instant timestamp) {
}
