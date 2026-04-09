package io.github.markpollack.hooks.decision;

/**
 * Sealed decision types returned by hooks. Block and Modify are only valid for
 * BEFORE_TOOL_CALL; Retry is only valid for AFTER_TOOL_CALL.
 */
public sealed interface HookDecision permits HookDecision.Proceed, HookDecision.Block, HookDecision.Modify,
		HookDecision.Retry {

	record Proceed() implements HookDecision {
	}

	record Block(String reason) implements HookDecision {
	}

	record Modify(String modifiedInput) implements HookDecision {
	}

	record Retry(String reason) implements HookDecision {
	}

	static Proceed proceed() {
		return new Proceed();
	}

	static Block block(String reason) {
		return new Block(reason);
	}

	static Modify modify(String modifiedInput) {
		return new Modify(modifiedInput);
	}

	static Retry retry(String reason) {
		return new Retry(reason);
	}

}
