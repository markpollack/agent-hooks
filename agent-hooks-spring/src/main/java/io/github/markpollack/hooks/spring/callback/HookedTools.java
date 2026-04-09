package io.github.markpollack.hooks.spring.callback;

import java.util.Arrays;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.registry.AgentHookRegistry;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Static utility for wrapping tool objects or callbacks with hook dispatch. This is the
 * primary entry point for integrating hooks in application code.
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * var provider = HookedTools.wrap(registry, hookContext, new RestaurantTools());
 * var chatClient = ChatClient.builder(model)
 *     .defaultToolCallbacks(provider)
 *     .build();
 * }</pre>
 */
public final class HookedTools {

	private HookedTools() {
	}

	/**
	 * Wrap {@code @Tool}-annotated objects. Creates a {@link MethodToolCallbackProvider}
	 * and wraps each callback with {@link HookedToolCallback}.
	 * @param registry the hook registry for dispatching events
	 * @param hookContext the session state shared across hooks
	 * @param toolObjects objects with {@code @Tool}-annotated methods
	 * @return a provider that produces hooked callbacks
	 */
	public static ToolCallbackProvider wrap(AgentHookRegistry registry, HookContext hookContext,
			Object... toolObjects) {
		ToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(toolObjects).build();
		return new HookedToolCallbackProvider(provider, registry, hookContext);
	}

	/**
	 * Wrap existing {@link ToolCallback} instances with {@link HookedToolCallback}.
	 * @param registry the hook registry for dispatching events
	 * @param hookContext the session state shared across hooks
	 * @param callbacks the callbacks to wrap
	 * @return wrapped callbacks with hook dispatch
	 */
	public static ToolCallback[] wrap(AgentHookRegistry registry, HookContext hookContext,
			ToolCallback... callbacks) {
		return Arrays.stream(callbacks)
			.map(cb -> (ToolCallback) new HookedToolCallback(cb, registry, hookContext))
			.toArray(ToolCallback[]::new);
	}

}
