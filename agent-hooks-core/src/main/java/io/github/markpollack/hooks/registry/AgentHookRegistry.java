package io.github.markpollack.hooks.registry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.event.HookEvent;
import io.github.markpollack.hooks.event.ToolEvent;
import io.github.markpollack.hooks.spi.AgentHook;
import io.github.markpollack.hooks.spi.AgentHookProvider;
import org.jspecify.annotations.Nullable;

/**
 * Registry and dispatcher for agent hooks. Hooks are registered per event type with
 * optional priority and tool name pattern matching.
 *
 * <p>
 * Dispatch semantics for {@link ToolEvent} types:
 * <ul>
 * <li>Hooks execute in priority order (lower priority value = invoked first)</li>
 * <li>{@link AfterToolCall} dispatches in <strong>reverse</strong> priority order (cleanup
 * ordering)</li>
 * <li>Block short-circuits — remaining hooks are not called</li>
 * <li>Modify chains — subsequent hooks see the modified input</li>
 * <li>Retry is only valid for {@link AfterToolCall}</li>
 * <li>Exception in a hook is treated as Proceed</li>
 * </ul>
 *
 * <p>
 * For non-tool events (session, custom): Block/Modify/Retry are logged as warnings and
 * treated as Proceed (observation-only).
 *
 * <p>
 * <strong>Dispatch matches the event's exact runtime class.</strong> A hook registered
 * against a supertype or interface — {@code on(ToolEvent.class, ...)},
 * {@code on(HookEvent.class, ...)} — compiles but is never invoked. Register against the
 * concrete event record you want to observe.
 *
 * <p>
 * <strong>Failure model.</strong> An exception thrown by a hook is logged at
 * {@code WARNING} and treated as Proceed; it never propagates to the agent runtime. A hook
 * that returns a decision the event cannot accept is a programming error, not a runtime
 * condition: returning {@code Retry} from a {@link BeforeToolCall} hook throws
 * {@link IllegalStateException} out of {@link #dispatch(HookEvent)}. Adapters that must not
 * fail the agent on a misconfigured hook are responsible for containing that exception.
 */
public class AgentHookRegistry {

	/**
	 * {@code java.util.logging} keeps the core module free of third-party dependencies.
	 * Its default handler writes to {@code System.err}, which the Gemini adapter relies on
	 * to keep {@code System.out} reserved for the hook protocol response.
	 */
	private static final Logger LOG = Logger.getLogger(AgentHookRegistry.class.getName());

	private static final int DEFAULT_PRIORITY = 100;

	private final Map<Class<?>, CopyOnWriteArrayList<PrioritizedHook<?>>> hooksByEventType = new ConcurrentHashMap<>();

	/**
	 * Register a hook for an event type.
	 * @param <E> the event type
	 * @param eventType the event class
	 * @param hook the hook to invoke
	 */
	public <E extends HookEvent> void on(Class<E> eventType, AgentHook<E> hook) {
		on(eventType, DEFAULT_PRIORITY, hook);
	}

	/**
	 * Register a hook for an event type with priority (lower = earlier).
	 * @param <E> the event type
	 * @param eventType the event class
	 * @param priority priority value (lower = invoked first; default 100)
	 * @param hook the hook to invoke
	 */
	public <E extends HookEvent> void on(Class<E> eventType, int priority, AgentHook<E> hook) {
		hooksByEventType.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
			.add(new PrioritizedHook<>(priority, null, hook));
	}

	/**
	 * Register a hook for tool events matching a name pattern (regex).
	 * @param <E> the tool event type
	 * @param toolNamePattern regex pattern to match tool names
	 * @param eventType the tool event class
	 * @param hook the hook to invoke
	 */
	public <E extends ToolEvent> void onTool(String toolNamePattern, Class<E> eventType, AgentHook<E> hook) {
		onTool(toolNamePattern, eventType, DEFAULT_PRIORITY, hook);
	}

