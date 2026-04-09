# Roadmap: agent-hooks

> **Created**: 2026-04-09T14:00-04:00
> **Last updated**: 2026-04-09T14:00-04:00
> **Design version**: 2026-04-09 (updated from 2026-03-23 scaffold)

## Overview

Three stages: **Foundation** (POM fixes, license, core types), **Spring AI Adapter** (HookedToolCallback, auto-config, HookedTools utility), and **Workshop Integration** (new step in art-of-building-agents). The core types are implemented first so the Spring adapter can depend on a stable API. The workshop step validates the library end-to-end with a real agent.

Pre-work: update VISION.md and DESIGN.md to reflect version alignment (Spring AI 2.0.0-M3, Boot 4.1.0-M2) and BSL 1.1 license before Stage 1 begins.

> **Before every commit**: Verify ALL exit criteria for the current step are met. Do NOT remove exit criteria to mark a step complete — fulfill them.

---

## Stage 1: Foundation + Core API

### Step 1.0: Design Review + Version Alignment

**Entry criteria**:
- [x] Read: `plans/DESIGN.md`
- [x] Read: `plans/VISION.md`

**Work items**:
- [x] UPDATE `pom.xml` versions: `spring-ai.version` → `2.0.0-M3`, `spring-boot.version` → `4.1.0-M2`
- [x] UPDATE `pom.xml` license block: Apache 2.0 → BSL 1.1 (match `~/projects/agent-journal/pom.xml`)
- [x] CREATE `LICENSE` file (BSL 1.1, Licensed Work: "Agent Hooks", Change Date: 2029-04-01, template from `~/projects/agent-journal/LICENSE`)
- [x] UPDATE `plans/VISION.md`: change constraints section from Apache 2.0 → BSL 1.1, update Spring AI version references
- [x] UPDATE `plans/DESIGN.md`: update Build Coordinates table (Spring AI 2.0.0-M3, Spring Boot 4.1.0-M2), verify ToolCallback API matches Spring AI 2.0.0-M3
- [x] VERIFY `ToolCallback.call(String, ToolContext)` signature in Spring AI 2.0.0-M3 source (`~/projects/spring-ai`)
- [x] VERIFY scaffold compiles: `./mvnw compile -q`
- [x] COMMIT

**Exit criteria**:
- [x] POMs aligned with workshop versions (Spring AI 2.0.0-M3, Boot 4.1.0-M2)
- [x] BSL 1.1 license in place
- [x] VISION.md and DESIGN.md updated
- [x] Scaffold compiles with updated dependencies
- [x] Create: `plans/learnings/step-1.0-design-review.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 1.1: Core Types

**Entry criteria**:
- [x] Step 1.0 complete
- [x] Read: `plans/learnings/step-1.0-design-review.md`
- [x] Read: `plans/DESIGN.md` — Interfaces and Data Models sections

**Work items**:
- [x] IMPLEMENT `AgentHookEvent` enum (4 events)
- [x] IMPLEMENT `HookDecision` sealed interface + 4 record variants + static factory methods
- [x] IMPLEMENT `ToolCallRecord` record
- [x] IMPLEMENT `HookContext` (ConcurrentHashMap state, CopyOnWriteArrayList history, thread-safe)
- [x] IMPLEMENT `HookInput` sealed interface + 4 record variants (BeforeToolCall, AfterToolCall, SessionStart, SessionEnd — all include hookContext)
- [x] IMPLEMENT `AgentHook` @FunctionalInterface
- [x] IMPLEMENT `AgentHookProvider` interface
- [x] WRITE unit tests: `HookDecisionTest` (factory methods, sealed exhaustiveness), `HookContextTest` (get/put, history immutability, recordToolCall)
- [x] VERIFY: `./mvnw test -pl agent-hooks-core`
- [x] COMMIT

**Exit criteria**:
- [x] All core types compile and tests pass
- [x] `./mvnw test -pl agent-hooks-core` green
- [x] Create: `plans/learnings/step-1.1-core-types.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: Core type system (7 files + 2 test files)

