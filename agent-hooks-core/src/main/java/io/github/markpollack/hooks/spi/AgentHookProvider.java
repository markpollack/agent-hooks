package io.github.markpollack.hooks.spi;

import io.github.markpollack.hooks.registry.AgentHookRegistry;

/**
 * Registers hooks with the registry during initialization. Implement this to bundle
 * related hooks together.
 */
public interface AgentHookProvider {

	void registerHooks(AgentHookRegistry registry);

}
