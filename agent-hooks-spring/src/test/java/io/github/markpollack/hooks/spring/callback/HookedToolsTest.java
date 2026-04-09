package io.github.markpollack.hooks.spring.callback;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HookedToolsTest {

	@Test
	void wrapToolObjectsShouldReturnHookedCallbacks() {
		AgentHookRegistry registry = new AgentHookRegistry();
		HookContext hookContext = new HookContext();

		ToolCallbackProvider provider = HookedTools.wrap(registry, hookContext, new SampleTools());
		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0]).isInstanceOf(HookedToolCallback.class);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("greet");
	}

	@Test
	void wrapExistingCallbacksShouldReturnHookedCallbacks() {
		AgentHookRegistry registry = new AgentHookRegistry();
		HookContext hookContext = new HookContext();

		ToolCallback cb = mock(ToolCallback.class);
		when(cb.getToolDefinition()).thenReturn(
				ToolDefinition.builder().name("myTool").description("desc").inputSchema("{}").build());

		ToolCallback[] wrapped = HookedTools.wrap(registry, hookContext, cb);

		assertThat(wrapped).hasSize(1);
		assertThat(wrapped[0]).isInstanceOf(HookedToolCallback.class);
	}

	static class SampleTools {

		@Tool(description = "Say hello")
		public String greet(String name) {
			return "Hello, " + name;
		}

	}

}
