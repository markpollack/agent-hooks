package io.github.markpollack.hooks.registry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AgentHookEvent;
import io.github.markpollack.hooks.event.HookInput;
import io.github.markpollack.hooks.spi.AgentHook;
import io.github.markpollack.hooks.spi.AgentHookProvider;

/**
 * Registry and dispatcher for agent hooks. Hooks are registered per event with optional
 * priority and tool name pattern matching.
 *
 * <p>
 * Dispatch semantics:
 * <ul>
 * <li>Hooks execute in priority order (lower priority value = invoked first)</li>
 * <li>Block short-circuits — remaining hooks are not called</li>
 * <li>Modify chains — subsequent hooks see the modified input</li>
 * <li>Retry is only valid for AFTER_TOOL_CALL</li>
 * <li>Exception in a hook is treated as Proceed</li>
 * </ul>
 */
public class AgentHookRegistry {

	private static final int DEFAULT_PRIORITY = 100;

	private final Map<AgentHookEvent, CopyOnWriteArrayList<PrioritizedHook>> hooks = new ConcurrentHashMap<>();

	public void on(AgentHookEvent event, AgentHook hook) {
		on(event, DEFAULT_PRIORITY, hook);
	}

	public void on(AgentHookEvent event, int priority, AgentHook hook) {
		hooks.computeIfAbsent(event, e -> new CopyOnWriteArrayList<>()).add(new PrioritizedHook(priority, null, hook));
	}

	public void onTool(String toolNamePattern, AgentHookEvent event, AgentHook hook) {
		onTool(toolNamePattern, event, DEFAULT_PRIORITY, hook);
	}

	public void onTool(String toolNamePattern, AgentHookEvent event, int priority, AgentHook hook) {
		Pattern pattern = Pattern.compile(toolNamePattern);
		hooks.computeIfAbsent(event, e -> new CopyOnWriteArrayList<>())
			.add(new PrioritizedHook(priority, pattern, hook));
	}

	public void register(AgentHookProvider provider) {
		provider.registerHooks(this);
	}

	public HookDecision dispatch(AgentHookEvent event, HookInput input) {
		CopyOnWriteArrayList<PrioritizedHook> eventHooks = hooks.get(event);
		if (eventHooks == null || eventHooks.isEmpty()) {
			return HookDecision.proceed();
		}

		String toolName = extractToolName(input);
		HookInput currentInput = input;
		HookDecision lastDecision = HookDecision.proceed();

		List<PrioritizedHook> sorted = eventHooks.stream()
			.sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
			.toList();

		for (PrioritizedHook ph : sorted) {
			if (ph.toolPattern() != null && (toolName == null || !ph.toolPattern().matcher(toolName).matches())) {
				continue;
			}

			HookDecision decision;
			try {
				decision = ph.hook().handle(currentInput);
			}
			catch (Exception e) {
				decision = HookDecision.proceed();
			}

			validateDecision(event, decision);

			if (decision instanceof HookDecision.Block) {
				return decision;
			}
			if (decision instanceof HookDecision.Modify modify && currentInput instanceof HookInput.BeforeToolCall btc) {
				currentInput = new HookInput.BeforeToolCall(btc.toolName(), modify.modifiedInput(), btc.hookContext());
				lastDecision = decision;
			}
			else if (decision instanceof HookDecision.Retry) {
				lastDecision = decision;
			}
			else {
				lastDecision = decision;
			}
		}

		return lastDecision;
	}

	private String extractToolName(HookInput input) {
		if (input instanceof HookInput.BeforeToolCall btc) {
			return btc.toolName();
		}
		if (input instanceof HookInput.AfterToolCall atc) {
			return atc.toolName();
		}
		return null;
	}

	private void validateDecision(AgentHookEvent event, HookDecision decision) {
		if (decision instanceof HookDecision.Block && event != AgentHookEvent.BEFORE_TOOL_CALL) {
			throw new IllegalStateException("Block is only valid for BEFORE_TOOL_CALL, got " + event);
		}
		if (decision instanceof HookDecision.Modify && event != AgentHookEvent.BEFORE_TOOL_CALL) {
			throw new IllegalStateException("Modify is only valid for BEFORE_TOOL_CALL, got " + event);
		}
		if (decision instanceof HookDecision.Retry && event != AgentHookEvent.AFTER_TOOL_CALL) {
			throw new IllegalStateException("Retry is only valid for AFTER_TOOL_CALL, got " + event);
		}
	}

	private record PrioritizedHook(int priority, Pattern toolPattern, AgentHook hook) {
	}

}
