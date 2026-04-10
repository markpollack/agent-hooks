package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired before the Gemini agent processes a prompt. Observation-only
 * — hooks can inspect but cannot steer execution.
 *
 * @param prompt the user's prompt text
 * @param sessionId the Gemini CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record GeminiBeforeAgent(String prompt, String sessionId, HookContext context) implements HookEvent {
}
