/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured metrics tracker for RL training.
 * <p>
 * Mirrors Python's {@code RLMetricsTracker} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.store.metrics_tracker}.
 */
public class RLMetricsTracker {

    private final Map<String, Object> initKwargs = new LinkedHashMap<>();

    public RLMetricsTracker(String projectName, String experimentName, List<String> backends, Map<String, Object> config) {
        initKwargs.put("project_name", projectName);
        initKwargs.put("experiment_name", experimentName);
        initKwargs.put("default_backend", backends);
        initKwargs.put("config", config);
    }

    public Map<String, Object> getInitKwargs() {
        return initKwargs;
    }
}
