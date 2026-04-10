package io.github.markpollack.hooks.gemini.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;
import io.github.markpollack.hooks.event.ToolEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiEventTest {

	private final HookContext context = new HookContext();

	@Test
	void beforeAgentShouldImplementHookEvent() {
		GeminiBeforeAgent event = new GeminiBeforeAgent("hello", "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.prompt()).isEqualTo("hello");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void afterAgentShouldImplementHookEvent() {
		GeminiAfterAgent event = new GeminiAfterAgent("hello", "world", true, "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.prompt()).isEqualTo("hello");
		assertThat(event.promptResponse()).isEqualTo("world");
		assertThat(event.stopHookActive()).isTrue();
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void beforeModelShouldImplementHookEvent() {
		GeminiBeforeModel event = new GeminiBeforeModel("{\"model\":\"gemini\"}", "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.llmRequestJson()).isEqualTo("{\"model\":\"gemini\"}");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void afterModelShouldImplementHookEvent() {
		GeminiAfterModel event = new GeminiAfterModel("{\"req\":1}", "{\"resp\":2}", "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.llmRequestJson()).isEqualTo("{\"req\":1}");
		assertThat(event.llmResponseJson()).isEqualTo("{\"resp\":2}");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void beforeToolSelectionShouldImplementHookEvent() {
		GeminiBeforeToolSelection event = new GeminiBeforeToolSelection("{\"tools\":[]}", "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.llmRequestJson()).isEqualTo("{\"tools\":[]}");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void notificationShouldImplementHookEvent() {
		GeminiNotification event = new GeminiNotification("info", "something happened", "{\"key\":\"val\"}",
				"session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.notificationType()).isEqualTo("info");
		assertThat(event.message()).isEqualTo("something happened");
		assertThat(event.detailsJson()).isEqualTo("{\"key\":\"val\"}");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void notificationShouldAllowNullDetails() {
		GeminiNotification event = new GeminiNotification("warning", "no details", null, "session-1", context);

		assertThat(event.detailsJson()).isNull();
	}

	@Test
	void preCompressShouldImplementHookEvent() {
		GeminiPreCompress event = new GeminiPreCompress("auto", "session-1", context);

		assertThat(event).isInstanceOf(HookEvent.class);
		assertThat(event.trigger()).isEqualTo("auto");
		assertThat(event.sessionId()).isEqualTo("session-1");
		assertThat(event.context()).isSameAs(context);
	}

	@Test
	void geminiEventsShouldNotImplementToolEvent() {
		assertThat(new GeminiBeforeAgent("x", "s", context)).isNotInstanceOf(ToolEvent.class);
		assertThat(new GeminiAfterAgent("x", "y", false, "s", context)).isNotInstanceOf(ToolEvent.class);
		assertThat(new GeminiBeforeModel("{}", "s", context)).isNotInstanceOf(ToolEvent.class);
		assertThat(new GeminiAfterModel("{}", "{}", "s", context)).isNotInstanceOf(ToolEvent.class);
		assertThat(new GeminiBeforeToolSelection("{}", "s", context)).isNotInstanceOf(ToolEvent.class);
		assertThat(new GeminiNotification("t", "m", null, "s", context)).isNotInstanceOf(ToolEvent.class);
		assertThat(new GeminiPreCompress("manual", "s", context)).isNotInstanceOf(ToolEvent.class);
	}

}
