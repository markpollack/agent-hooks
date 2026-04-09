package io.github.markpollack.hooks.spi;

import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.HookInput;

/**
 * A hook that handles agent events and returns a decision.
 */
@FunctionalInterface
public interface AgentHook {

	HookDecision handle(HookInput input);

}
