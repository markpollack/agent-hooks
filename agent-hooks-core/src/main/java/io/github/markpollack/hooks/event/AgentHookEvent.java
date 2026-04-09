package io.github.markpollack.hooks.event;

/**
 * Portable hook events fired at the tool-call boundary.
 */
public enum AgentHookEvent {

	BEFORE_TOOL_CALL,

	AFTER_TOOL_CALL,

	SESSION_START,

	SESSION_END

}
