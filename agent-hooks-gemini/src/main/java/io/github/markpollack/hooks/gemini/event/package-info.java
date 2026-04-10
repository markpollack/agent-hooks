/**
 * Gemini CLI-specific hook events. These events extend the core {@code HookEvent}
 * interface for Gemini CLI lifecycle points that have no equivalent in the core API.
 *
 * <p>All Gemini-specific events are observation-only — steering decisions
 * (Block, Modify, Retry) are logged as warnings and treated as Proceed.
 */
@NullMarked
package io.github.markpollack.hooks.gemini.event;

import org.jspecify.annotations.NullMarked;
