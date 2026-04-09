package io.github.markpollack.hooks.event;

/**
 * Portable hook events fired at the tool-call boundary.
 *
 * <p>
 * These events are runtime-agnostic — they work across Spring AI, Claude Agent SDK, and
 * any other adapter.
 */
public enum AgentHookEvent {

	/** Fired before a tool is executed. Hooks can block or modify the input. */
	BEFORE_TOOL_CALL,

	/** Fired after a tool completes. Hooks can observe results or trigger retry. */
	AFTER_TOOL_CALL,

	/** Fired when an agent session begins. */
	SESSION_START,

	/** Fired when an agent session ends. */
	SESSION_END

}
