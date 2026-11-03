/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProgressRegistry.
 * 
 * @since 0.1.7
 */
public final class ProgressRegistry {
    private static final Map<String, ProgressReporter> REPORTERS = new ConcurrentHashMap<>();

    /**
     * ProgressRegistry.
     * 
     * @since 0.1.7
     */
    private ProgressRegistry() {
    }

    /**
     * register.
     * 
     * @param sessionId sessionId
     * @param reporter reporter
     * @since 0.1.7
     */
    public static void register(String sessionId, ProgressReporter reporter) {
        REPORTERS.put(sessionId, reporter);
    }

    /**
     * getProgress.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public static BuildProgress getProgress(String sessionId) {
        ProgressReporter reporter = REPORTERS.get(sessionId);
        return reporter != null ? reporter.getProgress() : null;
    }

    /**
     * remove.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public static void remove(String sessionId) {
        REPORTERS.remove(sessionId);
    }
}
