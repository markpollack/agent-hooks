package io.github.markpollack.hooks.gemini.dispatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.event.SessionEnd;
import io.github.markpollack.hooks.event.SessionStart;
import io.github.markpollack.hooks.gemini.event.GeminiAfterAgent;
import io.github.markpollack.hooks.gemini.event.GeminiBeforeAgent;
import io.github.markpollack.hooks.gemini.event.GeminiNotification;
import io.github.markpollack.hooks.gemini.event.GeminiPreCompress;
import io.github.markpollack.hooks.spi.AgentHookProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiHookDispatcherTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// --- BeforeTool tests ---

	@Test
	void beforeToolProceedShouldReturnAllow() throws Exception {
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create();

		String output = dispatcher.dispatch(beforeToolJson("Bash", "{\"command\":\"ls\"}"));

		JsonNode result = objectMapper.readTree(output);
		assertThat(result.get("decision").asText()).isEqualTo("allow");
	}

	@Test
	void beforeToolBlockShouldReturnBlockWithReason() throws Exception {
		AgentHookProvider bashBlocker = registry -> registry.on(BeforeToolCall.class, event -> {
			if ("Bash".equals(event.toolName())) {
				return HookDecision.block("Bash is not allowed");
			}
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(bashBlocker);

		String output = dispatcher.dispatch(beforeToolJson("Bash", "{\"command\":\"rm -rf /\"}"));

		JsonNode result = objectMapper.readTree(output);
		assertThat(result.get("decision").asText()).isEqualTo("block");
		assertThat(result.get("reason").asText()).isEqualTo("Bash is not allowed");
	}

	@Test
	void beforeToolModifyShouldWarnAndAllow() throws Exception {
		AgentHookProvider modifier = registry -> registry.on(BeforeToolCall.class,
				event -> HookDecision.modify("{\"command\":\"ls -la\"}"));
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(modifier);

		String output = dispatcher.dispatch(beforeToolJson("Bash", "{\"command\":\"ls\"}"));

		JsonNode result = objectMapper.readTree(output);
		assertThat(result.get("decision").asText()).isEqualTo("allow");
		// Modify is downgraded to allow (with warning logged)
	}

	@Test
	void beforeToolAllowShouldNotBlockNonMatchingTool() throws Exception {
		AgentHookProvider bashBlocker = registry -> registry.on(BeforeToolCall.class, event -> {
			if ("Bash".equals(event.toolName())) {
				return HookDecision.block("Bash is not allowed");
			}
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(bashBlocker);

		String output = dispatcher.dispatch(beforeToolJson("Read", "{\"path\":\"/tmp/file\"}"));

		JsonNode result = objectMapper.readTree(output);
		assertThat(result.get("decision").asText()).isEqualTo("allow");
	}

	// --- AfterTool test ---

	@Test
	void afterToolShouldDispatchAndReturnEmptyJson() throws Exception {
		final String[] capturedToolName = { null };
		final Duration[] capturedDuration = { null };

		AgentHookProvider observer = registry -> registry.on(AfterToolCall.class, event -> {
			capturedToolName[0] = event.toolName();
			capturedDuration[0] = event.duration();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"AfterTool","session_id":"s1","tool_name":"Bash",\
				"tool_input":{"command":"ls"},"tool_response":{"output":"file.txt"},\
				"cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedToolName[0]).isEqualTo("Bash");
		assertThat(capturedDuration[0]).isEqualTo(Duration.ZERO);
	}

	// --- Session events ---

	@Test
	void sessionStartShouldDispatchCoreEvent() throws Exception {
		final String[] capturedSessionId = { null };

		AgentHookProvider observer = registry -> registry.on(SessionStart.class, event -> {
			capturedSessionId[0] = event.sessionId();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"SessionStart","session_id":"gemini-s1",\
				"source":"startup","cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedSessionId[0]).isEqualTo("gemini-s1");
	}

	@Test
	void sessionEndShouldDispatchCoreEvent() throws Exception {
		final String[] capturedSessionId = { null };

		AgentHookProvider observer = registry -> registry.on(SessionEnd.class, event -> {
			capturedSessionId[0] = event.sessionId();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"SessionEnd","session_id":"gemini-s1",\
				"reason":"exit","cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedSessionId[0]).isEqualTo("gemini-s1");
	}

	// --- Gemini-specific events ---

	@Test
	void beforeAgentShouldDispatchGeminiEvent() throws Exception {
		final String[] capturedPrompt = { null };

		AgentHookProvider observer = registry -> registry.on(GeminiBeforeAgent.class, event -> {
			capturedPrompt[0] = event.prompt();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"BeforeAgent","session_id":"s1",\
				"prompt":"hello world","cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedPrompt[0]).isEqualTo("hello world");
	}

	@Test
	void afterAgentShouldDispatchGeminiEvent() throws Exception {
		final boolean[] capturedStopHookActive = { false };

		AgentHookProvider observer = registry -> registry.on(GeminiAfterAgent.class, event -> {
			capturedStopHookActive[0] = event.stopHookActive();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"AfterAgent","session_id":"s1",\
				"prompt":"hello","prompt_response":"hi there","stop_hook_active":true,\
				"cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedStopHookActive[0]).isTrue();
	}

	@Test
	void notificationShouldDispatchGeminiEvent() throws Exception {
		final String[] capturedType = { null };
		final String[] capturedMessage = { null };

		AgentHookProvider observer = registry -> registry.on(GeminiNotification.class, event -> {
			capturedType[0] = event.notificationType();
			capturedMessage[0] = event.message();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"Notification","session_id":"s1",\
				"notification_type":"info","message":"update available",\
				"details":{"version":"2.0"},\
				"cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedType[0]).isEqualTo("info");
		assertThat(capturedMessage[0]).isEqualTo("update available");
	}

	@Test
	void preCompressShouldDispatchGeminiEvent() throws Exception {
		final String[] capturedTrigger = { null };

		AgentHookProvider observer = registry -> registry.on(GeminiPreCompress.class, event -> {
			capturedTrigger[0] = event.trigger();
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(observer);

		String input = """
				{"hook_event_name":"PreCompress","session_id":"s1",\
				"trigger":"auto","cwd":"/tmp","transcript_path":"","timestamp":""}""";

		String output = dispatcher.dispatch(input);

		assertThat(output).isEqualTo("{}");
		assertThat(capturedTrigger[0]).isEqualTo("auto");
	}

	// --- Error handling ---

	@Test
	void unknownEventShouldReturnEmptyJson() {
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create();

		String output = dispatcher.dispatch("""
				{"hook_event_name":"UnknownEvent","session_id":"s1"}""");

		assertThat(output).isEqualTo("{}");
	}

	@Test
	void malformedJsonShouldReturnEmptyJson() {
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create();

		String output = dispatcher.dispatch("not json at all {{{");

		assertThat(output).isEqualTo("{}");
	}

	@Test
	void missingEventNameShouldReturnEmptyJson() {
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create();

		String output = dispatcher.dispatch("""
				{"session_id":"s1","tool_name":"Bash"}""");

		assertThat(output).isEqualTo("{}");
	}

	// --- Integration: run() with streams ---

	@Test
	void runShouldReadStdinAndWriteStdout() throws Exception {
		AgentHookProvider bashBlocker = registry -> registry.on(BeforeToolCall.class, event -> {
			if ("Bash".equals(event.toolName())) {
				return HookDecision.block("blocked");
			}
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(bashBlocker);

		String inputJson = beforeToolJson("Bash", "{\"command\":\"rm -rf /\"}");
		ByteArrayInputStream stdin = new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();

		dispatcher.run(stdin, new PrintStream(stdout), new PrintStream(stderr));

		JsonNode result = objectMapper.readTree(stdout.toString(StandardCharsets.UTF_8).trim());
		assertThat(result.get("decision").asText()).isEqualTo("block");
		assertThat(result.get("reason").asText()).isEqualTo("blocked");
	}

	// --- Provider registration ---

	@Test
	void providerRegistrationShouldBlockViaSameProvider() throws Exception {
		AgentHookProvider bashBlocker = registry -> registry.on(BeforeToolCall.class, event -> {
			if ("Bash".equals(event.toolName())) {
				return HookDecision.block("Bash is not allowed");
			}
			return HookDecision.proceed();
		});
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(bashBlocker);

		// Block
		String blockOutput = dispatcher.dispatch(beforeToolJson("Bash", "{\"command\":\"rm\"}"));
		JsonNode blockResult = objectMapper.readTree(blockOutput);
		assertThat(blockResult.get("decision").asText()).isEqualTo("block");

		// Allow
		String allowOutput = dispatcher.dispatch(beforeToolJson("Read", "{\"path\":\"/tmp\"}"));
		JsonNode allowResult = objectMapper.readTree(allowOutput);
		assertThat(allowResult.get("decision").asText()).isEqualTo("allow");
	}

	// --- helpers ---

	private String beforeToolJson(String toolName, String toolInput) {
		return String.format(
				"""
				{"hook_event_name":"BeforeTool","session_id":"s1","tool_name":"%s",\
				"tool_input":%s,"cwd":"/tmp","transcript_path":"","timestamp":""}""",
				toolName, toolInput);
	}

}
