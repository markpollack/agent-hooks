package io.github.markpollack.hooks.registry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.decision.HookDecision;
import io.github.markpollack.hooks.event.AfterToolCall;
import io.github.markpollack.hooks.event.BeforeToolCall;
import io.github.markpollack.hooks.event.SessionStart;
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
		HookDecision decision = registry.dispatch(new BeforeToolCall("searchRestaurants", "{}", hookContext));
		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void dispatchShouldInvokeHooksInPriorityOrder() {
		List<String> order = new ArrayList<>();

		registry.on(BeforeToolCall.class, 200, event -> {
			order.add("second");
			return HookDecision.proceed();
		});
		registry.on(BeforeToolCall.class, 50, event -> {
			order.add("first");
			return HookDecision.proceed();
		});
		registry.on(BeforeToolCall.class, 300, event -> {
			order.add("third");
			return HookDecision.proceed();
		});

		registry.dispatch(new BeforeToolCall("tool", "{}", hookContext));

		assertThat(order).containsExactly("first", "second", "third");
	}

	@Test
	void blockShouldShortCircuitRemainingHooks() {
		List<String> invoked = new ArrayList<>();

		registry.on(BeforeToolCall.class, 10, event -> {
			invoked.add("first");
			return HookDecision.block("blocked");
		});
		registry.on(BeforeToolCall.class, 20, event -> {
			invoked.add("second");
			return HookDecision.proceed();
		});

		HookDecision decision = registry.dispatch(new BeforeToolCall("tool", "{}", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Block.class);
		assertThat(((HookDecision.Block) decision).reason()).isEqualTo("blocked");
		assertThat(invoked).containsExactly("first");
	}

	@Test
	void modifyShouldChainToSubsequentHooks() {
		registry.on(BeforeToolCall.class, 10, event -> HookDecision.modify("{\"modified\": true}"));
		registry.on(BeforeToolCall.class, 20, event -> {
			assertThat(event.toolInput()).isEqualTo("{\"modified\": true}");
			return HookDecision.proceed();
		});

		HookDecision decision = registry
			.dispatch(new BeforeToolCall("tool", "{\"original\": true}", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void toolPatternShouldMatchOnlyMatchingTools() {
		List<String> invoked = new ArrayList<>();

		registry.onTool("search.*", BeforeToolCall.class, event -> {
			invoked.add("search-hook");
			return HookDecision.proceed();
		});

		registry.dispatch(new BeforeToolCall("searchRestaurants", "{}", hookContext));
		registry.dispatch(new BeforeToolCall("bookTable", "{}", hookContext));

		assertThat(invoked).containsExactly("search-hook");
	}

	@Test
	void exceptionInHookShouldBeTreatedAsProceed() {
		registry.on(BeforeToolCall.class, 10, event -> {
			throw new RuntimeException("hook failure");
		});

		HookDecision decision = registry.dispatch(new BeforeToolCall("tool", "{}", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void retryShouldThrowOnBeforeToolCall() {
		registry.on(BeforeToolCall.class, event -> HookDecision.retry("retry"));

		assertThatThrownBy(() -> registry.dispatch(new BeforeToolCall("tool", "{}", hookContext)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Retry is only valid for AfterToolCall");
	}

	@Test
	void retryShouldBeValidForAfterToolCall() {
		registry.on(AfterToolCall.class, event -> HookDecision.retry("try again"));

		HookDecision decision = registry
			.dispatch(new AfterToolCall("tool", "{}", "result", Duration.ofMillis(10), null, hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Retry.class);
		assertThat(((HookDecision.Retry) decision).reason()).isEqualTo("try again");
	}

	@Test
	void providerRegistrationShouldDelegateToProvider() {
		List<String> invoked = new ArrayList<>();

		registry.register(reg -> {
			reg.on(SessionStart.class, event -> {
				invoked.add("session-hook");
				return HookDecision.proceed();
			});
		});

		registry.dispatch(new SessionStart("session-1", hookContext));

		assertThat(invoked).containsExactly("session-hook");
	}

	// --- New v0.2 tests ---

	@Test
	void afterToolCallShouldDispatchInReversePriorityOrder() {
		List<String> order = new ArrayList<>();

		registry.on(AfterToolCall.class, 50, event -> {
			order.add("priority-50");
			return HookDecision.proceed();
		});
		registry.on(AfterToolCall.class, 200, event -> {
			order.add("priority-200");
			return HookDecision.proceed();
		});
		registry.on(AfterToolCall.class, 100, event -> {
			order.add("priority-100");
			return HookDecision.proceed();
		});

		registry.dispatch(new AfterToolCall("tool", "{}", "result", Duration.ofMillis(10), null, hookContext));

		assertThat(order).containsExactly("priority-200", "priority-100", "priority-50");
	}

	@Test
	void nonToolEventShouldTreatBlockAsProceed() {
		registry.on(SessionStart.class, event -> HookDecision.block("should be ignored"));

		HookDecision decision = registry.dispatch(new SessionStart("session-1", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void nonToolEventShouldTreatModifyAsProceed() {
		registry.on(SessionStart.class, event -> HookDecision.modify("modified"));

		HookDecision decision = registry.dispatch(new SessionStart("session-1", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void nonToolEventShouldTreatRetryAsProceed() {
		registry.on(SessionStart.class, event -> HookDecision.retry("retry"));

		HookDecision decision = registry.dispatch(new SessionStart("session-1", hookContext));

		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void toolPatternShouldWorkWithAfterToolCall() {
		List<String> invoked = new ArrayList<>();

		registry.onTool("book.*", AfterToolCall.class, event -> {
			invoked.add("book-after-hook");
			return HookDecision.proceed();
		});

		registry.dispatch(new AfterToolCall("bookTable", "{}", "ok", Duration.ofMillis(10), null, hookContext));
		registry.dispatch(
				new AfterToolCall("searchRestaurants", "{}", "found", Duration.ofMillis(10), null, hookContext));

		assertThat(invoked).containsExactly("book-after-hook");
	}

}
