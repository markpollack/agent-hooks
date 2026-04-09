package io.github.markpollack.hooks.spring.callback;

import java.time.Duration;
import java.time.Instant;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.decision.ToolCallRecord;
import io.github.markpollack.hooks.event.AgentHookEvent;
import io.github.markpollack.hooks.event.HookInput;
import io.github.markpollack.hooks.registry.AgentHookRegistry;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps a {@link ToolCallback} with hook dispatch. Fires BEFORE_TOOL_CALL before
 * execution and AFTER_TOOL_CALL after execution.
 */
public class HookedToolCallback implements ToolCallback {

	private final ToolCallback delegate;

	private final AgentHookRegistry registry;

	private final HookContext hookContext;

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
	public String call(String toolInput, ToolContext toolContext) {
		String toolName = getToolDefinition().name();
		String effectiveInput = toolInput;

		// BEFORE_TOOL_CALL dispatch
		HookInput.BeforeToolCall beforeInput = new HookInput.BeforeToolCall(toolName, toolInput, hookContext);
		HookDecision beforeDecision = registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL, beforeInput);

		if (beforeDecision instanceof HookDecision.Block block) {
			hookContext.recordToolCall(
					new ToolCallRecord(toolName, toolInput, block.reason(), Duration.ZERO, beforeDecision, Instant.now()));
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

		// AFTER_TOOL_CALL dispatch
		HookInput.AfterToolCall afterInput = new HookInput.AfterToolCall(toolName, effectiveInput, result, duration,
				exception, hookContext);
		registry.dispatch(AgentHookEvent.AFTER_TOOL_CALL, afterInput);

		// Record in history
		hookContext.recordToolCall(
				new ToolCallRecord(toolName, effectiveInput, result, duration, beforeDecision, Instant.now()));

		if (exception != null) {
			throw (exception instanceof RuntimeException re) ? re : new RuntimeException(exception);
		}

		return result;
	}

}
