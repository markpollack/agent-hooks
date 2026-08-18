# Agent Hooks

A portable Java hook API for steering agent behavior at the tool-call boundary. Write a
policy once — block a dangerous tool, rewrite an argument, record what ran and how long it
took — and run it on any agent runtime that has an adapter. `agent-hooks-core` defines the
event model, decision types, and dispatch registry; the Spring AI, Claude Agent SDK, and
Gemini CLI adapters wire that core into each runtime's tool-call lifecycle.

**Documentation: [lab.pollack.ai/projects/agent-hooks](https://lab.pollack.ai/projects/agent-hooks)**

## Modules

| Artifact | Adapter for |
|---|---|
| `agent-hooks-core` | none — the portable API |
| `agent-hooks-spring` | Spring AI `ToolCallback` |
| `agent-hooks-claude` | Claude Agent SDK (`claude-code-sdk`, `provided` scope) |
| `agent-hooks-gemini` | Gemini CLI stdin/stdout hook protocol |

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

Builds and tests the whole reactor from a clean checkout. Requires JDK 17 or later; all
artifacts target Java 17 bytecode. Everything resolves from Maven Central.

Dependency vulnerability scanning is deliberately not part of CI — see
[`scripts/security-scan.sh`](scripts/security-scan.sh) for the offline scan against a
vulnerability database snapshot you have validated and frozen yourself.

## Maturity

Pre-1.0. The event, decision, and registry types are stable across the 0.6.x line, but
minor releases may still change them. `HookDecision.Retry` is advisory: no shipped adapter
re-executes a tool for you.

## License

[Business Source License 1.1](LICENSE)
