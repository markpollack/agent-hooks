package io.github.markpollack.hooks.claude;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.github.markpollack.hooks.claude",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule claude_adapter_should_not_depend_on_spring = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.claude..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework..")
		.because("Claude adapter must not depend on Spring — it is a standalone adapter");

	@ArchTest
	static final ArchRule event_types_should_not_depend_on_bridge = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.claude.event..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.hooks.claude.bridge..")
		.because("Event types are pure data — no dependency on bridge infrastructure");

}