	/**
	 * Register a hook for tool events matching a name pattern with priority.
	 * @param <E> the tool event type
	 * @param toolNamePattern regex pattern to match tool names
	 * @param eventType the tool event class
	 * @param priority priority value (lower = invoked first)
	 * @param hook the hook to invoke
	 */
	public <E extends ToolEvent> void onTool(String toolNamePattern, Class<E> eventType, int priority,
			AgentHook<E> hook) {
		Pattern pattern = Pattern.compile(toolNamePattern);
		hooksByEventType.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
			.add(new PrioritizedHook<>(priority, pattern, hook));
	}

	/**
	 * Register a provider that self-registers for multiple events.
	 * @param provider the provider
	 */
	public void register(AgentHookProvider provider) {
		provider.registerHooks(this);
	}

	/**
	 * Dispatch an event to all matching hooks. Returns aggregate decision.
	 *
	 * <p>
	 * For {@link ToolEvent} types, the full decision algebra applies (Block, Modify,
	 * Retry). For non-tool events, only Proceed is meaningful — other decisions are logged
	 * as warnings and treated as Proceed.
	 * @param event the event to dispatch
	 * @return the aggregate decision
	 */
	@SuppressWarnings("unchecked")
	public HookDecision dispatch(HookEvent event) {
		CopyOnWriteArrayList<PrioritizedHook<?>> eventHooks = hooksByEventType.get(event.getClass());
		if (eventHooks == null || eventHooks.isEmpty()) {
			return HookDecision.proceed();
		}

		boolean isToolEvent = event instanceof ToolEvent;
		boolean isReverseOrder = event instanceof AfterToolCall;
		String toolName = isToolEvent ? ((ToolEvent) event).toolName() : null;

		List<PrioritizedHook<?>> sorted = eventHooks.stream()
			.sorted((a, b) -> isReverseOrder ? Integer.compare(b.priority(), a.priority())
					: Integer.compare(a.priority(), b.priority()))
			.toList();

		HookEvent currentEvent = event;
		HookDecision lastDecision = HookDecision.proceed();

		for (PrioritizedHook<?> ph : sorted) {
			if (ph.toolPattern() != null && (toolName == null || !ph.toolPattern().matcher(toolName).matches())) {
				continue;
			}

			HookDecision decision;
			try {
				decision = ((AgentHook<HookEvent>) ph.hook()).handle(currentEvent);
			}
			catch (Exception e) {
				// A failing hook must not take the agent down, but it must not fail silently
				// either: a security hook that throws otherwise degrades to Proceed unnoticed.
				LOG.log(Level.WARNING, e,
						() -> "Hook " + ph.hook().getClass().getName() + " threw handling "
								+ event.getClass().getSimpleName() + "; treating as Proceed");
				decision = HookDecision.proceed();
			}

			if (!isToolEvent) {
				// Non-tool events: observation only
				if (!(decision instanceof HookDecision.Proceed)) {
					HookDecision ignored = decision;
					LOG.warning(() -> "Ignoring " + ignored.getClass().getSimpleName() + " from hook "
							+ ph.hook().getClass().getName() + ": " + event.getClass().getSimpleName()
							+ " is observation-only, treating as Proceed");
					lastDecision = HookDecision.proceed();
					continue;
				}
				lastDecision = decision;
				continue;
			}

			// Tool event: full decision algebra
			validateToolDecision(event, decision);

			if (decision instanceof HookDecision.Block) {
				return decision;
			}
			if (decision instanceof HookDecision.Modify modify && currentEvent instanceof BeforeToolCall btc) {
				currentEvent = new BeforeToolCall(btc.toolName(), modify.modifiedInput(), btc.context());
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

	private void validateToolDecision(HookEvent event, HookDecision decision) {
		if (decision instanceof HookDecision.Retry && event instanceof BeforeToolCall) {
			throw new IllegalStateException(
					"Retry is only valid for AfterToolCall, got " + event.getClass().getSimpleName());
		}
	}

	private record PrioritizedHook<E extends HookEvent>(int priority, @Nullable Pattern toolPattern,
			AgentHook<E> hook) {
	}

}
