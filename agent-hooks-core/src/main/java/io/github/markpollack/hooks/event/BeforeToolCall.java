package io.github.markpollack.hooks.event;

import io.github.markpollack.hooks.decision.HookContext;

/**
 * Event fired before a tool is executed. Hooks can block or modify the input.
 *
 * <p>
 * Valid decisions: {@link io.github.markpollack.hooks.decision.HookDecision.Proceed Proceed},
 * {@link io.github.markpollack.hooks.decision.HookDecision.Block Block},
 * {@link io.github.markpollack.hooks.decision.HookDecision.Modify Modify}.
 *
 * @param toolName the name of the tool about to be called
 * @param toolInput the JSON input arguments for the tool
 * @param context mutable session state shared across hooks
 */
public record BeforeToolCall(String toolName, String toolInput, HookContext context) implements ToolEvent {
}
