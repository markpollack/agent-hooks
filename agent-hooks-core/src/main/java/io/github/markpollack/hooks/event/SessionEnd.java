package io.github.markpollack.hooks.event;

import java.time.Duration;

import io.github.markpollack.hooks.decision.HookContext;

/**
 * Event fired when an agent session ends. Observable only — steering decisions
 * (Block, Modify, Retry) are logged as warnings and treated as Proceed.
 *
 * @param sessionId unique identifier for this agent session
 * @param context mutable session state shared across hooks
 * @param totalDuration total duration of the session
 */
public record SessionEnd(String sessionId, HookContext context, Duration totalDuration) implements HookEvent {
}
