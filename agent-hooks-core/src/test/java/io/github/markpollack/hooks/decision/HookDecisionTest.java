package io.github.markpollack.hooks.decision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookDecisionTest {

	@Test
	void proceedFactoryMethodShouldReturnProceedInstance() {
		HookDecision decision = HookDecision.proceed();
		assertThat(decision).isInstanceOf(HookDecision.Proceed.class);
	}

	@Test
	void blockFactoryMethodShouldReturnBlockWithReason() {
		HookDecision decision = HookDecision.block("too expensive");
		assertThat(decision).isInstanceOf(HookDecision.Block.class);
		assertThat(((HookDecision.Block) decision).reason()).isEqualTo("too expensive");
	}

	@Test
	void modifyFactoryMethodShouldReturnModifyWithInput() {
		HookDecision decision = HookDecision.modify("{\"budget\": 50}");
		assertThat(decision).isInstanceOf(HookDecision.Modify.class);
		assertThat(((HookDecision.Modify) decision).modifiedInput()).isEqualTo("{\"budget\": 50}");
	}

	@Test
	void retryFactoryMethodShouldReturnRetryWithReason() {
		HookDecision decision = HookDecision.retry("timeout");
		assertThat(decision).isInstanceOf(HookDecision.Retry.class);
		assertThat(((HookDecision.Retry) decision).reason()).isEqualTo("timeout");
	}

	@Test
	void sealedInterfaceShouldHaveExactlyFourPermittedSubtypes() {
		Class<?>[] permitted = HookDecision.class.getPermittedSubclasses();
		assertThat(permitted).hasSize(4);
		assertThat(permitted).extracting(Class::getSimpleName)
			.containsExactlyInAnyOrder("Proceed", "Block", "Modify", "Retry");
	}

	@Test
	void recordEqualityShouldWorkCorrectly() {
		assertThat(HookDecision.proceed()).isEqualTo(HookDecision.proceed());
		assertThat(HookDecision.block("x")).isEqualTo(HookDecision.block("x"));
		assertThat(HookDecision.block("x")).isNotEqualTo(HookDecision.block("y"));
	}

}
