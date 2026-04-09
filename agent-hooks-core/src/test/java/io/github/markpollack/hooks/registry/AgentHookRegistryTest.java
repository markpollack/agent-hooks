package io.github.markpollack.hooks.registry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AgentHookEvent;
import io.github.markpollack.hooks.event.HookInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentHookRegistryTest {

	private AgentHookRegistry registry;

	private HookContext hookContext;

	@BeforeEach
	void setUp() {
		registry = new AgentHookRegistry();
		hookContext = new HookContext();
	}

	@Test
	void dispatchShouldReturnProceedWhenNoHooksRegistered() {
		HookInput input = new HookInput.BeforeToolCall("searchRestaurants", "{}", hookContext);
		HookDecision decision = registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL, input);
		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void dispatchShouldInvokeHooksInPriorityOrder() {
		List<String> order = new ArrayList<>();

		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 200, input -> {
			order.add("second");
			return HookDecision.proceed();
		});
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 50, input -> {
			order.add("first");
			return HookDecision.proceed();
		});
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 300, input -> {
			order.add("third");
			return HookDecision.proceed();
		});

		registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("tool", "{}", hookContext));

		assertThat(order).containsExactly("first", "second", "third");
	}

	@Test
	void blockShouldShortCircuitRemainingHooks() {
		List<String> invoked = new ArrayList<>();

		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 10, input -> {
			invoked.add("first");
			return HookDecision.block("blocked");
		});
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 20, input -> {
			invoked.add("second");
			return HookDecision.proceed();
		});

		HookDecision decision = registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("tool", "{}", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Block.class);
		assertThat(((HookDecision.Block) decision).reason()).isEqualTo("blocked");
		assertThat(invoked).containsExactly("first");
	}

	@Test
	void modifyShouldChainToSubsequentHooks() {
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 10, input -> {
			return HookDecision.modify("{\"modified\": true}");
		});
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 20, input -> {
			HookInput.BeforeToolCall btc = (HookInput.BeforeToolCall) input;
			assertThat(btc.toolInput()).isEqualTo("{\"modified\": true}");
			return HookDecision.proceed();
		});

		HookDecision decision = registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("tool", "{\"original\": true}", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void toolPatternShouldMatchOnlyMatchingTools() {
		List<String> invoked = new ArrayList<>();

		registry.onTool("search.*", AgentHookEvent.BEFORE_TOOL_CALL, input -> {
			invoked.add("search-hook");
			return HookDecision.proceed();
		});

		registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("searchRestaurants", "{}", hookContext));
		registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("bookTable", "{}", hookContext));

		assertThat(invoked).containsExactly("search-hook");
	}

	@Test
	void exceptionInHookShouldBeTreatedAsProceed() {
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, 10, input -> {
			throw new RuntimeException("hook failure");
		});

		HookDecision decision = registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("tool", "{}", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void retryShouldThrowOnBeforeToolCall() {
		registry.on(AgentHookEvent.BEFORE_TOOL_CALL, input -> HookDecision.retry("retry"));

		assertThatThrownBy(() -> registry.dispatch(AgentHookEvent.BEFORE_TOOL_CALL,
				new HookInput.BeforeToolCall("tool", "{}", hookContext)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Retry is only valid for AFTER_TOOL_CALL");
	}

	@Test
	void blockShouldThrowOnAfterToolCall() {
		registry.on(AgentHookEvent.AFTER_TOOL_CALL, input -> HookDecision.block("no"));

		assertThatThrownBy(() -> registry.dispatch(AgentHookEvent.AFTER_TOOL_CALL,
				new HookInput.AfterToolCall("tool", "{}", "result", Duration.ofMillis(10), null, hookContext)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Block is only valid for BEFORE_TOOL_CALL");
	}

	@Test
	void providerRegistrationShouldDelegateToProvider() {
		List<String> invoked = new ArrayList<>();

		registry.register(reg -> {
			reg.on(AgentHookEvent.SESSION_START, input -> {
				invoked.add("session-hook");
				return HookDecision.proceed();
			});
		});

		registry.dispatch(AgentHookEvent.SESSION_START, new HookInput.SessionStart("session-1", hookContext));

		assertThat(invoked).containsExactly("session-hook");
	}

	@Test
	void retryShouldBeValidForAfterToolCall() {
		registry.on(AgentHookEvent.AFTER_TOOL_CALL, input -> HookDecision.retry("try again"));

		HookDecision decision = registry.dispatch(AgentHookEvent.AFTER_TOOL_CALL,
				new HookInput.AfterToolCall("tool", "{}", "result", Duration.ofMillis(10), null, hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Retry.class);
		assertThat(((HookDecision.Retry) decision).reason()).isEqualTo("try again");
	}

}