---

### Step 1.2: Registry + Dispatch Engine

**Entry criteria**:
- [x] Step 1.1 complete
- [x] Read: `plans/learnings/step-1.1-core-types.md`

**Work items**:
- [x] IMPLEMENT `AgentHookRegistry`:
  - `on(event, hook)` and `on(event, priority, hook)` registration
  - `onTool(pattern, event, hook)` pattern-based registration (regex)
  - `register(provider)` provider registration
  - `dispatch(event, input)` with priority ordering, Block short-circuit, Modify chaining
  - Internal: `PrioritizedHook` record, `CopyOnWriteArrayList` per event
- [x] WRITE `AgentHookRegistryTest`:
  - Priority ordering (lower first)
  - Block short-circuits remaining hooks
  - Modify chains (subsequent hooks see modified input)
  - Tool pattern matching (regex)
  - Exception in hook treated as Proceed
  - Retry on BEFORE_TOOL_CALL → IllegalStateException
  - Provider registration delegates to provider
- [x] VERIFY: `./mvnw test -pl agent-hooks-core`
- [x] COMMIT

**Exit criteria**:
- [x] Registry dispatch logic fully tested
- [x] `./mvnw test -pl agent-hooks-core` green
- [x] Create: `plans/learnings/step-1.2-registry.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: Complete core module

---

### Step 1.K: Stage 1 Consolidation

**Entry criteria**:
- [x] All Stage 1 steps complete
- [x] Read: all `plans/learnings/step-1.*` files

**Work items**:
- [x] COMPACT learnings into `plans/learnings/LEARNINGS.md`
- [x] UPDATE `CLAUDE.md` with distilled learnings from Stage 1
- [x] COMMIT

**Exit criteria**:
- [x] `LEARNINGS.md` covers Stage 1
- [x] Create: `plans/learnings/step-1.K-stage1-summary.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

## Stage 2: Spring AI Adapter

### Step 2.0: Stage 2 Entry — Review and Context Load

**Entry criteria** *(inter-stage gate)*:
- [x] Stage 1 consolidation complete — Read: `plans/learnings/step-1.K-stage1-summary.md`
- [x] Read: `plans/learnings/LEARNINGS.md`
- [x] Read: `plans/DESIGN.md` — Spring adapter components section

**Work items**:
- [x] REVIEW core API for any changes needed before building adapter
- [x] READ Spring AI `ToolCallback`, `ToolCallbackProvider`, `DefaultToolCallingManager` source (`~/projects/spring-ai`)
- [x] VERIFY: `MethodToolCallbackProvider` accessible from agent-hooks-spring (needed for `HookedTools.wrap()`)
- [x] DOCUMENT integration approach

**Exit criteria**:
- [x] Core API verified stable for adapter use
- [x] Create: `plans/learnings/step-2.0-stage2-entry.md`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 2.1: HookedToolCallback

**Entry criteria**:
- [x] Step 2.0 complete
- [x] Read: `plans/learnings/step-2.0-stage2-entry.md`

**Work items**:
- [x] IMPLEMENT `HookedToolCallback` wrapping `ToolCallback`:
  - Dispatches `BEFORE_TOOL_CALL` before delegate
  - Handles Block (return reason as result, delegate never called), Modify (pass modified input)
  - Dispatches `AFTER_TOOL_CALL` after execution (with result, duration, exception)
  - Records tool call in `HookContext.history()`
  - Delegates `getToolDefinition()` and `getToolMetadata()` to original
- [x] WRITE `HookedToolCallbackTest` with mock ToolCallback (Mockito):
  - Proceed passes through unchanged
  - Block returns reason string, delegate never called
  - Modify passes modified input to delegate
  - AfterToolCall receives correct duration and result
  - Exception in tool execution captured in AfterToolCall
  - Tool call recorded in HookContext history
