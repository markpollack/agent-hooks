package io.github.markpollack.hooks.spi;

import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.HookInput;

/**
 * A hook that handles agent events and returns a decision.
 *
 * <p>
 * Implementations must:
 * <ul>
 * <li>Return a non-null {@link HookDecision}</li>
 * <li>Complete synchronously within a reasonable time</li>
 * <li>Be thread-safe if the agent runtime is multi-threaded</li>
 * </ul>
 *
 * <p>
 * If a hook throws an unchecked exception, the registry treats it as
 * {@link HookDecision#proceed()}.
 */
@FunctionalInterface
public interface AgentHook {

	/**
	 * Handle an agent event and return a decision.
	 * @param input the event-specific input
	 * @return the decision (never null)
	 */
	HookDecision handle(HookInput input);

}
