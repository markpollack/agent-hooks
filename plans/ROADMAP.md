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
