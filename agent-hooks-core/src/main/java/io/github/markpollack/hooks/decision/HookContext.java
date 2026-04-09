package io.github.markpollack.hooks.decision;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mutable, thread-safe session state shared across hooks. One {@code HookContext} per
 * agent session.
 *
 * <p>
 * Hooks use {@link #get(String, Class)} and {@link #put(String, Object)} to share state
 * (e.g., tracking budget spent across tool calls). The adapter records completed tool
 * calls via {@link #recordToolCall(ToolCallRecord)}.
 */
public class HookContext {

	private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

	private final CopyOnWriteArrayList<ToolCallRecord> history = new CopyOnWriteArrayList<>();

	/**
	 * Get a value by key, cast to the expected type.
	 * @param <T> the expected type
	 * @param key the state key
	 * @param type the expected class
	 * @return the value if present and type-compatible, otherwise empty
	 */
	@SuppressWarnings("unchecked")
	public <T> Optional<T> get(String key, Class<T> type) {
		Object value = state.get(key);
		if (value != null && type.isInstance(value)) {
			return Optional.of((T) value);
		}
		return Optional.empty();
	}

	/**
	 * Put a value into the session state.
	 * @param key the state key
	 * @param value the value (must not be null)
	 */
	public void put(String key, Object value) {
		state.put(key, value);
	}

	/**
	 * Returns an unmodifiable view of the tool call history for this session.
	 * @return the tool call history, ordered chronologically
	 */
	public List<ToolCallRecord> history() {
		return Collections.unmodifiableList(history);
	}

	/**
	 * Record a completed tool call. Called by the adapter after each tool execution.
	 * @param record the tool call record to append
	 */
	public void recordToolCall(ToolCallRecord record) {
		history.add(record);
	}

}
