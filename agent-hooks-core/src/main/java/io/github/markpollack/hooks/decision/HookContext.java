package io.github.markpollack.hooks.decision;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mutable, thread-safe session state shared across hooks. One HookContext per agent
 * session.
 */
public class HookContext {

	private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

	private final CopyOnWriteArrayList<ToolCallRecord> history = new CopyOnWriteArrayList<>();

	@SuppressWarnings("unchecked")
	public <T> Optional<T> get(String key, Class<T> type) {
		Object value = state.get(key);
		if (value != null && type.isInstance(value)) {
			return Optional.of((T) value);
		}
		return Optional.empty();
	}

	public void put(String key, Object value) {
		state.put(key, value);
	}

	/**
	 * Returns an unmodifiable view of the tool call history.
	 */
	public List<ToolCallRecord> history() {
		return Collections.unmodifiableList(history);
	}

	public void recordToolCall(ToolCallRecord record) {
		history.add(record);
	}

}
