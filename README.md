# Agent Hooks

A portable Java hook API for steering agent behavior at the tool-call boundary, for Java developers
building agents on Spring AI, the Claude Agent SDK or the Gemini CLI. Write a policy once (block a
dangerous tool, rewrite an argument, record what ran and how long it took) and run it on any
runtime that has an adapter.

**Documentation: [lab.pollack.ai/projects/agent-hooks](https://lab.pollack.ai/projects/agent-hooks)**
· **Releases: [GitHub releases](https://github.com/markpollack/agent-hooks/releases)** and
[`release-notes/`](release-notes/)

## Modules

| Artifact | Adapter for | Requires |
|---|---|---|
| `agent-hooks-core` | none, the portable API | Java 17 |
| `agent-hooks-spring` | Spring AI `ToolCallback` | Java 17 |
| `agent-hooks-gemini` | Gemini CLI stdin/stdout hook protocol | Java 17 |
| `agent-hooks-claude` | Claude Agent SDK (`claude-code-sdk`, `provided` scope) | **Java 21** |

`agent-hooks-claude` needs Java 21 because every published `claude-code-sdk` version is
Java 21 bytecode. If you are on Java 17, the other three modules are unaffected.

## Dependency

The current release is **0.8.3**, on Maven Central under `io.github.markpollack`.

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-hooks-spring</artifactId>
    <version>0.8.3</version>
</dependency>
```

Each adapter brings `agent-hooks-core` with it; depend on core alone to write portable hook
providers. `agent-hooks-claude` expects you to supply `io.github.markpollack:claude-code-sdk`
yourself. The AgentWorks BOM manages all four modules.

## Example

```java
AgentHookRegistry registry = new AgentHookRegistry();

// Block shell tools before they run.
registry.onTool("shell.*", BeforeToolCall.class,
        event -> HookDecision.block("Shell access is disabled here"));

// Observe every completed tool call.
registry.on(AfterToolCall.class, event -> {
    System.out.println(event.toolName() + " took " + event.duration().toMillis() + " ms");
    return HookDecision.proceed();
});

// Spring AI: wrap @Tool-annotated objects so every call goes through the registry.
ToolCallbackProvider tools = HookedTools.wrap(registry, new HookContext(), new MyTools());
```

With Spring Boot, `AgentHooksAutoConfiguration` builds the registry from your `AgentHookProvider`
beans. Its default `HookContext` is application-wide, so a multi-user server should supply its own
request- or session-scoped bean. Dispatch matches an event's exact class: register hooks on the
concrete record (`BeforeToolCall`), not a supertype. See the documentation for the Claude and Gemini
adapters.

## Build

```bash
./mvnw clean verify
```

Builds and tests the whole reactor. The full reactor needs **JDK 21**; the three Java 17 modules
build on JDK 17 with
`./mvnw clean verify -pl agent-hooks-core,agent-hooks-spring,agent-hooks-gemini`. Everything
resolves from Maven Central. [`AGENTS.md`](AGENTS.md) has the rest.

Dependency vulnerability scanning is deliberately not part of CI; see
[`scripts/security-scan.sh`](scripts/security-scan.sh) for the offline scan against a
vulnerability database snapshot you have validated and frozen yourself.

## License

[Business Source License 1.1](LICENSE): Change Date 2029-04-01, Change License Apache 2.0.
