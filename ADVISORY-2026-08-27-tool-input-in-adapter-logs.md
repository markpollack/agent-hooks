# Advisory — tool-input payloads in adapter log and exception messages (0.6.x)

> **Affects** `agent-hooks-gemini` and `agent-hooks-claude` **0.6.0 – 0.6.4**.
> **Fixed in 0.7.0.** All 0.6.x versions remain published on Maven Central.
> **Published 2026-08-27.**

This is a disclosure, not a new finding. The fix shipped in 0.7.0 and is listed in
`release-notes/0.7.0.md`. What was never published is **which released versions are affected and
what an operator running one of them should do** — and a reader on 0.6.4 has no reason to read the
0.7.0 release notes, which describe what changes if you upgrade rather than what is true if you
do not.

## What happens

Two adapter code paths embedded a whole tool-input payload into a message:

| Where | What | When it fires |
|---|---|---|
| `GeminiDecisionMapper` | `LOG.warning(… + modify.modifiedInput())` via `java.util.logging` | whenever a hook returns a **`Modify`** decision — Gemini does not support `Modify`, so the adapter downgrades it to `allow` and logs the discarded payload |
| Claude `DecisionMapper` | `IllegalArgumentException("Failed to parse modified input as JSON: " + json, e)` | only when a **`Modify`** decision's replacement input **fails to parse as JSON** |

`HookDecision.Modify(String modifiedInput)` is documented as *"the replacement JSON input"* — it is
tool input, and tool inputs routinely carry credentials.

**This is not general logging of tool inputs.** Both paths require a hook that returns `Modify`. If
none of your hooks ever returns `Modify`, neither path is reachable and you are not affected.

## Affected versions

Verified by blob hash on `GeminiDecisionMapper.java` — byte-identical across all three tagged 0.6.x
releases, and changed only at 0.7.0:

```
v0.6.2  2c8622c9e828      v0.7.0  b32069ba6357   ← fixed
v0.6.3  2c8622c9e828      v0.8.2  b32069ba6357
v0.6.4  2c8622c9e828
```

The Claude-side exception is present at v0.6.2 and byte-identical at v0.6.3 and v0.6.4.

⚠ **0.6.0 and 0.6.1 are published to Maven Central but carry no git tag**, so their source cannot be
verified from this repository. They precede every version that is verifiably affected. **Treat them
as affected.**

## The fix

0.7.0 records the size instead of the content:

```java
LOG.warning("… treating as allow (" + modify.modifiedInput().length() + "-character payload discarded)");
throw new IllegalArgumentException("Failed to parse modified input as JSON (" + json.length() + " characters)", e);
```

## What to do

- **Upgrade to 0.7.0 or later.** 0.8.2 is current.
- **If you ran 0.6.x with any hook that returns `Modify`**, treat the Gemini CLI and Claude CLI logs
  from that period as potentially containing tool inputs, and rotate anything those inputs carried.
- If no hook of yours returns `Modify`, no action is needed.

## Why this is published separately

The 0.7.0 release note lists the fix under *"Behavior and documentation corrections"*, one bullet
among six, beside javadoc improvements. That framing is accurate about the change and wrong about
its weight: it reads as tidying rather than as a credential path, and it is filed where only someone
already upgrading would look.
