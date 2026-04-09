package io.github.markpollack.hooks.registry;

import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AgentHookEvent;
import io.github.markpollack.hooks.event.HookInput;
import io.github.markpollack.hooks.spi.AgentHook;
import io.github.markpollack.hooks.spi.AgentHookProvider;

/**
 * Registry and dispatcher for agent hooks. Hooks are registered per event with optional
 * priority and tool name pattern matching.
 *
 * <p>
 * Implementation is provided in Step 1.2.
 */
public class AgentHookRegistry {

	public void on(AgentHookEvent event, AgentHook hook) {
		throw new UnsupportedOperationException("Implemented in Step 1.2");
	}

	public void on(AgentHookEvent event, int priority, AgentHook hook) {
		throw new UnsupportedOperationException("Implemented in Step 1.2");
	}

	public void onTool(String toolNamePattern, AgentHookEvent event, AgentHook hook) {
		throw new UnsupportedOperationException("Implemented in Step 1.2");
	}

	public void register(AgentHookProvider provider) {
		throw new UnsupportedOperationException("Implemented in Step 1.2");
	}

	public HookDecision dispatch(AgentHookEvent event, HookInput input) {
		throw new UnsupportedOperationException("Implemented in Step 1.2");
	}

}
