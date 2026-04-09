/**
 * Bridge between the Claude Agent SDK hook system and the agent-hooks core registry.
 * {@link io.github.markpollack.hooks.claude.bridge.AgentHookBridge} registers callbacks
 * into the Claude SDK's {@code HookRegistry} that dispatch to our
 * {@link io.github.markpollack.hooks.registry.AgentHookRegistry}.
 */
@NullMarked
package io.github.markpollack.hooks.claude.bridge;

import org.jspecify.annotations.NullMarked;
