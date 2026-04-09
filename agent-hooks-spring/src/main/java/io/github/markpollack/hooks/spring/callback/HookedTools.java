package io.github.markpollack.hooks.spring.callback;

import java.util.Arrays;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.registry.AgentHookRegistry;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Static utility for wrapping tool objects or callbacks with hook dispatch.
 */
public final class HookedTools {

	private HookedTools() {
	}

	/**
	 * Wraps {@code @Tool}-annotated objects. Creates a {@link MethodToolCallbackProvider}
	 * and wraps each callback with {@link HookedToolCallback}.
	 */
	public static ToolCallbackProvider wrap(AgentHookRegistry registry, HookContext hookContext,
			Object... toolObjects) {
		ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(toolObjects).build();
		return new HookedToolCallbackProvider(provider, registry, hookContext);
	}

	/**
	 * Wraps existing {@link ToolCallback} instances with {@link HookedToolCallback}.
	 */
	public static ToolCallback[] wrap(AgentHookRegistry registry, HookContext hookContext,
			ToolCallback... callbacks) {
		return Arrays.stream(callbacks)
			.map(cb -> (ToolCallback) new HookedToolCallback(cb, registry, hookContext))
			.toArray(ToolCallback[]::new);
	}

}
