package io.github.markpollack.hooks.spring;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.markpollack.hooks.spring.callback.HookedToolCallback;

import org.springframework.ai.tool.ToolCallback;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.github.markpollack.hooks.spring",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule config_should_not_depend_on_callback_internals = noClasses()
		.that()
		.resideInAPackage("io.github.markpollack.hooks.spring.config..")
		.should()
		.dependOnClassesThat()
		.haveSimpleName("HookedToolCallback")
		.because("Auto-configuration should not reference HookedToolCallback directly");

	@ArchTest
	static final ArchRule hooked_callback_implements_tool_callback = classes()
		.that()
		.haveSimpleName("HookedToolCallback")
		.should()
		.implement(ToolCallback.class)
		.because("HookedToolCallback must implement ToolCallback for transparent wrapping");

}
