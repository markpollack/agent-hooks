package io.github.markpollack.hooks.spring.callback;

import java.time.Duration;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.decision.ToolCallRecord;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.registry.AgentHookRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HookedToolCallbackTest {

	private ToolCallback delegate;

	private AgentHookRegistry registry;

	private HookContext hookContext;

	private HookedToolCallback hooked;

	@BeforeEach
	void setUp() {
		delegate = mock(ToolCallback.class);
		ToolDefinition toolDef = ToolDefinition.builder()
			.name("searchRestaurants")
			.description("Search restaurants")
			.inputSchema("{}")
			.build();
		when(delegate.getToolDefinition()).thenReturn(toolDef);
		when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());

		registry = new AgentHookRegistry();
		hookContext = new HookContext();
		hooked = new HookedToolCallback(delegate, registry, hookContext);
	}

	@Test
	void proceedShouldPassThroughUnchanged() {
		when(delegate.call(eq("{\"cuisine\":\"Italian\"}"), any())).thenReturn("found 3 restaurants");

		String result = hooked.call("{\"cuisine\":\"Italian\"}", null);

		assertThat(result).isEqualTo("found 3 restaurants");
		verify(delegate).call(eq("{\"cuisine\":\"Italian\"}"), any());
	}

	@Test
	void blockShouldReturnReasonAndNeverCallDelegate() {
		registry.on(BeforeToolCall.class, event -> HookDecision.block("over budget"));

		String result = hooked.call("{}", null);

		assertThat(result).isEqualTo("over budget");
		verify(delegate, never()).call(any(String.class), any());
	}

	@Test
	void modifyShouldPassModifiedInputToDelegate() {
		registry.on(BeforeToolCall.class,
				event -> HookDecision.modify("{\"cuisine\":\"Spanish\"}"));
		when(delegate.call(eq("{\"cuisine\":\"Spanish\"}"), any())).thenReturn("found 5 restaurants");

		String result = hooked.call("{\"cuisine\":\"Italian\"}", null);

		assertThat(result).isEqualTo("found 5 restaurants");
		verify(delegate).call(eq("{\"cuisine\":\"Spanish\"}"), any());
	}

	@Test
	void afterToolCallShouldReceiveResultAndDuration() {
		when(delegate.call(any(String.class), any())).thenReturn("ok");
		final String[] capturedResult = { null };
		final Duration[] capturedDuration = { null };

		registry.on(AfterToolCall.class, event -> {
			capturedResult[0] = event.toolResult();
			capturedDuration[0] = event.duration();
			return HookDecision.proceed();
		});

		hooked.call("{}", null);

		assertThat(capturedResult[0]).isEqualTo("ok");
		assertThat(capturedDuration[0]).isNotNull();
		assertThat(capturedDuration[0]).isGreaterThanOrEqualTo(Duration.ZERO);
	}

	@Test
	void exceptionInToolShouldBeCapturedInAfterToolCall() {
		RuntimeException error = new RuntimeException("connection failed");
		when(delegate.call(any(String.class), any())).thenThrow(error);
		final Exception[] capturedException = { null };

		registry.on(AfterToolCall.class, event -> {
			capturedException[0] = event.exception();
			return HookDecision.proceed();
		});

		assertThatThrownBy(() -> hooked.call("{}", null)).isInstanceOf(RuntimeException.class)
			.hasMessage("connection failed");

		assertThat(capturedException[0]).isSameAs(error);
	}

	@Test
	void toolCallShouldBeRecordedInHookContextHistory() {
		when(delegate.call(any(String.class), any())).thenReturn("result");

		hooked.call("{\"q\":\"pizza\"}", null);

		assertThat(hookContext.history()).hasSize(1);
		ToolCallRecord record = hookContext.history().get(0);
		assertThat(record.toolName()).isEqualTo("searchRestaurants");
		assertThat(record.toolInput()).isEqualTo("{\"q\":\"pizza\"}");
		assertThat(record.toolResult()).isEqualTo("result");
	}

	@Test
	void blockedCallShouldAlsoBeRecordedInHistory() {
		registry.on(BeforeToolCall.class, event -> HookDecision.block("denied"));

		hooked.call("{}", null);

		assertThat(hookContext.history()).hasSize(1);
		ToolCallRecord record = hookContext.history().get(0);
		assertThat(record.decision()).isInstanceOf(HookDecision.Block.class);
		assertThat(record.toolResult()).isEqualTo("denied");
	}

	@Test
	void getToolDefinitionShouldDelegateToOriginal() {
		assertThat(hooked.getToolDefinition().name()).isEqualTo("searchRestaurants");
	}

	@Test
	void getToolMetadataShouldDelegateToOriginal() {
		assertThat(hooked.getToolMetadata()).isNotNull();
	}

}
