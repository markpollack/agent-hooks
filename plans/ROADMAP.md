# Roadmap: agent-hooks

> **Created**: 2026-04-09T14:00-04:00
> **Last updated**: 2026-04-09T19:00-04:00
> **Design version**: 2026-04-09T19:00-04:00 (v0.2 event hierarchy redesign)

## Overview

Four stages: **Foundation** (POM fixes, license, core types — complete), **Spring AI Adapter** (HookedToolCallback, auto-config — complete), **Event Hierarchy Refactoring** (v0.1→v0.2 type system redesign), and **Workshop Integration** (new step in art-of-building-agents — deferred).

Stage 3 refactors the core API from a closed enum+sealed-interface dual hierarchy to an open event hierarchy where the event IS the input. See `plans/DESIGN.md` for full rationale and `plans/review.md` for decision analysis.

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

## Stage 3: Event Hierarchy Refactoring (v0.1 → v0.2)

### Step 3.0: Refactoring Entry — Design Verification

**Entry criteria**:
- [ ] Read: `plans/DESIGN.md` — v0.2 type system
- [ ] Read: `plans/review.md` — decision rationale
- [ ] Read: `plans/learnings/LEARNINGS.md`

**Work items**:
- [ ] REVIEW v0.2 design against current source code — identify all files that change
- [ ] MAP the exact delta: which files are created, modified, deleted
- [ ] VERIFY ArchUnit rules still make sense for new package structure
- [ ] DOCUMENT the migration plan (file-by-file)

**Exit criteria**:
- [ ] Migration plan documented
- [ ] Create: `plans/learnings/step-3.0-refactoring-entry.md`
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 3.1: Core Event Types (replace enum + sealed interface)

**Entry criteria**:
- [ ] Step 3.0 complete
- [ ] Read: `plans/learnings/step-3.0-refactoring-entry.md`

**Work items**:
- [ ] CREATE `HookEvent` unsealed interface (`context(): HookContext`)
- [ ] CREATE `ToolEvent` sub-interface (`toolName(): String`, `toolInput(): String`)
- [ ] CONVERT `BeforeToolCall` from `HookInput` record → `ToolEvent` record
- [ ] CONVERT `AfterToolCall` from `HookInput` record → `ToolEvent` record
- [ ] CONVERT `SessionStart` from `HookInput` record → `HookEvent` record
- [ ] CONVERT `SessionEnd` from `HookInput` record → `HookEvent` record
- [ ] DELETE `AgentHookEvent` enum
- [ ] DELETE `HookInput` sealed interface
- [ ] UPDATE `AgentHook` — make generic: `<E extends HookEvent> HookDecision handle(E event)`
- [ ] UPDATE `package-info.java` files for any package moves
- [ ] UPDATE unit tests: `HookContextTest` (may reference old types)
- [ ] WRITE new tests for `HookEvent`/`ToolEvent` interface contracts
- [ ] VERIFY: `./mvnw compile -pl agent-hooks-core`
- [ ] COMMIT

**Exit criteria**:
- [ ] Core compiles with new event hierarchy
- [ ] No references to `AgentHookEvent` or `HookInput` remain in core
- [ ] Create: `plans/learnings/step-3.1-core-events.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 3.2: Registry Refactoring (type-based dispatch)

**Entry criteria**:
- [ ] Step 3.1 complete
- [ ] Read: `plans/learnings/step-3.1-core-events.md`

**Work items**:
- [ ] REFACTOR `AgentHookRegistry`:
  - `on(Class<E>, AgentHook<E>)` — type-based registration
  - `on(Class<E>, int priority, AgentHook<E>)` — with priority
  - `onTool(String pattern, Class<E extends ToolEvent>, AgentHook<E>)` — type-safe tool pattern
  - `dispatch(HookEvent)` — routes by `event.getClass()`, returns `HookDecision`
  - Reverse priority order for "after" events (AfterToolCall)
  - Runtime enforcement: Block/Modify/Retry on non-ToolEvent → log warning, treat as Proceed
- [ ] UPDATE `AgentHookRegistryTest`:
  - Type-based registration and dispatch
  - Reverse ordering for AfterToolCall
  - Runtime enforcement for non-tool decisions
  - Tool pattern matching with ToolEvent type constraint
  - All existing semantics (priority, Block short-circuit, Modify chaining, exception handling)
- [ ] VERIFY: `./mvnw test -pl agent-hooks-core`
- [ ] COMMIT

**Exit criteria**:
- [ ] Registry fully functional with type-based dispatch
- [ ] Reverse ordering for after events tested
- [ ] Runtime enforcement for non-tool decisions tested
- [ ] `./mvnw test -pl agent-hooks-core` green
- [ ] Create: `plans/learnings/step-3.2-registry-refactoring.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 3.3: Spring Adapter Update

