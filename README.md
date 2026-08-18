# Agent Hooks

A portable Java hook API for steering agent behavior at the tool-call boundary. Write a
policy once — block a dangerous tool, rewrite an argument, record what ran and how long it
took — and run it on any agent runtime that has an adapter. `agent-hooks-core` defines the
event model, decision types, and dispatch registry; the Spring AI, Claude Agent SDK, and
Gemini CLI adapters wire that core into each runtime's tool-call lifecycle.

**Documentation: [lab.pollack.ai/projects/agent-hooks](https://lab.pollack.ai/projects/agent-hooks)**

## Modules

| Artifact | Adapter for | Requires |
|---|---|---|
| `agent-hooks-core` | none — the portable API | Java 17 |
| `agent-hooks-spring` | Spring AI `ToolCallback` | Java 17 |
| `agent-hooks-gemini` | Gemini CLI stdin/stdout hook protocol | Java 17 |
| `agent-hooks-claude` | Claude Agent SDK (`claude-code-sdk`, `provided` scope) | **Java 21** |

`agent-hooks-claude` needs Java 21 because every published `claude-code-sdk` version is
Java 21 bytecode. If you are on Java 17, the other three modules are unaffected.

## Dependency

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-hooks-core</artifactId>
    <version>0.6.4</version>
</dependency>
```

Add the adapter for your runtime alongside it. `agent-hooks-claude` expects you to supply
`io.github.markpollack:claude-code-sdk` yourself.

## Build

```bash
./mvnw clean verify
```

Builds and tests the whole reactor from a clean checkout. Building the full reactor requires
**JDK 21**, because `agent-hooks-claude` compiles against a Java 21 SDK. The other three
modules build and test on JDK 17 on their own:

```bash
./mvnw clean verify -pl agent-hooks-core,agent-hooks-spring,agent-hooks-gemini
```

Everything resolves from Maven Central.

Dependency vulnerability scanning is deliberately not part of CI — see
[`scripts/security-scan.sh`](scripts/security-scan.sh) for the offline scan against a
vulnerability database snapshot you have validated and frozen yourself.

## Maturity

Pre-1.0. The event, decision, and registry types are stable across the 0.6.x line, but
minor releases may still change them. `HookDecision.Retry` is advisory: no shipped adapter
re-executes a tool for you.

## License

[Business Source License 1.1](LICENSE)