- [x] VERIFY: `./mvnw test -pl agent-hooks-spring`
- [x] COMMIT

**Exit criteria**:
- [x] HookedToolCallback fully functional
- [x] `./mvnw test -pl agent-hooks-spring` green
- [x] Create: `plans/learnings/step-2.1-hooked-tool-callback.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: Core Spring adapter component

---

### Step 2.2: Provider, Utility, and Auto-Configuration

**Entry criteria**:
- [x] Step 2.1 complete
- [x] Read: `plans/learnings/step-2.1-hooked-tool-callback.md`

**Work items**:
- [x] IMPLEMENT `HookedToolCallbackProvider` wrapping `ToolCallbackProvider`:
  - Wraps each `ToolCallback` from delegate with `HookedToolCallback`
  - Shares a single `AgentHookRegistry` + `HookContext`
- [x] IMPLEMENT `HookedTools` static utility:
  - `wrap(registry, hookContext, toolObjects...)` — creates `MethodToolCallbackProvider`, wraps it
  - `wrap(registry, hookContext, ToolCallback...)` — wraps existing callbacks
- [x] IMPLEMENT `AgentHooksAutoConfiguration`:
  - `@AutoConfiguration @ConditionalOnClass(ToolCallback.class)`
  - Creates `AgentHookRegistry` bean from all `AgentHookProvider` beans
  - Creates default `HookContext` bean
- [x] CREATE `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [x] WRITE `AgentHooksAutoConfigurationTest` (Spring context test):
  - Registry created from providers
  - HookContext bean created
- [x] VERIFY: `./mvnw verify -pl agent-hooks-spring`
- [x] COMMIT

**Exit criteria**:
- [x] Auto-configuration + utility wiring complete
- [x] `./mvnw verify` green (both modules)
- [x] Create: `plans/learnings/step-2.2-auto-config.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: Complete Spring adapter with auto-configuration + HookedTools utility

---

### Step 2.K: Stage 2 Consolidation + Local Install

**Entry criteria**:
- [x] All Stage 2 steps complete
- [x] Read: all `plans/learnings/step-2.*` files

**Work items**:
- [x] COMPACT learnings into `plans/learnings/LEARNINGS.md`
- [x] UPDATE `CLAUDE.md` with full Stage 2 learnings
- [x] RUN `./mvnw clean install` — install 0.1.0-SNAPSHOT to `~/.m2`
- [x] VERIFY both artifacts in `~/.m2/repository/io/github/markpollack/`
- [x] COMMIT

**Exit criteria**:
- [x] Library installed locally, ready for workshop consumption
- [x] `LEARNINGS.md` covers Stages 1-2
- [x] Create: `plans/learnings/step-2.K-stage2-summary.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

## Stage 3: Workshop Integration

### Step 3.0: Stage 3 Entry — Workshop Context Load

**Entry criteria** *(inter-stage gate)*:
- [ ] Stage 2 consolidation complete — Read: `plans/learnings/step-2.K-stage2-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md`
- [ ] Read: `~/projects/art-of-building-agents/CLAUDE.md`
- [ ] Read: `~/projects/art-of-building-agents/agents/03-guardrails/` source (handler pattern)

**Work items**:
- [ ] REVIEW workshop handler pattern (AgentHandler, Session, ChatClient builder)
- [ ] REVIEW step 03 RestaurantTools (to copy)
- [ ] IDENTIFY workshop pom.xml changes needed (agents/pom.xml module list)
- [ ] DOCUMENT integration approach

**Exit criteria**:
- [ ] Workshop patterns understood, integration approach documented
- [ ] Create: `plans/learnings/step-3.0-workshop-entry.md`
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 3.1: Workshop Module

**Entry criteria**:
- [ ] Step 3.0 complete
- [ ] Read: `plans/learnings/step-3.0-workshop-entry.md`
- [ ] agent-hooks-spring:0.1.0-SNAPSHOT installed in `~/.m2`

