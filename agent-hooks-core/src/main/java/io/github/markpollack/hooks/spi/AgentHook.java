package io.github.markpollack.hooks.spi;

import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.HookEvent;

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
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * registry.on(BeforeToolCall.class, event -> {
 *     if (event.toolName().equals("bookTable"))
 *         return HookDecision.block("Over budget");
 *     return HookDecision.proceed();
 * });
 * }</pre>
 *
 * @param <E> the event type this hook handles
 */
@FunctionalInterface
public interface AgentHook<E extends HookEvent> {

	/**
	 * Handle an agent event and return a decision.
	 * @param event the event
	 * @return the decision (never null)
	 */
	HookDecision handle(E event);

}
