package io.github.markpollack.hooks.event;

import java.time.Duration;

import io.github.markpollack.hooks.decision.HookContext;
import org.jspecify.annotations.Nullable;

/**
 * Sealed input types per hook event. Each variant carries the data relevant to its
 * event.
 *
 * @see AgentHookEvent
 */
public sealed interface HookInput
		permits HookInput.BeforeToolCall, HookInput.AfterToolCall, HookInput.SessionStart, HookInput.SessionEnd {

	/**
	 * Input for {@link AgentHookEvent#BEFORE_TOOL_CALL}. Hooks can block or modify the
	 * tool input.
	 *
	 * @param toolName the name of the tool about to be called
	 * @param toolInput the JSON input arguments for the tool
	 * @param hookContext mutable session state shared across hooks
	 */
	record BeforeToolCall(String toolName, String toolInput, HookContext hookContext) implements HookInput {
	}

	/**
	 * Input for {@link AgentHookEvent#AFTER_TOOL_CALL}. Hooks can observe results or
	 * trigger retry.
	 *
	 * @param toolName the name of the tool that was called
	 * @param toolInput the JSON input arguments that were passed
	 * @param toolResult the tool result (null if the tool threw an exception)
	 * @param duration how long the tool execution took
	 * @param exception the exception thrown by the tool (null on success)
	 * @param hookContext mutable session state shared across hooks
	 */
	record AfterToolCall(String toolName, String toolInput, @Nullable String toolResult, Duration duration,
			@Nullable Exception exception, HookContext hookContext) implements HookInput {
	}

	/**
	 * Input for {@link AgentHookEvent#SESSION_START}.
	 *
	 * @param sessionId unique identifier for this agent session
	 * @param hookContext mutable session state shared across hooks
	 */
	record SessionStart(String sessionId, HookContext hookContext) implements HookInput {
	}

	/**
	 * Input for {@link AgentHookEvent#SESSION_END}.
	 *
	 * @param sessionId unique identifier for this agent session
	 * @param hookContext mutable session state shared across hooks
	 * @param totalDuration total duration of the session
	 */
	record SessionEnd(String sessionId, HookContext hookContext, Duration totalDuration) implements HookInput {
	}

}
