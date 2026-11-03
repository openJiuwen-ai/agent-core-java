/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ProgressManager} and module-level
 * {@code progress_manager} in {@code openjiuwen/dev_tools/agent_builder/utils/progress.py}.
 */
public class ProgressManager {
    public static final ProgressManager PROGRESS_MANAGER = new ProgressManager();

    private final Map<String, ProgressReporter> reporters;

    public ProgressManager() {
        this.reporters = new LinkedHashMap<>();
    }

    public ProgressReporter getReporter(String sessionId) {
        return reporters.get(sessionId);
    }

    public ProgressReporter createReporter(String sessionId, String agentType) {
        if (reporters.containsKey(sessionId)) {
            return reporters.get(sessionId);
        }

        ProgressReporter reporter = new ProgressReporter(sessionId, agentType);
        reporters.put(sessionId, reporter);
        return reporter;
    }

    public void removeReporter(String sessionId) {
        reporters.remove(sessionId);
    }

    public BuildProgress getProgress(String sessionId) {
        ProgressReporter reporter = reporters.get(sessionId);
        return reporter != null ? reporter.getProgress() : null;
    }
}
