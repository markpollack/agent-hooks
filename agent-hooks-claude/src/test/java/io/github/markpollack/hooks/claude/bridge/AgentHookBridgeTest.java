package io.github.markpollack.hooks.claude.bridge;

import java.time.Duration;
import java.util.Map;

import io.github.markpollack.hooks.claude.event.AgentStop;
import io.github.markpollack.hooks.claude.event.PreCompact;
import io.github.markpollack.hooks.claude.event.SubagentStop;
import io.github.markpollack.hooks.claude.event.UserPromptSubmit;
import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.claude.agent.sdk.hooks.HookRegistry;
import org.springaicommunity.claude.agent.sdk.types.control.HookInput;
import org.springaicommunity.claude.agent.sdk.types.control.HookOutput;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHookBridgeTest {

	private AgentHookRegistry registry;

	private HookRegistry claudeRegistry;

	private AgentHookBridge bridge;

	@BeforeEach
	void setUp() {
		registry = new AgentHookRegistry();
		claudeRegistry = new HookRegistry();
		bridge = new AgentHookBridge(registry);
		bridge.registerInto(claudeRegistry);
	}

	@Test
	void proceedShouldReturnAllow() {
		// No hooks registered — default is proceed
		HookInput input = preToolUseInput("Bash", "tool-1", Map.of("command", "ls"));

		HookOutput output = executePreToolUse(input);

		assertThat(output.continueExecution()).isTrue();
	}

	@Test
	void blockShouldReturnBlockWithDeny() {
		registry.on(BeforeToolCall.class, event -> HookDecision.block("not allowed"));

		HookInput input = preToolUseInput("Bash", "tool-1", Map.of("command", "rm -rf /"));

		HookOutput output = executePreToolUse(input);

		assertThat(output.continueExecution()).isFalse();
		assertThat(output.reason()).isEqualTo("not allowed");
		assertThat(output.hookSpecificOutput()).isNotNull();
		assertThat(output.hookSpecificOutput().permissionDecision()).isEqualTo("deny");
	}

	@Test
	void modifyShouldReturnAllowWithUpdatedInput() {
		registry.on(BeforeToolCall.class,
				event -> HookDecision.modify("{\"command\":\"ls -la\"}"));

		HookInput input = preToolUseInput("Bash", "tool-1", Map.of("command", "ls"));

		HookOutput output = executePreToolUse(input);

		assertThat(output.continueExecution()).isTrue();
		assertThat(output.hookSpecificOutput()).isNotNull();
		assertThat(output.hookSpecificOutput().updatedInput()).containsEntry("command", "ls -la");
	}

	@Test
	void postToolUseShouldDispatchAfterToolCallWithDuration() {
		final Duration[] capturedDuration = { null };
		final String[] capturedResult = { null };

		registry.on(AfterToolCall.class, event -> {
			capturedDuration[0] = event.duration();
			capturedResult[0] = event.toolResult();
			return HookDecision.proceed();
		});

		// Fire pre-hook to start timing
		HookInput preInput = preToolUseInput("Read", "tool-2", Map.of("path", "/tmp/test"));
		executePreToolUse(preInput);

		// Fire post-hook
		HookInput postInput = postToolUseInput("Read", "tool-2", Map.of("path", "/tmp/test"), "file contents");
		HookOutput output = executePostToolUse(postInput);

		assertThat(output.continueExecution()).isTrue();
		assertThat(capturedDuration[0]).isNotNull();
		assertThat(capturedDuration[0]).isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(capturedResult[0]).isEqualTo("file contents");
	}

	@Test
	void postToolUseShouldRecordInHistory() {
		HookInput preInput = preToolUseInput("Bash", "tool-3", Map.of("command", "echo hi"));
		executePreToolUse(preInput);

		HookInput postInput = postToolUseInput("Bash", "tool-3", Map.of("command", "echo hi"), "hi");
		executePostToolUse(postInput);

		HookContext ctx = bridge.contextForSession("session-1");
		assertThat(ctx.history()).hasSize(1);
		assertThat(ctx.history().get(0).toolName()).isEqualTo("Bash");
	}

	@Test
	void userPromptSubmitShouldDispatchEvent() {
		final String[] capturedPrompt = { null };

		registry.on(UserPromptSubmit.class, event -> {
			capturedPrompt[0] = event.prompt();
			return HookDecision.proceed();
		});

		HookInput input = new HookInput.UserPromptSubmitInput("UserPromptSubmit", "session-1", "/tmp/transcript",
				"/tmp", null, "hello world");

		HookOutput output = executeCallback(input);

		assertThat(output.continueExecution()).isTrue();
		assertThat(capturedPrompt[0]).isEqualTo("hello world");
	}

	@Test
	void stopShouldDispatchAgentStop() {
		final boolean[] capturedStopHookActive = { false };

		registry.on(AgentStop.class, event -> {
			capturedStopHookActive[0] = event.stopHookActive();
			return HookDecision.proceed();
		});

		HookInput input = new HookInput.StopInput("Stop", "session-1", "/tmp/transcript", "/tmp", null, true);

		HookOutput output = executeCallback(input);

		assertThat(output.continueExecution()).isTrue();
		assertThat(capturedStopHookActive[0]).isTrue();
	}

	@Test
	void sessionIsolationShouldUseDifferentContexts() {
		HookInput preA = preToolUseInput("Bash", "tool-a", Map.of(), "session-A");
		executePreToolUse(preA);
		HookInput postA = postToolUseInput("Bash", "tool-a", Map.of(), "ok", "session-A");
		executePostToolUse(postA);

		HookInput preB = preToolUseInput("Read", "tool-b", Map.of(), "session-B");
		executePreToolUse(preB);
		HookInput postB = postToolUseInput("Read", "tool-b", Map.of(), "ok", "session-B");
		executePostToolUse(postB);

		HookContext ctxA = bridge.contextForSession("session-A");
		HookContext ctxB = bridge.contextForSession("session-B");

		assertThat(ctxA).isNotSameAs(ctxB);
		assertThat(ctxA.history()).hasSize(1);
		assertThat(ctxA.history().get(0).toolName()).isEqualTo("Bash");
		assertThat(ctxB.history()).hasSize(1);
		assertThat(ctxB.history().get(0).toolName()).isEqualTo("Read");
	}

	@Test
	void blockedToolShouldRecordInHistoryAndNotStartTimer() {
		registry.on(BeforeToolCall.class, event -> HookDecision.block("denied"));

		HookInput input = preToolUseInput("Bash", "tool-4", Map.of("command", "rm -rf /"));
		executePreToolUse(input);

		HookContext ctx = bridge.contextForSession("session-1");
		assertThat(ctx.history()).hasSize(1);
		assertThat(ctx.history().get(0).decision()).isInstanceOf(HookDecision.Block.class);
		assertThat(ctx.history().get(0).toolResult()).isEqualTo("denied");
	}

	@Test
	void subagentStopShouldDispatchEvent() {
		final boolean[] fired = { false };

		registry.on(SubagentStop.class, event -> {
			fired[0] = true;
			return HookDecision.proceed();
		});

		HookInput input = new HookInput.SubagentStopInput("SubagentStop", "session-1", "/tmp/transcript", "/tmp",
				null, false);

		HookOutput output = executeCallback(input);

		assertThat(output.continueExecution()).isTrue();
		assertThat(fired[0]).isTrue();
	}

	@Test
	void preCompactShouldDispatchEvent() {
		final String[] capturedTrigger = { null };

		registry.on(PreCompact.class, event -> {
			capturedTrigger[0] = event.trigger();
			return HookDecision.proceed();
		});

		HookInput input = new HookInput.PreCompactInput("PreCompact", "session-1", "/tmp/transcript", "/tmp", null,
				"auto", "keep it short");

		HookOutput output = executeCallback(input);

		assertThat(output.continueExecution()).isTrue();
		assertThat(capturedTrigger[0]).isEqualTo("auto");
	}

	// --- helpers ---

	private HookInput preToolUseInput(String toolName, String toolUseId, Map<String, Object> toolInput) {
		return preToolUseInput(toolName, toolUseId, toolInput, "session-1");
	}

	private HookInput preToolUseInput(String toolName, String toolUseId, Map<String, Object> toolInput,
			String sessionId) {
		return new HookInput.PreToolUseInput("PreToolUse", sessionId, "/tmp/transcript", "/tmp", null, toolName,
				toolUseId, toolInput);
	}

	private HookInput postToolUseInput(String toolName, String toolUseId, Map<String, Object> toolInput,
			Object toolResponse) {
		return postToolUseInput(toolName, toolUseId, toolInput, toolResponse, "session-1");
	}

	private HookInput postToolUseInput(String toolName, String toolUseId, Map<String, Object> toolInput,
			Object toolResponse, String sessionId) {
		return new HookInput.PostToolUseInput("PostToolUse", sessionId, "/tmp/transcript", "/tmp", null, toolName,
				toolUseId, toolInput, toolResponse);
	}

	private HookOutput executePreToolUse(HookInput input) {
		return executeCallback(input);
	}

	private HookOutput executePostToolUse(HookInput input) {
		return executeCallback(input);
	}

	private HookOutput executeCallback(HookInput input) {
		// Look up the registered callback and execute it directly
		var registrations = claudeRegistry
			.getByEvent(org.springaicommunity.claude.agent.sdk.types.control.HookEvent.fromProtocolName(input.hookEventName()));
		assertThat(registrations).isNotEmpty();
		return registrations.get(registrations.size() - 1).callback().handle(input);
	}

}
