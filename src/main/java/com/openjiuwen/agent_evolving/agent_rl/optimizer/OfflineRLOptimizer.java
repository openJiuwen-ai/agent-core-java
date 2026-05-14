/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.FileRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.NullRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RLMetricsTracker;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.nio.file.Path;

/**
 * Minimal offline RL optimizer shell.
 * <p>
 * Mirrors Python's {@code OfflineRLOptimizer} in
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.rl_optimizer}.
 */
public class OfflineRLOptimizer {

    protected final RLConfig config;
    protected final String runName;
    private final List<Object> tools = new ArrayList<>();
    private Object agentFactory;

    public OfflineRLOptimizer(RLConfig config) {
        this.config = config;
        this.config.validate();
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.runName = config.getTraining().getExperimentName() + "_" + timestamp;
    }

    public RLConfig getConfig() { return config; }
    public String getRunName() { return runName; }

    public Object buildPersistence(RLConfig config) {
        if (config == null || config.getPersistence() == null || !config.getPersistence().isEnabled()) {
            return new NullRolloutStore();
        }
        String configuredSavePath = config.getPersistence().getSavePath();
        String effectiveSavePath = Path.of(configuredSavePath, runName).toString();
        return new FileRolloutStore(effectiveSavePath, config.getPersistence().getFlushInterval());
    }

    public RLMetricsTracker buildMetricsTracker(RLConfig config) {
        return new RLMetricsTracker(
                config.getTraining().getProjectName(),
                runName,
                config.getTraining().getLogger(),
                new LinkedHashMap<>());
    }

    public void setTools(List<Object> tools) {
        this.tools.clear();
        if (tools != null) {
            this.tools.addAll(tools);
        }
    }

    public void setAgentFactory(Object agentFactory) {
        this.agentFactory = agentFactory;
    }

    public Object resolveAgentFactory() {
        if (agentFactory != null) {
            return agentFactory;
        }
        return new Object();
    }

    public void registerReward(Function<Object, Object> reward, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("reward name must not be empty");
        }
    }
}
