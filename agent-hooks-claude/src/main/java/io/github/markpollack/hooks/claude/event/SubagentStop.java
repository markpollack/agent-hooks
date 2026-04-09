package io.github.markpollack.hooks.claude.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;

/**
 * Event fired when a Claude sub-agent stops. Observation-only — hooks can
 * observe the stop but cannot prevent it.
 *
 * @param sessionId the Claude CLI session identifier
 * @param stopHookActive whether the stop hook is active in the Claude CLI
 * @param context mutable session state shared across hooks
 */
public record SubagentStop(String sessionId, boolean stopHookActive, HookContext context) implements HookEvent {
}
