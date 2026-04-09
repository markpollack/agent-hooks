package io.github.markpollack.hooks.spring.callback;

import java.time.Duration;
import java.time.Instant;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.decision.ToolCallRecord;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps a {@link ToolCallback} with hook dispatch. Fires {@link BeforeToolCall} before
 * execution and {@link AfterToolCall} after execution.
 *
 * <p>
 * On {@link HookDecision.Block}: returns the block reason as the tool result and never
 * calls the delegate. On {@link HookDecision.Modify}: passes the modified input to the
 * delegate.
 */
public class HookedToolCallback implements ToolCallback {

	private final ToolCallback delegate;

	private final AgentHookRegistry registry;

	private final HookContext hookContext;

	/**
	 * Create a new HookedToolCallback.
	 * @param delegate the original tool callback to wrap
	 * @param registry the hook registry for dispatching events
	 * @param hookContext the session state shared across hooks
	 */
	public HookedToolCallback(ToolCallback delegate, AgentHookRegistry registry, HookContext hookContext) {
		this.delegate = delegate;
		this.registry = registry;
		this.hookContext = hookContext;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return delegate.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext toolContext) {
		String toolName = getToolDefinition().name();
		String effectiveInput = toolInput;

		// BeforeToolCall dispatch
		HookDecision beforeDecision = registry.dispatch(new BeforeToolCall(toolName, toolInput, hookContext));

		if (beforeDecision instanceof HookDecision.Block block) {
			hookContext.recordToolCall(new ToolCallRecord(toolName, toolInput, block.reason(), Duration.ZERO,
					beforeDecision, Instant.now()));
			return block.reason();
		}

		if (beforeDecision instanceof HookDecision.Modify modify) {
			effectiveInput = modify.modifiedInput();
		}

		// Execute delegate
		Instant start = Instant.now();
		String result = null;
		Exception exception = null;
		try {
			result = delegate.call(effectiveInput, toolContext);
		}
		catch (Exception e) {
			exception = e;
		}
		Duration duration = Duration.between(start, Instant.now());

		// AfterToolCall dispatch
		registry.dispatch(new AfterToolCall(toolName, effectiveInput, result, duration, exception, hookContext));

		// Record in history
		hookContext.recordToolCall(
				new ToolCallRecord(toolName, effectiveInput, result, duration, beforeDecision, Instant.now()));

		if (exception != null) {
			throw (exception instanceof RuntimeException re) ? re : new RuntimeException(exception);
		}

		return result;
	}

}
