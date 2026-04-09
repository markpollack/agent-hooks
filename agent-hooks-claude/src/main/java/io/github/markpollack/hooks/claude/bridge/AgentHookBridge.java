package io.github.markpollack.hooks.claude.bridge;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.hooks.claude.event.AgentStop;
import io.github.markpollack.hooks.claude.event.PreCompact;
import io.github.markpollack.hooks.claude.event.SubagentStop;
import io.github.markpollack.hooks.claude.event.UserPromptSubmit;
import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.decision.ToolCallRecord;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.jspecify.annotations.Nullable;
import org.springaicommunity.claude.agent.sdk.hooks.HookCallback;
import org.springaicommunity.claude.agent.sdk.hooks.HookRegistry;
import org.springaicommunity.claude.agent.sdk.types.control.HookEvent;
import org.springaicommunity.claude.agent.sdk.types.control.HookInput;
import org.springaicommunity.claude.agent.sdk.types.control.HookOutput;

/**
 * Bridges the Claude Agent SDK hook system to the agent-hooks core registry.
 * Registers callbacks into a Claude {@link HookRegistry} that dispatch events
 * through our {@link AgentHookRegistry}.
 *
 * <p>
 * All 6 Claude hook events are registered unconditionally. Dispatching to an
 * empty hook list is effectively free.
 *
 * <p>
 * <strong>Duration tracking:</strong> The bridge computes real wall-clock duration
 * for tool calls by capturing {@link Instant#now()} in the pre-hook callback
 * (keyed by {@code toolUseId}) and computing the delta in the post-hook callback.
 * Not as precise as in-process timing (includes IPC overhead), but provides
 * meaningful numbers for cost/time guards on both adapters.
 *
 * <p>
 * Note: {@code toolStartTimes} entries leak if a pre-hook fires but the post-hook
 * never does (agent crash, timeout). Each entry is ~100 bytes, bounded by session
 * lifetime.
 */
public class AgentHookBridge {

	private final AgentHookRegistry registry;

	private final ObjectMapper objectMapper;

	private final DecisionMapper decisionMapper;

	/** Per-session HookContext, keyed by Claude session ID. */
	private final ConcurrentHashMap<String, HookContext> sessions = new ConcurrentHashMap<>();

	/**
	 * Pre-hook start times for computing tool call duration, keyed by toolUseId.
	 * Entries are added in the pre-tool-use callback and removed in the post-tool-use
	 * callback.
	 */
	private final ConcurrentHashMap<String, Instant> toolStartTimes = new ConcurrentHashMap<>();

	/**
	 * Create a new bridge.
	 * @param registry the agent-hooks registry to dispatch events through
	 */
	public AgentHookBridge(AgentHookRegistry registry) {
		this(registry, new ObjectMapper());
	}

	/**
	 * Create a new bridge with a custom ObjectMapper.
	 * @param registry the agent-hooks registry to dispatch events through
	 * @param objectMapper the Jackson ObjectMapper for JSON conversions
	 */
	public AgentHookBridge(AgentHookRegistry registry, ObjectMapper objectMapper) {
		this.registry = registry;
		this.objectMapper = objectMapper;
		this.decisionMapper = new DecisionMapper(objectMapper);
	}

	/**
	 * Register all 6 hook callbacks into the Claude SDK's {@link HookRegistry}.
	 * @param claudeRegistry the Claude hook registry to register into
	 */
	public void registerInto(HookRegistry claudeRegistry) {
		claudeRegistry.registerPreToolUse(preToolUseCallback());
		claudeRegistry.registerPostToolUse(postToolUseCallback());
		claudeRegistry.registerUserPromptSubmit(userPromptSubmitCallback());
		claudeRegistry.registerStop(stopCallback());
		claudeRegistry.register(HookEvent.SUBAGENT_STOP, null, subagentStopCallback());
		claudeRegistry.register(HookEvent.PRE_COMPACT, null, preCompactCallback());
	}

