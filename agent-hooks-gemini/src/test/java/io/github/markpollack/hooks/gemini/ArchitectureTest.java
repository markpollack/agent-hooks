package io.github.markpollack.hooks.gemini;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.github.markpollack.hooks.gemini",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule gemini_adapter_should_not_depend_on_spring = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.gemini..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework..")
		.because("Gemini adapter must not depend on Spring — it is a standalone adapter");

	@ArchTest
	static final ArchRule gemini_adapter_should_not_depend_on_claude = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.gemini..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.hooks.claude..")
		.because("Gemini adapter must not depend on Claude adapter");

	@ArchTest
	static final ArchRule event_types_should_not_depend_on_dispatcher = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.gemini.event..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.hooks.gemini.dispatcher..")
		.because("Event types are pure data — no dependency on dispatcher infrastructure");

}
