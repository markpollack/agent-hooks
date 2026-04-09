# Build Handoff: agent-hooks

## Mission

Implement Stage 1 of ROADMAP.md — Foundation (scaffold, quality infrastructure, test infrastructure).

## Before You Start

1. Read `CLAUDE.md` — project scope, build commands, architecture
2. Read `plans/VISION.md` — problem, success criteria, scope
3. Read `plans/DESIGN.md` — types, interfaces, decisions
4. Read `plans/ROADMAP.md` — Stage 1 steps with entry/exit criteria

## First Roadmap Step

**Step 1.0: Design Review** — Read DESIGN.md and VISION.md, verify the sealed interface approach works with Java 17, verify the Spring AI ToolCallback wrapping approach by reading Spring AI source, document any design changes.

### Entry Criteria
- Read DESIGN.md and VISION.md

### Exit Criteria
- Design reviewed and approved
- Learnings file created
- CLAUDE.md updated
- ROADMAP.md checkboxes updated

## Key Integration Context

| Project | What to reference |
|---------|------------------|
| Spring AI (`~/projects/spring-ai`) | `ToolCallback`, `ToolCallbackProvider`, `ToolContext`, `DefaultToolCallingManager` — verify wrapping approach |
| claude-agent-sdk-java (`~/community/claude-agent-sdk-java`) | `HookRegistry`, `HookCallback`, `HookOutput` — ensure core API is compatible for a bridge adapter |
| agent-journal (`~/projects/agent-journal`) | `Run`, `JournalEvent`, `ToolCallEvent` — plan the bridge hook provider |
| agent-harness (`~/projects/agent-harness`) | `ChatClientStep`, `AgentLoopAdvisor` — understand how auto-config would wire in |

## After Each Step

1. Run tests: `./mvnw test`
2. Create learnings file: `plans/learnings/step-X.Y-topic.md`
3. Update `CLAUDE.md` with distilled learnings
4. Update `ROADMAP.md` checkboxes
5. Commit: `git commit`
