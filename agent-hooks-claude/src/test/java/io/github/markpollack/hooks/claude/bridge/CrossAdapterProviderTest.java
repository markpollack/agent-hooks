package io.github.markpollack.hooks.claude.bridge;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.gemini.dispatcher.GeminiHookDispatcher;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import io.github.markpollack.hooks.spi.AgentHookProvider;
import io.github.markpollack.hooks.spring.callback.HookedToolCallback;
import org.junit.jupiter.api.Test;
import org.springaicommunity.claude.agent.sdk.hooks.HookRegistry;
import org.springaicommunity.claude.agent.sdk.types.control.HookInput;
import org.springaicommunity.claude.agent.sdk.types.control.HookOutput;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cross-adapter proof: one {@link AgentHookProvider} works on the Claude SDK path,
 * the Spring AI path, and the Gemini CLI path. This is the core value proposition
 * — write once, run on any adapter.
 */
class CrossAdapterProviderTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * A provider that blocks the "Bash" tool — portable across adapters.
	 */
	private final AgentHookProvider bashBlocker = registry -> registry.on(BeforeToolCall.class, event -> {
		if ("Bash".equals(event.toolName())) {
			return HookDecision.block("Bash is not allowed");
		}
		return HookDecision.proceed();
	});

	@Test
	void providerShouldBlockBashViaClaude() {
		AgentHookRegistry registry = new AgentHookRegistry();
		registry.register(bashBlocker);

		HookRegistry claudeRegistry = new HookRegistry();
		AgentHookBridge bridge = new AgentHookBridge(registry);
		bridge.registerInto(claudeRegistry);

		// Simulate a Bash tool call through Claude
		HookInput input = new HookInput.PreToolUseInput("PreToolUse", "session-1", "/tmp/t", "/tmp", null, "Bash",
				"tool-1", Map.of("command", "rm -rf /"));

		var registrations = claudeRegistry
			.getByEvent(org.springaicommunity.claude.agent.sdk.types.control.HookEvent.PRE_TOOL_USE);
		HookOutput output = registrations.get(registrations.size() - 1).callback().handle(input);

		assertThat(output.continueExecution()).isFalse();
		assertThat(output.reason()).isEqualTo("Bash is not allowed");
	}

	@Test
	void providerShouldBlockBashViaSpring() {
		AgentHookRegistry registry = new AgentHookRegistry();
		registry.register(bashBlocker);

		ToolCallback delegate = mock(ToolCallback.class);
		ToolDefinition toolDef = ToolDefinition.builder().name("Bash").description("Execute bash").inputSchema("{}").build();
		when(delegate.getToolDefinition()).thenReturn(toolDef);
		when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());

		HookContext hookContext = new HookContext();
		HookedToolCallback hooked = new HookedToolCallback(delegate, registry, hookContext);

		String result = hooked.call("{\"command\":\"rm -rf /\"}", null);

		assertThat(result).isEqualTo("Bash is not allowed");
		verify(delegate, never()).call(any(String.class), any());
	}

	@Test
	void providerShouldAllowReadViaClaude() {
		AgentHookRegistry registry = new AgentHookRegistry();
		registry.register(bashBlocker);

		HookRegistry claudeRegistry = new HookRegistry();
		AgentHookBridge bridge = new AgentHookBridge(registry);
		bridge.registerInto(claudeRegistry);

		HookInput input = new HookInput.PreToolUseInput("PreToolUse", "session-1", "/tmp/t", "/tmp", null, "Read",
				"tool-2", Map.of("path", "/tmp/file.txt"));

		var registrations = claudeRegistry
			.getByEvent(org.springaicommunity.claude.agent.sdk.types.control.HookEvent.PRE_TOOL_USE);
		HookOutput output = registrations.get(registrations.size() - 1).callback().handle(input);

		assertThat(output.continueExecution()).isTrue();
	}

	@Test
	void providerShouldAllowReadViaSpring() {
		AgentHookRegistry registry = new AgentHookRegistry();
		registry.register(bashBlocker);

		ToolCallback delegate = mock(ToolCallback.class);
		ToolDefinition toolDef = ToolDefinition.builder().name("Read").description("Read file").inputSchema("{}").build();
		when(delegate.getToolDefinition()).thenReturn(toolDef);
		when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
		when(delegate.call(any(String.class), any())).thenReturn("file contents");

		HookContext hookContext = new HookContext();
		HookedToolCallback hooked = new HookedToolCallback(delegate, registry, hookContext);

		String result = hooked.call("{\"path\":\"/tmp/file.txt\"}", null);

		assertThat(result).isEqualTo("file contents");
	}

	@Test
	void providerShouldBlockBashViaGemini() throws Exception {
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(bashBlocker);

		String input = """
				{"hook_event_name":"BeforeTool","session_id":"s1","tool_name":"Bash",\
				"tool_input":{"command":"rm -rf /"},"cwd":"/tmp","transcript_path":"","timestamp":""}""";

		JsonNode result = objectMapper.readTree(dispatcher.dispatch(input));

		assertThat(result.get("decision").asText()).isEqualTo("block");
		assertThat(result.get("reason").asText()).isEqualTo("Bash is not allowed");
	}

	@Test
	void providerShouldAllowReadViaGemini() throws Exception {
		GeminiHookDispatcher dispatcher = GeminiHookDispatcher.create(bashBlocker);

		String input = """
				{"hook_event_name":"BeforeTool","session_id":"s1","tool_name":"Read",\
				"tool_input":{"path":"/tmp/file.txt"},"cwd":"/tmp","transcript_path":"","timestamp":""}""";

		JsonNode result = objectMapper.readTree(dispatcher.dispatch(input));

		assertThat(result.get("decision").asText()).isEqualTo("allow");
	}

}
