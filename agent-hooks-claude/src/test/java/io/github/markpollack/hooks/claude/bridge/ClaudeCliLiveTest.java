package io.github.markpollack.hooks.claude.bridge;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.markpollack.claude.agent.sdk.ClaudeClient;
import io.github.markpollack.claude.agent.sdk.ClaudeSyncClient;
import io.github.markpollack.claude.agent.sdk.config.PermissionMode;
import io.github.markpollack.claude.agent.sdk.hooks.HookRegistry;
import io.github.markpollack.claude.agent.sdk.transport.CLIOptions;
import io.github.markpollack.claude.agent.sdk.types.AssistantMessage;
import io.github.markpollack.claude.agent.sdk.types.Message;
import io.github.markpollack.claude.agent.sdk.types.ToolResultBlock;
import io.github.markpollack.claude.agent.sdk.types.ToolUseBlock;
import io.github.markpollack.claude.agent.sdk.types.UserMessage;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live check that a PreToolUse {@link HookDecision.Block} and {@link HookDecision.Modify},
 * returned through {@link AgentHookBridge}, take effect in a real Claude CLI via the
 * Claude Agent SDK.
 *
 * <p>
 * Opt-in: skipped unless {@code -Dagent-hooks.live=true} or {@code AGENT_HOOKS_LIVE=true}
 * is set and {@code claude} is on the {@code PATH}. It calls a real model and costs a
 * little API money. Each case asks the model to run one {@code touch} command in a
 * temporary directory, and asserts on what the tool did there and on the tool result the
 * CLI reported.
 *
 * <pre>
 * AGENT_HOOKS_LIVE=true ./mvnw test -pl agent-hooks-claude -am -Dtest=ClaudeCliLiveTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * From inside a Claude Code session, launch it through {@code ~/scripts/claude-run.sh}.
 */
@Tag("live")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class ClaudeCliLiveTest {

	private static final String BLOCK_REASON = "agent-hooks live test: blocked.txt is not allowed";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	static void requireOptInAndCli() {
		assumeTrue(Boolean.getBoolean("agent-hooks.live") || "true".equalsIgnoreCase(System.getenv("AGENT_HOOKS_LIVE")),
				"live Claude CLI test: set -Dagent-hooks.live=true or AGENT_HOOKS_LIVE=true");
		assumeTrue(claudeOnPath(), "live Claude CLI test: no claude executable on PATH");
	}

	@Test
	void blockStopsTheToolCall(@TempDir Path dir) {
		AgentHookRegistry registry = new AgentHookRegistry();
		registry.onTool("Bash", BeforeToolCall.class,
				event -> event.toolInput().contains("blocked.txt") ? HookDecision.block(BLOCK_REASON)
						: HookDecision.proceed());

		List<Message> messages = run(dir, registry, "touch blocked.txt");

		ToolUseBlock bash = bashCall(messages, "blocked.txt");
		assertThat(dir.resolve("blocked.txt")).as("the blocked command must not have run").doesNotExist();
		assertThat(resultFor(messages, bash.id())).as("the CLI reported the block as the call's tool result")
			.hasValueSatisfying(r -> {
				assertThat(r.isError()).isTrue();
				assertThat(r.getContentAsString()).contains(BLOCK_REASON);
			});
	}

	@Test
	void modifyRewritesTheToolInput(@TempDir Path dir) {
		AgentHookRegistry registry = new AgentHookRegistry();
		registry.onTool("Bash", BeforeToolCall.class, event -> {
			if (!event.toolInput().contains("original.txt")) {
				return HookDecision.proceed();
			}
			try {
				ObjectNode input = (ObjectNode) objectMapper.readTree(event.toolInput());
				input.put("command", input.path("command").asText().replace("original.txt", "modified.txt"));
				return HookDecision.modify(objectMapper.writeValueAsString(input));
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});

		List<Message> messages = run(dir, registry, "touch original.txt");

		ToolUseBlock bash = bashCall(messages, "original.txt");
		assertThat(dir.resolve("modified.txt")).as("the rewritten command ran").exists();
		assertThat(dir.resolve("original.txt")).as("the original command did not run").doesNotExist();
		assertThat(resultFor(messages, bash.id())).as("the CLI reported a tool result for the rewritten call")
			.hasValueSatisfying(r -> assertThat(r.isError()).isNotEqualTo(Boolean.TRUE));
	}

	private List<Message> run(Path dir, AgentHookRegistry registry, String command) {
		HookRegistry claudeHooks = new HookRegistry();
		new AgentHookBridge(registry, objectMapper).registerInto(claudeHooks);

		CLIOptions options = CLIOptions.builder()
			.model(CLIOptions.MODEL_HAIKU)
			.permissionMode(PermissionMode.DEFAULT)
			.tools(List.of("Bash"))
			.allowedTools(List.of("Bash"))
			.settingSources(List.of("project"))
			.maxTurns(4)
			.build();
		String prompt = "Use the Bash tool to run exactly this command, once, and nothing else: " + command
				+ ". Then reply with one word: done.";

		List<Message> messages = new ArrayList<>();
		try (ClaudeSyncClient client = ClaudeClient.sync(options)
			.workingDirectory(dir)
			.timeout(Duration.ofMinutes(3))
			.hookRegistry(claudeHooks)
			.build()) {
			for (Message message : client.connectAndReceive(prompt)) {
				messages.add(message);
			}
		}
		messages.stream()
			.filter(UserMessage.class::isInstance)
			.flatMap(m -> ((UserMessage) m).getContentAsBlocks().stream())
			.filter(ToolResultBlock.class::isInstance)
			.forEach(r -> System.out.println("live " + command + " -> " + r));
		return messages;
	}

	private static ToolUseBlock bashCall(List<Message> messages, String fileName) {
		return messages.stream()
			.filter(AssistantMessage.class::isInstance)
			.flatMap(m -> ((AssistantMessage) m).content().stream())
			.filter(ToolUseBlock.class::isInstance)
			.map(ToolUseBlock.class::cast)
			.filter(b -> "Bash".equals(b.name()) && String.valueOf(b.input().get("command")).contains(fileName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("the model never asked to run Bash on " + fileName + ": " + messages));
	}

	private static Optional<ToolResultBlock> resultFor(List<Message> messages, String toolUseId) {
		return messages.stream()
			.filter(UserMessage.class::isInstance)
			.flatMap(m -> ((UserMessage) m).getContentAsBlocks().stream())
			.filter(ToolResultBlock.class::isInstance)
			.map(ToolResultBlock.class::cast)
			.filter(r -> toolUseId.equals(r.toolUseId()))
			.findFirst();
	}

	private static boolean claudeOnPath() {
		String path = System.getenv("PATH");
		if (path == null) {
			return false;
		}
		for (String entry : path.split(File.pathSeparator)) {
			if (Files.isExecutable(Path.of(entry, "claude"))) {
				return true;
			}
		}
		return false;
	}

}
