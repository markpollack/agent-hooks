package io.github.markpollack.hooks.claude.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeEventTest {

	private final HookContext context = new HookContext();

	@Test
	void userPromptSubmitShouldImplementHookEvent() {
		UserPromptSubmit event = new UserPromptSubmit("hello", "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.prompt()).isEqualTo("hello");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void agentStopShouldImplementHookEvent() {
		AgentStop event = new AgentStop("session-1", true, context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.stopHookActive()).isTrue();
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void subagentStopShouldImplementHookEvent() {
		SubagentStop event = new SubagentStop("session-2", false, context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.sessionId()).isEqualTo("session-2");
		assertThat(event.stopHookActive()).isFalse();
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void preCompactShouldImplementHookEvent() {
		PreCompact event = new PreCompact("session-1", "auto", "custom instructions", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.trigger()).isEqualTo("auto");
		assertThat(event.customInstructions()).isEqualTo("custom instructions");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void preCompactShouldAllowNullTriggerAndInstructions() {
		PreCompact event = new PreCompact("session-1", null, null, context);

		assertThat(event.trigger()).isNull();
		assertThat(event.customInstructions()).isNull();
	}

	@Test
	void claudeEventsShouldNotImplementToolEvent() {
		assertThat(new UserPromptSubmit("x", "s", context))
			.isNotInstanceOf(io.github.markpollack.hooks.event.ToolEvent.class);
		assertThat(new AgentStop("s", true, context))
			.isNotInstanceOf(io.github.markpollack.hooks.event.ToolEvent.class);
		assertThat(new SubagentStop("s", false, context))
			.isNotInstanceOf(io.github.markpollack.hooks.event.ToolEvent.class);
		assertThat(new PreCompact("s", null, null, context))
			.isNotInstanceOf(io.github.markpollack.hooks.event.ToolEvent.class);
	}

}
