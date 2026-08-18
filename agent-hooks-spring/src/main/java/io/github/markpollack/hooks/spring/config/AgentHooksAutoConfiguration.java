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
 *
 * <p>
 * <strong>The default {@link HookContext} bean is an application-wide singleton.</strong>
 * Every hooked tool call in the application then shares one state map and one tool-call
 * history. That is what a single-user CLI or desktop application wants. It is not what a
 * multi-user server wants: one user's tool arguments and results become visible to hooks
 * running for another user, and the history grows for the lifetime of the application
 * context. A server should define its own request- or session-scoped {@code HookContext}
 * bean — {@link ConditionalOnMissingBean} means yours replaces this one — or construct the
 * context per conversation and wrap tools with
 * {@link io.github.markpollack.hooks.spring.callback.HookedTools#wrap} at that point.
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
