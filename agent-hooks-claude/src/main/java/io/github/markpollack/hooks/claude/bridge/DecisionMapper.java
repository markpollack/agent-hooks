package io.github.markpollack.hooks.claude.bridge;

import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.hooks.decision.HookDecision;
import org.springaicommunity.claude.agent.sdk.types.control.HookOutput;
import org.springaicommunity.claude.agent.sdk.types.control.HookOutput.HookSpecificOutput;

/**
 * Maps {@link HookDecision} to Claude SDK {@link HookOutput}. Package-private — used
 * only by {@link AgentHookBridge}.
 */
final class DecisionMapper {

	private static final Logger LOG = Logger.getLogger(DecisionMapper.class.getName());

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	DecisionMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Map a decision from a pre-tool-use hook to the Claude SDK output.
	 * @param decision the hook decision
	 * @return the corresponding Claude SDK output
	 */
	HookOutput toPreToolUseOutput(HookDecision decision) {
		if (decision instanceof HookDecision.Block block) {
			return HookOutput.builder()
				.continueExecution(false)
				.decision("block")
				.reason(block.reason())
				.hookSpecificOutput(HookSpecificOutput.preToolUseDeny(block.reason()))
				.build();
		}
		if (decision instanceof HookDecision.Modify modify) {
			Map<String, Object> updatedInput = parseJsonToMap(modify.modifiedInput());
			return HookOutput.builder()
				.continueExecution(true)
				.hookSpecificOutput(HookSpecificOutput.preToolUseModify(updatedInput))
				.build();
		}
		if (decision instanceof HookDecision.Retry retry) {
			LOG.warning("Retry decision has no Claude equivalent, treating as allow: " + retry.reason());
			return HookOutput.allow();
		}
		// Proceed (default)
		return HookOutput.builder()
			.continueExecution(true)
			.hookSpecificOutput(HookSpecificOutput.preToolUseAllow())
			.build();
	}

	/**
	 * Map a decision from an observable (non-steerable) hook to the Claude SDK output.
	 * Observable events always produce {@code HookOutput.allow()}.
	 * @param decision the hook decision (ignored — always allows)
	 * @return {@code HookOutput.allow()}
	 */
	HookOutput toObservableOutput(HookDecision decision) {
		return HookOutput.allow();
	}

	private Map<String, Object> parseJsonToMap(String json) {
		try {
			return objectMapper.readValue(json, MAP_TYPE);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to parse modified input as JSON: " + json, e);
		}
	}

}