**Work items**:
- [ ] CREATE `agents/04-hooks/pom.xml` (deps: agent-core, spring-boot-starter-web, spring-ai-starter-model-openai, agent-hooks-spring:0.1.0-SNAPSHOT)
- [ ] ADD `<module>04-hooks</module>` to `agents/pom.xml`
- [ ] CREATE `HooksApplication.java` (@SpringBootApplication)
- [ ] COPY `RestaurantTools.java` from step 03 (same 4 tools, new package)
- [ ] CREATE `ExpensePolicyHookProvider.java` (steering hook: blocks bookTable when over budget)
- [ ] CREATE `ToolCallLoggingHookProvider.java` (observation hook: counts/logs tool calls)
- [ ] CREATE `HooksHandler.java` (AgentHandler impl using HookedTools.wrap(), hook activity table in state panel)
- [ ] CREATE `application.yml`
- [ ] VERIFY: `./mvnw compile -pl agents/04-hooks` (from workshop root)
- [ ] COMMIT (in workshop repo)

**Exit criteria**:
- [ ] Workshop step compiles against locally installed agent-hooks
- [ ] `./mvnw compile -pl agents/04-hooks` green
- [ ] Create: `plans/learnings/step-3.1-workshop-module.md` (in agent-hooks repo)
- [ ] Update `ROADMAP.md` checkboxes

**Deliverables**: Workshop step demonstrating hooks with Inspector UI

---

### Step 3.2: End-to-End Verification

**Entry criteria**:
- [ ] Step 3.1 complete
- [ ] Read: `plans/learnings/step-3.1-workshop-module.md`
- [ ] OPENAI_API_KEY set

**Work items**:
- [ ] RUN `./mvnw spring-boot:run -pl agents/04-hooks`
- [ ] TEST: send "Find a restaurant in Paral-lel for 4 people"
- [ ] VERIFY: hook activity table appears in Inspector state panel
- [ ] VERIFY: expense policy hook blocks over-budget bookings
- [ ] VERIFY: tool call logging hook counts calls
- [ ] FIX any issues found during manual testing
- [ ] COMMIT (both repos if needed)

**Exit criteria**:
- [ ] End-to-end workshop demo functional
- [ ] Hook activity visible in Inspector
- [ ] Create: `plans/learnings/step-3.2-e2e-verification.md`
- [ ] Update `ROADMAP.md` checkboxes

---

### Step 3.K: Stage 3 Consolidation

**Entry criteria**:
- [ ] All Stage 3 steps complete
- [ ] Read: all `plans/learnings/step-3.*` files

**Work items**:
- [ ] COMPACT learnings into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with full project learnings
- [ ] COMMIT

**Exit criteria**:
- [ ] `LEARNINGS.md` covers all stages
- [ ] Create: `plans/learnings/step-3.K-stage3-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

## Plans Directory Structure

```
plans/
├── ROADMAP.md
├── DESIGN.md
├── VISION.md
├── inbox/
├── prompts/
└── learnings/
    ├── LEARNINGS.md
    ├── step-1.0-design-review.md
    ├── step-1.1-core-types.md
    └── ...
```

---

## Conventions

### Commit Convention

```
Step X.Y: Brief description of what was done
```

### Step Entry Criteria Convention

Every step's entry criteria must include:
```markdown
- [ ] Previous step complete
- [ ] Read: `plans/learnings/step-{{PREV}}-{{topic}}.md` — prior step learnings
```

### Step Exit Criteria Convention

Every step's exit criteria must include:
```markdown
- [ ] All tests pass: `./mvnw test` (or `./mvnw verify` if integration tests)
- [ ] Create: `plans/learnings/step-X.Y-topic.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT
```

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-09T14:00-04:00 | Initial draft — converted from claude plan | /plan-to-roadmap |
