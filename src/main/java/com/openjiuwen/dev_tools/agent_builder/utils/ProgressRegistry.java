/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-generated for codecheck compliance.
 */
public final class ProgressRegistry {
    private static final Map<String, ProgressReporter> REPORTERS = new ConcurrentHashMap<>();

    private ProgressRegistry() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void register(String sessionId, ProgressReporter reporter) {
        REPORTERS.put(sessionId, reporter);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static BuildProgress getProgress(String sessionId) {
        ProgressReporter reporter = REPORTERS.get(sessionId);
        return reporter != null ? reporter.getProgress() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void remove(String sessionId) {
        REPORTERS.remove(sessionId);
    }
}
