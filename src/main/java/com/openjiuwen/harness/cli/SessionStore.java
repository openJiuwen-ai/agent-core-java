/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class SessionStore used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SessionStore {
    private final Map<String, String> sessions = new LinkedHashMap<>();

    /**
     * newSession.
     * 
     * @param sessionId sessionId
     * @param model model
     * @since 0.1.7
     */
    public void newSession(String sessionId, String model) {
        sessions.put(sessionId, model);
    }

    /**
     * sessions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, String> sessions() {
        return Map.copyOf(sessions);
    }
}
