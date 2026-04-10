package io.github.markpollack.hooks.gemini.dispatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.event.SessionEnd;
import io.github.markpollack.hooks.event.SessionStart;
import io.github.markpollack.hooks.gemini.event.GeminiAfterAgent;
import io.github.markpollack.hooks.gemini.event.GeminiAfterModel;
import io.github.markpollack.hooks.gemini.event.GeminiBeforeAgent;
import io.github.markpollack.hooks.gemini.event.GeminiBeforeModel;
import io.github.markpollack.hooks.gemini.event.GeminiBeforeToolSelection;
import io.github.markpollack.hooks.gemini.event.GeminiNotification;
import io.github.markpollack.hooks.gemini.event.GeminiPreCompress;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import io.github.markpollack.hooks.spi.AgentHookProvider;
import org.jspecify.annotations.Nullable;

/**
 * Stateless stdin/stdout dispatcher for the Gemini CLI hook protocol.
 *
 * <p>Gemini CLI spawns the hook process as a child subprocess per event:
 * JSON on stdin, JSON response on stdout, then the process exits. This means
 * {@link HookContext} is fresh per invocation — stateless hooks (security gates,
 * audit logging) work; stateful hooks (budget tracking, tool history) silently
 * degrade.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * public class MyGeminiHooks {
 *     public static void main(String[] args) throws Exception {
 *         GeminiHookDispatcher.create(new SecurityHooks(), new LoggingHooks())
 *             .run();
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Protocol:</strong> All logging goes to stderr (stdout is reserved
 * for the JSON response). Malformed stdin JSON produces {@code {}} on stdout
 * and an error on stderr — never blocks the agent due to hook parse failure.
 */
public class GeminiHookDispatcher {

	private final AgentHookRegistry registry;

	private final ObjectMapper objectMapper;

	private final GeminiDecisionMapper decisionMapper;

	private GeminiHookDispatcher(AgentHookRegistry registry, ObjectMapper objectMapper) {
		this.registry = registry;
		this.objectMapper = objectMapper;
		this.decisionMapper = new GeminiDecisionMapper(objectMapper);
	}

	/**
	 * Create a dispatcher with the given hook providers.
	 * @param providers the hook providers to register
	 * @return a new dispatcher ready to run
	 */
	public static GeminiHookDispatcher create(AgentHookProvider... providers) {
		AgentHookRegistry registry = new AgentHookRegistry();
		for (AgentHookProvider provider : providers) {
			registry.register(provider);
		}
		return new GeminiHookDispatcher(registry, new ObjectMapper());
	}

	/**
	 * Read JSON from stdin, dispatch the hook event, write JSON to stdout, and return.
	 * This is the production entry point — call from {@code main()}.
	 */
	public void run() {
		run(System.in, System.out, System.err);
	}

