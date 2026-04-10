package io.github.markpollack.hooks.gemini.dispatcher;

import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.markpollack.hooks.decision.HookDecision;

/**
 * Maps {@link HookDecision} to Gemini CLI JSON output. Package-private — used only by
 * {@link GeminiHookDispatcher}.
 *
 * <p>Unlike Claude, Gemini CLI BeforeTool <strong>cannot modify tool inputs</strong> — only
 * allow or block. {@link HookDecision.Modify} and {@link HookDecision.Retry} are mapped
 * to a warning on stderr and an allow decision.
 */
final class GeminiDecisionMapper {

	private static final Logger LOG = Logger.getLogger(GeminiDecisionMapper.class.getName());

	private final ObjectMapper objectMapper;

	GeminiDecisionMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Map a decision from a BeforeTool hook to Gemini JSON output.
	 * @param decision the hook decision
	 * @return JSON string for stdout
	 */
	String toBeforeToolOutput(HookDecision decision) {
		ObjectNode node = objectMapper.createObjectNode();
		if (decision instanceof HookDecision.Block block) {
			node.put("decision", "block");
			node.put("reason", block.reason());
		}
		else if (decision instanceof HookDecision.Modify modify) {
			LOG.warning("Modify decision not supported by Gemini CLI, treating as allow: " + modify.modifiedInput());
			node.put("decision", "allow");
		}
		else if (decision instanceof HookDecision.Retry retry) {
			LOG.warning("Retry decision not supported by Gemini CLI, treating as allow: " + retry.reason());
			node.put("decision", "allow");
		}
		else {
			// Proceed
			node.put("decision", "allow");
		}
		return node.toString();
	}

	/**
	 * Output for observation-only events — always an empty JSON object.
	 * @return {@code "{}"}
	 */
	String toObservationOutput() {
		return "{}";
	}

}