	/**
	 * Get or create the {@link HookContext} for a Claude session.
	 * @param sessionId the Claude session identifier
	 * @return the session's hook context
	 */
	public HookContext contextForSession(String sessionId) {
		return sessions.computeIfAbsent(sessionId, k -> new HookContext());
	}

	private HookCallback preToolUseCallback() {
		return input -> {
			HookInput.PreToolUseInput pre = (HookInput.PreToolUseInput) input;
			HookContext ctx = contextForSession(pre.sessionId());
			String toolInputJson = mapToJson(pre.toolInput());

			toolStartTimes.put(pre.toolUseId(), Instant.now());

			HookDecision decision = registry.dispatch(new BeforeToolCall(pre.toolName(), toolInputJson, ctx));

			if (decision instanceof HookDecision.Block block) {
				toolStartTimes.remove(pre.toolUseId());
				ctx.recordToolCall(
						new ToolCallRecord(pre.toolName(), toolInputJson, block.reason(), Duration.ZERO, decision,
								Instant.now()));
			}

			return decisionMapper.toPreToolUseOutput(decision);
		};
	}

	private HookCallback postToolUseCallback() {
		return input -> {
			HookInput.PostToolUseInput post = (HookInput.PostToolUseInput) input;
			HookContext ctx = contextForSession(post.sessionId());
			String toolInputJson = mapToJson(post.toolInput());
			String toolResult = objectToString(post.toolResponse());

			Instant startTime = toolStartTimes.remove(post.toolUseId());
			Duration duration = (startTime != null) ? Duration.between(startTime, Instant.now()) : Duration.ZERO;

			HookDecision decision = registry
				.dispatch(new AfterToolCall(post.toolName(), toolInputJson, toolResult, duration, null, ctx));

			ctx.recordToolCall(
					new ToolCallRecord(post.toolName(), toolInputJson, toolResult, duration, decision, Instant.now()));

			return decisionMapper.toObservableOutput(decision);
		};
	}

	private HookCallback userPromptSubmitCallback() {
		return input -> {
			HookInput.UserPromptSubmitInput prompt = (HookInput.UserPromptSubmitInput) input;
			HookContext ctx = contextForSession(prompt.sessionId());

			HookDecision decision = registry
				.dispatch(new UserPromptSubmit(prompt.prompt(), prompt.sessionId(), ctx));

			return decisionMapper.toObservableOutput(decision);
		};
	}

	private HookCallback stopCallback() {
		return input -> {
			HookInput.StopInput stop = (HookInput.StopInput) input;
			HookContext ctx = contextForSession(stop.sessionId());

			HookDecision decision = registry
				.dispatch(new AgentStop(stop.sessionId(), stop.stopHookActive(), ctx));

			return decisionMapper.toObservableOutput(decision);
		};
	}

	private HookCallback subagentStopCallback() {
		return input -> {
			HookInput.SubagentStopInput subStop = (HookInput.SubagentStopInput) input;
			HookContext ctx = contextForSession(subStop.sessionId());

			HookDecision decision = registry
				.dispatch(new SubagentStop(subStop.sessionId(), subStop.stopHookActive(), ctx));

			return decisionMapper.toObservableOutput(decision);
		};
	}

	private HookCallback preCompactCallback() {
		return input -> {
			HookInput.PreCompactInput compact = (HookInput.PreCompactInput) input;
			HookContext ctx = contextForSession(compact.sessionId());

			HookDecision decision = registry
				.dispatch(new PreCompact(compact.sessionId(), compact.trigger(), compact.customInstructions(), ctx));

			return decisionMapper.toObservableOutput(decision);
		};
	}

	private String mapToJson(Map<String, Object> map) {
		try {
			return objectMapper.writeValueAsString(map);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to serialize tool input to JSON", e);
		}
	}

	@Nullable
	private String objectToString(@Nullable Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof String s) {
			return s;
		}
		try {
			return objectMapper.writeValueAsString(obj);
		}
		catch (JsonProcessingException e) {
			return obj.toString();
		}
	}

}
