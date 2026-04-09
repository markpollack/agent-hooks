package io.github.markpollack.hooks.spring.callback;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HookedToolCallbackProviderTest {

	@Test
	void shouldWrapAllCallbacksFromDelegate() {
		ToolCallback cb1 = mockCallback("tool1");
		ToolCallback cb2 = mockCallback("tool2");
		ToolCallbackProvider delegate = ToolCallbackProvider.from(cb1, cb2);

		AgentHookRegistry registry = new AgentHookRegistry();
		HookContext hookContext = new HookContext();
		HookedToolCallbackProvider provider = new HookedToolCallbackProvider(delegate, registry, hookContext);

		ToolCallback[] wrapped = provider.getToolCallbacks();

		assertThat(wrapped).hasSize(2);
		assertThat(wrapped[0]).isInstanceOf(HookedToolCallback.class);
		assertThat(wrapped[1]).isInstanceOf(HookedToolCallback.class);
	}

	@Test
	void wrappedCallbacksShouldDispatchHooks() {
		ToolCallback cb = mockCallback("searchRestaurants");
		when(cb.call(any(String.class), any())).thenReturn("result");
		ToolCallbackProvider delegate = ToolCallbackProvider.from(cb);

		AgentHookRegistry registry = new AgentHookRegistry();
		HookContext hookContext = new HookContext();
		final boolean[] hookFired = { false };
		registry.on(BeforeToolCall.class, event -> {
			hookFired[0] = true;
			return HookDecision.proceed();
		});

		HookedToolCallbackProvider provider = new HookedToolCallbackProvider(delegate, registry, hookContext);
		ToolCallback[] wrapped = provider.getToolCallbacks();
		wrapped[0].call("{}", null);

		assertThat(hookFired[0]).isTrue();
	}

	private ToolCallback mockCallback(String name) {
		ToolCallback cb = mock(ToolCallback.class);
		when(cb.getToolDefinition()).thenReturn(
				ToolDefinition.builder().name(name).description("desc").inputSchema("{}").build());
		return cb;
	}

}
