package io.github.markpollack.hooks.claude.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired when the user submits a prompt to the Claude CLI. Observation-only
 * — hooks can inspect the prompt but cannot steer execution.
 *
 * @param prompt the user's prompt text
 * @param sessionId the Claude CLI session identifier
 * @param context mutable session state shared across hooks
 */
public record UserPromptSubmit(String prompt, String sessionId, HookContext context) implements HookEvent {
}
