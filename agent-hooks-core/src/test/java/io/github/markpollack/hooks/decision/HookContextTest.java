package io.github.markpollack.hooks.decision;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HookContextTest {

	@Test
	void getShouldReturnEmptyForMissingKey() {
		HookContext ctx = new HookContext();
		assertThat(ctx.get("missing", String.class)).isEmpty();
	}

	@Test
	void putAndGetShouldRoundTrip() {
		HookContext ctx = new HookContext();
		ctx.put("budget", 100);
		assertThat(ctx.get("budget", Integer.class)).hasValue(100);
	}

	@Test
	void getShouldReturnEmptyForTypeMismatch() {
		HookContext ctx = new HookContext();
		ctx.put("budget", 100);
		assertThat(ctx.get("budget", String.class)).isEmpty();
	}

	@Test
	void historyShouldReturnUnmodifiableView() {
		HookContext ctx = new HookContext();
		List<ToolCallRecord> history = ctx.history();
		assertThat(history).isEmpty();
		assertThatThrownBy(() -> history.add(new ToolCallRecord("tool", "{}", "result", Duration.ofMillis(10),
				HookDecision.proceed(), Instant.now())))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void recordToolCallShouldAppendToHistory() {
		HookContext ctx = new HookContext();
		ToolCallRecord record = new ToolCallRecord("searchRestaurants", "{\"cuisine\":\"Italian\"}", "found 3",
				Duration.ofMillis(50), HookDecision.proceed(), Instant.now());
		ctx.recordToolCall(record);

		assertThat(ctx.history()).hasSize(1);
		assertThat(ctx.history().get(0).toolName()).isEqualTo("searchRestaurants");
	}

	@Test
	void recordToolCallShouldPreserveOrder() {
		HookContext ctx = new HookContext();
		ctx.recordToolCall(new ToolCallRecord("first", "{}", "r1", Duration.ofMillis(1), HookDecision.proceed(),
				Instant.now()));
		ctx.recordToolCall(new ToolCallRecord("second", "{}", "r2", Duration.ofMillis(2), HookDecision.proceed(),
				Instant.now()));

		assertThat(ctx.history()).extracting(ToolCallRecord::toolName).containsExactly("first", "second");
	}

}
