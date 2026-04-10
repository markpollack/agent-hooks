package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired after the Gemini agent finishes processing a prompt. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * <p>{@code stopHookActive} is informational only — Gemini signals that the agent
 * is stopping, but this is not steerable through our hook system.
 *
 * @param prompt the user's prompt text
 * @param promptResponse the agent's response text
 * @param stopHookActive whether the Gemini stop hook is active (informational only)
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiAfterAgent(String prompt, String promptResponse, boolean stopHookActive, String sessionId,
		HookContext context) implements HookEvent {
}
