package io.github.markpollack.hooks;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.github.markpollack.hooks", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule core_should_not_depend_on_spring = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks..")
		.and()
		.resideOutsideOfPackage("io.github.markpollack.hooks.spring..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework..")
		.because("Core API must have zero framework dependencies");

	@ArchTest
	static final ArchRule decision_types_should_not_depend_on_registry = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.decision..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.hooks.registry..")
		.because("Decision types are pure data — no dependency on registry");

	@ArchTest
	static final ArchRule event_types_should_not_depend_on_registry = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.event..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.hooks.registry..")
		.because("Event types are pure data — no dependency on registry");

	@ArchTest
	static final ArchRule spi_should_not_depend_on_context_internals = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.spi..")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("HookContext")
		.because("SPI interfaces should not depend on HookContext directly");

}
