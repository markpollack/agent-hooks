# Agent Hooks Agent Instructions

A portable Java hook API for steering agent behavior at the tool-call boundary, with adapters for Spring AI, the Claude Agent SDK and the Gemini CLI.

## Steward

Private steward: `/home/mark/projects/agent-hooks-steward`. Read its `BINDING.md` before planning or
executing work.

## AI Development Laws

> Reality first. Evidence sufficient to trust it. Infrastructure only when necessary.

1. **Maintain Contact With Reality.** Prefer the shortest credible path to observing real application behavior and completing the assigned engineering objective. Use established development environments, dependencies, tools and workflows unless the objective specifically requires isolation. Treat reproducibility and evidence integrity as constraints on the assigned work. Distinguish apparatus failure from failure of the engineering objective. Do not spend more effort perfecting the apparatus than learning from the experiment.
2. **Objective Is Sovereign.** The assigned objective, acceptance criteria and scope govern the work. Do not substitute infrastructure improvements or unrelated engineering for the requested outcome.
3. **Discovery Is Not Authorization.** Discovering an unrelated problem does not authorize fixing it. Report it or request authorization when necessary.
4. **Do Not Confuse Measurement With the Objective.** Measurement, recording, qualification and evaluation support real engineering work. Their failures must not automatically invalidate independently established results or trigger unrelated infrastructure projects.
5. **Stop When Done.** Once the authorized objective and acceptance criteria are satisfied, report the result and stop. Do not add speculative hardening or new work.

The shortest credible path must satisfy the actual acceptance contract, required tests, honest evidence, pinned experimental inputs and safety requirements. Experimental isolation and independent evaluation apply where the owning contract requires them.

For missing Maven release artifacts, first investigate the established dependency installation procedure. A private Maven cache needs a concrete requirement from the assigned objective.

## Build and test

Build from the reactor root on **JDK 21** (`agent-hooks-claude` needs it; CI runs at 21).

```bash
./mvnw clean verify                              # the gate: tests, ArchUnit, JaCoCo coverage checks
./mvnw test -pl agent-hooks-core -Dtest=AgentHookRegistryTest   # one test
./mvnw clean verify -pl agent-hooks-core,agent-hooks-spring,agent-hooks-gemini   # JDK 17 subset
```

A single adapter needs `-am` (for example `-pl agent-hooks-spring -am`), or core must already be
installed. Vulnerability scanning is not part of CI; run `scripts/security-scan.sh` against an
offline database you have validated and frozen.

`ClaudeCliLiveTest` drives a real Claude CLI (PreToolUse Block and Modify through SDK and CLI) and is
skipped unless `AGENT_HOOKS_LIVE=true` (or `-Dagent-hooks.live=true`) is set and `claude` is on
`PATH`. It calls a real model. Run it locally before a release that changes the SDK:
`AGENT_HOOKS_LIVE=true ./mvnw test -pl agent-hooks-claude -am -Dtest=ClaudeCliLiveTest -Dsurefire.failIfNoSpecifiedTests=false`

## Modules

- `agent-hooks-core`: events, decisions, `AgentHookRegistry`, SPI. Java 17.
- `agent-hooks-spring`: wraps Spring AI `ToolCallback`s (`HookedTools`, `HookedToolCallback`) and
  Spring Boot auto-configuration. Java 17.
- `agent-hooks-gemini`: stateless stdin/stdout dispatcher for Gemini CLI hooks. Java 17.
- `agent-hooks-claude`: `AgentHookBridge` into the Claude Agent SDK `HookRegistry`; the SDK is
  `provided` scope. Java 21, because every `claude-code-sdk` release is Java 21 bytecode.

## Architecture

- Core has no framework dependency. Its only compile dependency is `org.jspecify`, and it logs with
  `java.util.logging`. Enforced by `ArchitectureTest` in core.
- Adapters depend on core, never the reverse. `HookEvent` is unsealed, so an adapter adds its own
  events as records without changing core. The event is the input; there is no parallel input type.
- `HookDecision` is sealed (`Proceed`, `Block`, `Modify`, `Retry`). Only `ToolEvent`s honour
  anything but `Proceed`. `Block` short-circuits, `Modify` chains, and `AfterToolCall` hooks run in
  reverse priority order.
- Dispatch matches the event's exact runtime class. A hook registered on a supertype never fires.

## Hard rules

- **Log and exception messages never include tool-input payloads.** They carry credentials; 0.6.x
  leaked them and needed the public advisory at the repository root.
- **A failing hook degrades to `Proceed` and never takes the agent down.** The Gemini dispatcher
  writes exactly one JSON object to stdout on every invocation (`{}` on any failure) and logs only
  to stderr. Breaking this wedges the Gemini CLI.
- **The group ID is `io.github.markpollack`, settled.** Changing it breaks every consumer and the
  AgentWorks BOM that manages these modules.
- **Everything resolves from Maven Central.** The parent declares no `<repositories>`; a published
  POM that adds one hands consumers extra repositories. Check: `grep -n '<repositories>' */pom.xml pom.xml`.
- **Coverage floors stay: 80% core, 70% per adapter.** `jacoco:check` fails `verify` below them.
- **Licence is BSL 1.1 (Change Date 2029-04-01, then Apache 2.0).** Relicensing is an owner
  decision that changes every consumer's terms; never do it in a code change.

## Docs

- User docs: https://lab.pollack.ai/projects/agent-hooks
- Releases: https://github.com/markpollack/agent-hooks/releases and `release-notes/`
- API: Javadoc in the sources, https://javadoc.io/doc/io.github.markpollack/agent-hooks-core. The
  code and its tests win over the docs.

## When the preferred path hits a separate defect

If the preferred path in a work order runs into a defect outside its scope, aim for the smallest change that can ship
with the full gate green:

1. Where a quick check can establish it, check whether the defect also occurs on the baseline.
2. If a smaller change still meets the work order's acceptance criteria and passes the full gate, continue within the
   existing authorization, and record the separate defect with its owning repository.
3. Bring the Owner a decision instead if proceeding would change the acceptance criteria, weaken the gate, or expand
   the work into another repository.
