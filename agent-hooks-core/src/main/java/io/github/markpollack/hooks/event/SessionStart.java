package io.github.markpollack.hooks.event;

import io.github.markpollack.hooks.decision.HookContext;

/**
 * Event fired when an agent session begins. Observable only — steering decisions
 * (Block, Modify, Retry) are logged as warnings and treated as Proceed.
 *
 * @param sessionId unique identifier for this agent session
 * @param context mutable session state shared across hooks
 */
public record SessionStart(String sessionId, HookContext context) implements HookEvent {
}
