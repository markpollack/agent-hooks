package io.github.markpollack.hooks.spring.config;

import java.util.List;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import io.github.markpollack.hooks.spi.AgentHookProvider;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for agent-hooks. Creates an {@link AgentHookRegistry} from all
 * {@link AgentHookProvider} beans and a default {@link HookContext}.
 */
@AutoConfiguration
@ConditionalOnClass(ToolCallback.class)
public class AgentHooksAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AgentHookRegistry agentHookRegistry(List<AgentHookProvider> providers) {
		AgentHookRegistry registry = new AgentHookRegistry();
		providers.forEach(registry::register);
		return registry;
	}

	@Bean
	@ConditionalOnMissingBean
	public HookContext hookContext() {
		return new HookContext();
	}

}
