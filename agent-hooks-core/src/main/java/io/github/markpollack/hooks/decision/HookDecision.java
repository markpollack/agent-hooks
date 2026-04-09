package io.github.markpollack.hooks.decision;

/**
 * Sealed decision types returned by hooks.
 *
 * <p>
 * {@link Block} and {@link Modify} are only valid for
 * {@link io.github.markpollack.hooks.event.AgentHookEvent#BEFORE_TOOL_CALL BEFORE_TOOL_CALL};
 * {@link Retry} is only valid for
 * {@link io.github.markpollack.hooks.event.AgentHookEvent#AFTER_TOOL_CALL AFTER_TOOL_CALL}.
 * {@link Proceed} is valid for all events.
 */
public sealed interface HookDecision permits HookDecision.Proceed, HookDecision.Block, HookDecision.Modify,
		HookDecision.Retry {

	/** Continue unchanged. Valid for all events. */
	record Proceed() implements HookDecision {
	}

	/**
	 * Block the tool call. The agent sees the reason as the tool result.
	 * @param reason explanation shown to the agent
	 */
	record Block(String reason) implements HookDecision {
	}

	/**
	 * Replace the tool input before execution.
	 * @param modifiedInput the replacement JSON input
	 */
	record Modify(String modifiedInput) implements HookDecision {
	}

	/**
	 * Request the tool call be retried. Only valid for AFTER_TOOL_CALL.
	 * @param reason explanation for the retry
	 */
	record Retry(String reason) implements HookDecision {
	}

	/** Create a {@link Proceed} decision. */
	static Proceed proceed() {
		return new Proceed();
	}

	/**
	 * Create a {@link Block} decision.
	 * @param reason explanation shown to the agent
	 */
	static Block block(String reason) {
		return new Block(reason);
	}

	/**
	 * Create a {@link Modify} decision.
	 * @param modifiedInput the replacement JSON input
	 */
	static Modify modify(String modifiedInput) {
		return new Modify(modifiedInput);
	}

	/**
	 * Create a {@link Retry} decision.
	 * @param reason explanation for the retry
	 */
	static Retry retry(String reason) {
		return new Retry(reason);
	}

}