	/**
	 * Testable entry point with injectable streams.
	 */
	void run(InputStream in, PrintStream out, PrintStream err) {
		String inputJson;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			inputJson = reader.lines().collect(Collectors.joining("\n"));
		}
		catch (IOException e) {
			err.println("agent-hooks-gemini: failed to read stdin: " + e.getMessage());
			out.println("{}");
			return;
		}
		String outputJson = dispatch(inputJson, err);
		out.println(outputJson);
	}

	/**
	 * Testable core: JSON string in, JSON string out.
	 * @param inputJson the Gemini hook event as JSON
	 * @return the JSON response for Gemini CLI
	 */
	public String dispatch(String inputJson) {
		return dispatch(inputJson, System.err);
	}

	private String dispatch(String inputJson, PrintStream err) {
		JsonNode root;
		try {
			root = objectMapper.readTree(inputJson);
		}
		catch (JsonProcessingException e) {
			err.println("agent-hooks-gemini: malformed JSON input: " + e.getMessage());
			return "{}";
		}

		String eventName = textOrNull(root, "hook_event_name");
		if (eventName == null) {
			err.println("agent-hooks-gemini: missing hook_event_name field");
			return "{}";
		}

		String sessionId = textOrDefault(root, "session_id", "");
		HookContext context = new HookContext();

		return switch (eventName) {
			case "BeforeTool" -> dispatchBeforeTool(root, sessionId, context);
			case "AfterTool" -> dispatchAfterTool(root, sessionId, context);
			case "SessionStart" -> dispatchSessionStart(root, sessionId, context);
			case "SessionEnd" -> dispatchSessionEnd(root, sessionId, context);
			case "BeforeAgent" -> dispatchBeforeAgent(root, sessionId, context);
			case "AfterAgent" -> dispatchAfterAgent(root, sessionId, context);
			case "BeforeModel" -> dispatchBeforeModel(root, sessionId, context);
			case "AfterModel" -> dispatchAfterModel(root, sessionId, context);
			case "BeforeToolSelection" -> dispatchBeforeToolSelection(root, sessionId, context);
			case "Notification" -> dispatchNotification(root, sessionId, context);
			case "PreCompress" -> dispatchPreCompress(root, sessionId, context);
			default -> {
				err.println("agent-hooks-gemini: unknown event: " + eventName);
				yield "{}";
			}
		};
	}

	private String dispatchBeforeTool(JsonNode root, String sessionId, HookContext context) {
		String toolName = textOrDefault(root, "tool_name", "");
		String toolInput = nodeToJsonString(root.get("tool_input"));
		HookDecision decision = registry.dispatch(new BeforeToolCall(toolName, toolInput, context));
		return decisionMapper.toBeforeToolOutput(decision);
	}

	private String dispatchAfterTool(JsonNode root, String sessionId, HookContext context) {
		String toolName = textOrDefault(root, "tool_name", "");
		String toolInput = nodeToJsonString(root.get("tool_input"));
		String toolResult = nodeToJsonString(root.get("tool_response"));
		registry.dispatch(new AfterToolCall(toolName, toolInput, toolResult, Duration.ZERO, null, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchSessionStart(JsonNode root, String sessionId, HookContext context) {
		registry.dispatch(new SessionStart(sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchSessionEnd(JsonNode root, String sessionId, HookContext context) {
		registry.dispatch(new SessionEnd(sessionId, context, Duration.ZERO));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchBeforeAgent(JsonNode root, String sessionId, HookContext context) {
		String prompt = textOrDefault(root, "prompt", "");
		registry.dispatch(new GeminiBeforeAgent(prompt, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchAfterAgent(JsonNode root, String sessionId, HookContext context) {
		String prompt = textOrDefault(root, "prompt", "");
		String promptResponse = textOrDefault(root, "prompt_response", "");
		boolean stopHookActive = root.path("stop_hook_active").asBoolean(false);
		registry.dispatch(new GeminiAfterAgent(prompt, promptResponse, stopHookActive, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchBeforeModel(JsonNode root, String sessionId, HookContext context) {
		String llmRequestJson = nodeToJsonString(root.get("llm_request"));
		registry.dispatch(new GeminiBeforeModel(llmRequestJson, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchAfterModel(JsonNode root, String sessionId, HookContext context) {
		String llmRequestJson = nodeToJsonString(root.get("llm_request"));
		String llmResponseJson = nodeToJsonString(root.get("llm_response"));
		registry.dispatch(new GeminiAfterModel(llmRequestJson, llmResponseJson, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchBeforeToolSelection(JsonNode root, String sessionId, HookContext context) {
		String llmRequestJson = nodeToJsonString(root.get("llm_request"));
		registry.dispatch(new GeminiBeforeToolSelection(llmRequestJson, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchNotification(JsonNode root, String sessionId, HookContext context) {
		String notificationType = textOrDefault(root, "notification_type", "");
		String message = textOrDefault(root, "message", "");
		@Nullable
		String detailsJson = root.has("details") ? nodeToJsonString(root.get("details")) : null;
		registry.dispatch(new GeminiNotification(notificationType, message, detailsJson, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	private String dispatchPreCompress(JsonNode root, String sessionId, HookContext context) {
		String trigger = textOrDefault(root, "trigger", "");
		registry.dispatch(new GeminiPreCompress(trigger, sessionId, context));
		return decisionMapper.toObservationOutput();
	}

	@Nullable
	private static String textOrNull(JsonNode node, String field) {
		JsonNode child = node.get(field);
		return (child != null && child.isTextual()) ? child.asText() : null;
	}

	private static String textOrDefault(JsonNode node, String field, String defaultValue) {
		JsonNode child = node.get(field);
		return (child != null && child.isTextual()) ? child.asText() : defaultValue;
	}

	private String nodeToJsonString(@Nullable JsonNode node) {
		if (node == null || node.isNull()) {
			return "{}";
		}
		if (node.isTextual()) {
			return node.asText();
		}
		return node.toString();
	}

}
