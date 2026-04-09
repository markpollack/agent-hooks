package io.github.markpollack.hooks.decision;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable record of a completed (or blocked) tool call.
 */
public record ToolCallRecord(String toolName, String toolInput, String toolResult, Duration duration,
		HookDecision decision, Instant timestamp) {
}
