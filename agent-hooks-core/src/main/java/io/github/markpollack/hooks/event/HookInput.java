package io.github.markpollack.hooks.event;

import java.time.Duration;

import io.github.markpollack.hooks.decision.HookContext;

/**
 * Sealed input types per hook event. Each variant carries the data relevant to its
 * event.
 */
public sealed interface HookInput
		permits HookInput.BeforeToolCall, HookInput.AfterToolCall, HookInput.SessionStart, HookInput.SessionEnd {

	record BeforeToolCall(String toolName, String toolInput, HookContext hookContext) implements HookInput {
	}

	record AfterToolCall(String toolName, String toolInput, String toolResult, Duration duration, Exception exception,
			HookContext hookContext) implements HookInput {
	}

	record SessionStart(String sessionId, HookContext hookContext) implements HookInput {
	}

	record SessionEnd(String sessionId, HookContext hookContext, Duration totalDuration) implements HookInput {
	}

}
