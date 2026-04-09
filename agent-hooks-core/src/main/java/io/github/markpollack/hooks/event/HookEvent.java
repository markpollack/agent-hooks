package io.github.markpollack.hooks.event;

import io.github.markpollack.hooks.decision.HookContext;

/**
 * Base interface for all hook events. Unsealed — adapters define new events by
 * implementing this interface.
 *
 * <p>
 * Core events:
 * <ul>
 * <li>{@link BeforeToolCall} — before a tool executes (steerable)</li>
 * <li>{@link AfterToolCall} — after a tool completes (steerable)</li>
 * <li>{@link SessionStart} — agent session begins (observable)</li>
 * <li>{@link SessionEnd} — agent session ends (observable)</li>
 * </ul>
 *
 * <p>
 * Adapters add events by creating new records that implement this interface. For tool-related
 * events, implement {@link ToolEvent} instead for tool name pattern matching support.
 *
 * @see ToolEvent
 */
public interface HookEvent {

	/**
	 * The session context shared across all hooks.
	 * @return the hook context (never null)
	 */
	HookContext context();

}
