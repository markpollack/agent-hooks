package io.github.markpollack.hooks.spi;

import io.github.markpollack.hooks.registry.AgentHookRegistry;

/**
 * Registers hooks with the registry during initialization. Implement this to bundle
 * related hooks together (e.g., all expense policy hooks in one provider).
 *
 * <p>
 * In Spring Boot, declare as a {@code @Component} bean — the auto-configuration collects
 * all providers and registers them automatically.
 */
public interface AgentHookProvider {

	/**
	 * Register hooks with the given registry.
	 * @param registry the registry to register hooks with
	 */
	void registerHooks(AgentHookRegistry registry);

}
