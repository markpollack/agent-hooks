# Roadmap: agent-hooks

> **Created**: 2026-04-09T14:00-04:00
> **Last updated**: 2026-04-10T10:00-04:00
> **Design version**: 2026-04-10T10:00-04:00 (v0.2 event hierarchy redesign)

## Overview

Six stages: **Foundation** (core types — complete), **Spring AI Adapter** (HookedToolCallback, auto-config — complete), **Event Hierarchy Refactoring** (v0.1→v0.2 — complete), **Claude Agent SDK Adapter** (AgentHookBridge, cross-adapter proof — complete), **Workshop Integration** (art-of-building-agents — complete), and **Gemini CLI Adapter** (stateless stdin/stdout — complete). All stages complete. 102 tests, 4 modules, 3 runtime adapters.

Stage 3 refactors the core API from a closed enum+sealed-interface dual hierarchy to an open event hierarchy where the event IS the input. See `plans/DESIGN.md` for full rationale and `plans/review.md` for decision analysis.

> **Note**: Stage 4 (Claude adapter) was completed before Stage 5 (workshop) because the workshop is blocked on external research. The numbering reflects actual completion order, not the original plan order.

> **Before every commit**: Verify ALL exit criteria for the current step are met. Do NOT remove exit criteria to mark a step complete — fulfill them.

---

## Stage 1: Foundation + Core API (COMPLETE)

### Step 1.0: Design Review + Version Alignment (COMPLETE)

**Work items**: Updated POMs (Spring AI 2.0.0-M3, Boot 4.1.0-M2), BSL 1.1 license, VISION.md, DESIGN.md.
**Commit**: `9fc78f3`

### Step 1.1: Core Types (COMPLETE)

**Work items**: AgentHookEvent enum, HookDecision sealed interface, ToolCallRecord, HookContext, HookInput sealed interface, AgentHook, AgentHookProvider. 12 tests.
**Commit**: `38a14c3`

### Step 1.2: Registry + Dispatch Engine (COMPLETE)

**Work items**: AgentHookRegistry with priority ordering, Block short-circuit, Modify chaining, tool pattern matching. 10 tests.
**Commit**: `dc4b63a`

### Step 1.K: Stage 1 Consolidation (COMPLETE)

**Commit**: `383b43d`

---

## Stage 2: Spring AI Adapter (COMPLETE)

### Step 2.0: Stage 2 Entry (COMPLETE)

**Commit**: `6794f55`

### Step 2.1: HookedToolCallback (COMPLETE)

**Work items**: Wraps ToolCallback with BEFORE/AFTER dispatch. Block/Modify/Proceed semantics. 9 tests.
**Commit**: `fb31a4d`

### Step 2.2: Provider, Utility, Auto-Configuration (COMPLETE)

**Work items**: HookedToolCallbackProvider, HookedTools, AgentHooksAutoConfiguration, META-INF imports. 4 auto-config tests.
**Commit**: `d8aff47`

### Step 2.K: Stage 2 Consolidation + Local Install (COMPLETE)

**Work items**: LEARNINGS.md, `./mvnw clean install` to ~/.m2. Quality commit: JaCoCo, ArchUnit, JSpecify, OWASP, Javadoc.
**Commits**: `5f083f8`, `f160e8d`

---

## Stage 3: Event Hierarchy Refactoring (v0.1 → v0.2) (COMPLETE)

### Step 3.0: Refactoring Entry — Design Verification (COMPLETE)

**Work items**: Reviewed v0.2 design, mapped all file deltas (6 create, 2 delete, 6 modify), verified ArchUnit rules.

### Step 3.1-3.2: Core Event Types + Registry Refactoring (COMPLETE)

**Work items**: Created `HookEvent`/`ToolEvent` interfaces + 4 event records. Deleted `AgentHookEvent` enum + `HookInput` sealed interface. Made `AgentHook` generic. Rewrote `AgentHookRegistry` with type-based dispatch, reverse ordering for AfterToolCall, runtime enforcement for non-tool events. 15 core tests (10 ported + 5 new).

### Step 3.3: Spring Adapter Update (COMPLETE)

**Work items**: Updated `HookedToolCallback` to construct event records directly. Updated 4 test files to v0.2 registration API. `HookedToolCallbackProvider`, `HookedTools`, auto-config unchanged. All ArchUnit rules pass without changes.

### Step 3.K: Stage 3 Consolidation + Local Install (COMPLETE)

**Work items**: LEARNINGS.md updated, `./mvnw clean install` to ~/.m2, learnings file created.

---

## Stage 4: Claude Agent SDK Adapter (COMPLETE)

> The abstraction only works with at least two implementations. This stage proves it.

### Step 4.1: Module Skeleton + Claude-Specific Event Types (COMPLETE)

**Work items**: Created `agent-hooks-claude` module. POM with `claude-code-sdk` (provided) + `jackson-databind` (provided). 4 event records (`UserPromptSubmit`, `AgentStop`, `SubagentStop`, `PreCompact`). `@NullMarked` package-info files. `ClaudeEventTest` (6 tests), `ArchitectureTest` (2 tests).

### Step 4.2: AgentHookBridge + DecisionMapper (COMPLETE)

