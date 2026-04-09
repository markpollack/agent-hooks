package io.github.markpollack.hooks.event;

/**
 * Marker interface for tool-related events. Enables type-safe tool name pattern matching
 * via {@link io.github.markpollack.hooks.registry.AgentHookRegistry#onTool}.
 *
 * <p>
 * Core tool events: {@link BeforeToolCall}, {@link AfterToolCall}.
 *
 * @see HookEvent
 */
public interface ToolEvent extends HookEvent {

	/**
	 * The name of the tool.
	 * @return the tool name
	 */
	String toolName();

	/**
	 * The JSON input arguments for the tool.
	 * @return the tool input
	 */
	String toolInput();

}
