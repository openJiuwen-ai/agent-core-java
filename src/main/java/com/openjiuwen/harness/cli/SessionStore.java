/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class SessionStore used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SessionStore {
    private final Map<String, String> sessions = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public void newSession(String sessionId, String model) {
        sessions.put(sessionId, model);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> sessions() {
        return Map.copyOf(sessions);
    }
}
