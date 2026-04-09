package io.github.markpollack.hooks.spring.callback;

import java.util.Arrays;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.registry.AgentHookRegistry;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Wraps a {@link ToolCallbackProvider} so that every {@link ToolCallback} it produces is
 * wrapped with {@link HookedToolCallback}.
 *
 * <p>
 * All wrapped callbacks share the same {@link AgentHookRegistry} and
 * {@link HookContext}.
 */
public class HookedToolCallbackProvider implements ToolCallbackProvider {

	private final ToolCallbackProvider delegate;

	private final AgentHookRegistry registry;

	private final HookContext hookContext;

	/**
	 * Create a new HookedToolCallbackProvider.
	 * @param delegate the original provider to wrap
	 * @param registry the hook registry for dispatching events
	 * @param hookContext the session state shared across hooks
	 */
	public HookedToolCallbackProvider(ToolCallbackProvider delegate, AgentHookRegistry registry,
			HookContext hookContext) {
		this.delegate = delegate;
		this.registry = registry;
		this.hookContext = hookContext;
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		return Arrays.stream(delegate.getToolCallbacks())
			.map(cb -> (ToolCallback) new HookedToolCallback(cb, registry, hookContext))
			.toArray(ToolCallback[]::new);
	}

}
