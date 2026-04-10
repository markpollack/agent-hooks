/**
 * Stateless stdin/stdout dispatcher for the Gemini CLI hook protocol.
 * {@link io.github.markpollack.hooks.gemini.dispatcher.GeminiHookDispatcher} reads
 * JSON from stdin, dispatches through the core
 * {@link io.github.markpollack.hooks.registry.AgentHookRegistry}, and writes
 * JSON to stdout.
 */
@NullMarked
package io.github.markpollack.hooks.gemini.dispatcher;

import org.jspecify.annotations.NullMarked;