**Work items**: `AgentHookBridge.registerInto(HookRegistry)` — registers 6 callbacks, converts Claude SDK types to core events, maps `HookDecision` → `HookOutput`. `DecisionMapper` (package-private): Proceed→allow, Block→block+deny, Modify→allow+modifyMap, Retry→warn+allow. Duration tracking via `ConcurrentHashMap<toolUseId, Instant>`. Per-session `HookContext` isolation. `AgentHookBridgeTest` (11 tests).

### Step 4.3: Cross-Adapter Proof (COMPLETE)

**Work items**: `CrossAdapterProviderTest` — same `AgentHookProvider` blocks "Bash" on both Claude and Spring paths. 4 tests (block+allow × 2 runtimes). `agent-hooks-spring` as test-only dependency.

### Step 4.K: Consolidation (COMPLETE)

**Work items**: JaCoCo ≥ 70%, Javadoc on all public types, ArchUnit green. Updated LEARNINGS.md, CLAUDE.md, DESIGN.md. `./mvnw clean install` — all 3 JARs to ~/.m2. 58 tests total.
**Commit**: `c0473a1`

---

## Stage 5: Workshop Integration (COMPLETE)

> Module `agents/03b-hooks/` in `~/projects/art-of-building-agents/`.

**What was built**: Dedicated workshop step ("Step 03b: Hooks — Prompt Suggests, Hook Enforces") with three teaching hook providers:
- `ToolCallLoggingProvider` — observation pattern (logs every tool call with timing)
- `ExpensePolicyProvider` — steering pattern (blocks `bookTable` unless `checkExpensePolicy` was called first)
- `CostGuardProvider` — tracking pattern (accumulates per-tool call counts and durations)

Integration uses `HookedTools.wrap(registry, hookContext, tools)` with per-invocation `HookContext` isolation. Workshop slides and README document the progression from prompt-based guardrails (Step 03) to hook-based enforcement (Step 03b).

---

## Stage 6: Gemini CLI Adapter — Stateless (COMPLETE)

> Stateless-only — see DD-8 in DESIGN.md.

### Step 6.1: Module Skeleton + Gemini Event Types (COMPLETE)

**Work items**: Created `agent-hooks-gemini` module. POM with `jackson-databind` (compile), `agent-hooks-core` (compile), JSpecify. 7 Gemini-specific event records (`GeminiBeforeAgent`, `GeminiAfterAgent`, `GeminiBeforeModel`, `GeminiAfterModel`, `GeminiBeforeToolSelection`, `GeminiNotification`, `GeminiPreCompress`). `@NullMarked` package-info files. `GeminiEventTest` (9 tests), `ArchitectureTest` (3 rules). 12 tests.
**Commit**: `6a2b3c0`

### Step 6.2: GeminiHookDispatcher + GeminiDecisionMapper (COMPLETE)

**Work items**: `GeminiHookDispatcher` — factory `create(providers...)`, `run()` reads stdin/dispatches/writes stdout, `dispatch(json)` testable core. Routes 11 Gemini events: 4 to core events (`BeforeToolCall`, `AfterToolCall`, `SessionStart`, `SessionEnd`) and 7 to Gemini-specific events. `GeminiDecisionMapper` (package-private): Proceed→allow, Block→block+reason, Modify→warn+allow, Retry→warn+allow. Stateless: `HookContext` fresh per invocation, `Duration.ZERO` for tool calls. 16 dispatcher tests, 28 total.
**Commit**: `f153fe6`

### Step 6.3: Cross-Adapter Proof — 3 Runtimes (COMPLETE)

**Work items**: Extended `CrossAdapterProviderTest` — same `bashBlocker` blocks via `GeminiHookDispatcher` (Gemini), `AgentHookBridge` (Claude), `HookedToolCallback` (Spring). 6 cross-adapter tests (block + allow × 3 runtimes). `agent-hooks-gemini` as test-only dependency in `agent-hooks-claude`.
**Commit**: `3c3aefe`

### Step 6.K: Consolidation (COMPLETE)

**Work items**: JaCoCo ≥ 70%, Javadoc, ArchUnit green. Updated LEARNINGS.md, CLAUDE.md, DESIGN.md (module structure, component diagram, landscape table fix: Gemini Can Modify → No), VISION.md, docs site. `./mvnw clean install` — all 4 modules to ~/.m2. 102 tests total.

---

## Conventions

### Commit Convention

```
Step X.Y: Brief description of what was done
```

### Step Entry/Exit Criteria Convention

Every step's entry criteria must include reading prior step learnings.
Every step's exit criteria must include: tests pass, learnings file, CLAUDE.md update, ROADMAP.md checkboxes, COMMIT.

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-09T14:00-04:00 | Initial draft — converted from claude plan | /plan-to-roadmap |
| 2026-04-09T19:00-04:00 | Added Stage 3 (event hierarchy refactoring), renumbered workshop to Stage 4. Collapsed completed stages to summaries. | review.md decision: Option B |
| 2026-04-10T10:00-04:00 | Stage 4 (Claude adapter) complete. Renumbered: Claude adapter 5→4, workshop 4→5. Updated workshop entry criteria to reference Stage 4 learnings (duration tracking, session isolation). | Stage 4 completion + doc review |
| 2026-04-10T11:00-04:00 | DD-8 resolved: stateless-only Gemini adapter in agent-hooks repo. Added Stage 6 placeholder. Stage 5 (workshop) marked complete — agents/03b-hooks/ already built in art-of-building-agents. | Design review decisions + workshop verification |
