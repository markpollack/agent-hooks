package io.github.markpollack.hooks.claude.event;

import io.github.markpollack.hooks.decision.HookContext;
import io.github.markpollack.hooks.event.HookEvent;
import org.jspecify.annotations.Nullable;

/**
 * Event fired before the Claude CLI compacts the conversation context.
 * Observation-only — hooks can observe compaction but cannot prevent it.
 *
 * @param sessionId the Claude CLI session identifier
 * @param trigger the compaction trigger (e.g., "auto", "manual"), or null if unknown
 * @param customInstructions custom instructions injected during compaction, or null
 * @param context mutable session state shared across hooks
 */
public record PreCompact(String sessionId, @Nullable String trigger, @Nullable String customInstructions,
		HookContext context) implements HookEvent {
}