**Entry criteria**:
- [ ] Step 3.2 complete
- [ ] Read: `plans/learnings/step-3.2-registry-refactoring.md`

**Work items**:
- [ ] UPDATE `HookedToolCallback`:
  - Construct `BeforeToolCall` / `AfterToolCall` event records instead of `HookInput` records
  - Call `registry.dispatch(event)` instead of `registry.dispatch(enum, input)`
- [ ] UPDATE `HookedToolCallbackProvider` (if needed)
- [ ] UPDATE `HookedTools` (if needed)
- [ ] UPDATE `AgentHooksAutoConfiguration` (if needed)
- [ ] UPDATE all Spring module tests
- [ ] UPDATE ArchUnit rules if package structure changed
- [ ] VERIFY: `./mvnw test` (both modules)
- [ ] VERIFY: JaCoCo coverage still passes (80% core, 70% spring)
- [ ] COMMIT

**Exit criteria**:
- [ ] Spring adapter works with new event hierarchy
- [ ] All 45+ tests green
- [ ] Coverage thresholds met
- [ ] Create: `plans/learnings/step-3.3-spring-adapter-update.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 3.K: Stage 3 Consolidation + Local Install

**Entry criteria**:
- [ ] All Stage 3 steps complete
- [ ] Read: all `plans/learnings/step-3.*` files

**Work items**:
- [ ] COMPACT learnings into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with v0.2 architecture summary
- [ ] UPDATE Javadoc on all public types
- [ ] RUN `./mvnw clean install` — install updated 0.1.0-SNAPSHOT to `~/.m2`
- [ ] VERIFY both artifacts in `~/.m2/repository/io/github/markpollack/`
- [ ] COMMIT

**Exit criteria**:
- [ ] Library installed locally with v0.2 event hierarchy
- [ ] `LEARNINGS.md` covers Stages 1-3
- [ ] Create: `plans/learnings/step-3.K-stage3-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

## Stage 4: Workshop Integration (DEFERRED)

> Deferred until Mark completes workshop research. The steps below are placeholders.

### Step 4.0: Workshop Context Load

**Entry criteria**:
- [ ] Stage 3 consolidation complete — Read: `plans/learnings/step-3.K-stage3-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md`
- [ ] Read: `~/projects/art-of-building-agents/CLAUDE.md`
- [ ] Read: `~/projects/art-of-building-agents/agents/03-guardrails/` source

**Work items**:
- [ ] REVIEW workshop handler pattern
- [ ] IDENTIFY integration approach
- [ ] DOCUMENT

**Exit criteria**:
- [ ] Create: `plans/learnings/step-4.0-workshop-entry.md`
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 4.1: Workshop Module

**Entry criteria**:
- [ ] Step 4.0 complete
- [ ] agent-hooks-spring:0.1.0-SNAPSHOT (v0.2) installed in `~/.m2`

**Work items**:
- [ ] CREATE workshop module with hook providers using v0.2 API
- [ ] Hook providers register via `registry.on(BeforeToolCall.class, hook)` (v0.2 syntax)
- [ ] VERIFY compilation

**Exit criteria**:
- [ ] Workshop compiles
- [ ] Create: `plans/learnings/step-4.1-workshop-module.md`
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 4.2: End-to-End Verification

**Entry criteria**:
- [ ] Step 4.1 complete
- [ ] OPENAI_API_KEY set

**Work items**:
- [ ] RUN and test end-to-end
- [ ] FIX issues
- [ ] COMMIT

**Exit criteria**:
- [ ] End-to-end demo functional
- [ ] Create: `plans/learnings/step-4.2-e2e.md`
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 4.K: Stage 4 Consolidation

**Exit criteria**:
- [ ] `LEARNINGS.md` covers all stages
- [ ] COMMIT

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
