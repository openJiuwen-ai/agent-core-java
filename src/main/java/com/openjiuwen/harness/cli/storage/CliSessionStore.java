/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.storage;

import java.util.*;

/**
 * CLI session store — persists session state.
 * <p>
 * Mirrors Python's {@code SessionStore} in
 * {@code openjiuwen.harness.cli.storage.session_store}.
 */
public class CliSessionStore {

    private final Map<String, Object> store = new LinkedHashMap<>();
    private final String storePath;

    public CliSessionStore(String storePath) {
        this.storePath = storePath;
    }

    /** Save session state. */
    public void save(String sessionId, Map<String, Object> state) {
        store.put(sessionId, state);
    }

    /** Load session state. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> load(String sessionId) {
        Object data = store.get(sessionId);
        return data instanceof Map ? (Map<String, Object>) data : Collections.emptyMap();
    }

    /** List all session IDs. */
    public Set<String> listSessions() {
        return Collections.unmodifiableSet(store.keySet());
    }

    /** Delete a session. */
    public void delete(String sessionId) {
        store.remove(sessionId);
    }
}
