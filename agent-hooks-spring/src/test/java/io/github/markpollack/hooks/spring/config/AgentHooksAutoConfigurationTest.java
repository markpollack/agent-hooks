package io.github.markpollack.hooks.spring.config;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.SessionStart;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import io.github.markpollack.hooks.spi.AgentHookProvider;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHooksAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AgentHooksAutoConfiguration.class));

	@Test
	void registryShouldBeCreatedFromProviders() {
		contextRunner.withUserConfiguration(TestHookProviderConfig.class).run(context -> {
			assertThat(context).hasSingleBean(AgentHookRegistry.class);
			assertThat(context).hasSingleBean(HookContext.class);

			// Verify provider was registered
			AgentHookRegistry registry = context.getBean(AgentHookRegistry.class);
			HookContext hookContext = context.getBean(HookContext.class);
			HookDecision decision = registry.dispatch(new SessionStart("test", hookContext));
			assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
		});
	}

	@Test
	void hookContextBeanShouldBeCreated() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(HookContext.class);
		});
	}

	@Test
	void registryShouldWorkWithNoProviders() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(AgentHookRegistry.class);
		});
	}

	@Test
	void existingRegistryBeanShouldNotBeOverridden() {
		contextRunner.withUserConfiguration(CustomRegistryConfig.class).run(context -> {
			assertThat(context).hasSingleBean(AgentHookRegistry.class);
			// Should be the custom one
			AgentHookRegistry registry = context.getBean(AgentHookRegistry.class);
			assertThat(registry).isNotNull();
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class TestHookProviderConfig {

		@Bean
		AgentHookProvider testProvider() {
			return registry -> {
				registry.on(SessionStart.class, event -> HookDecision.proceed());
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomRegistryConfig {

		@Bean
		AgentHookRegistry agentHookRegistry() {
			return new AgentHookRegistry();
		}

	}

}
