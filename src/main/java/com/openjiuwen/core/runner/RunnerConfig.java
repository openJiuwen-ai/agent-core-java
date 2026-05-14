/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runner global configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerConfig {

    @Builder.Default
    private boolean distributedMode = true;

    @Builder.Default
    private DistributedConfig distributedConfig = new DistributedConfig();

    @Builder.Default
    private String envPrefix = "";

    @Builder.Default
    private String instanceId = UUID.randomUUID().toString();

    /**
     * Checkpointer configuration. Uses a generic map since CheckpointerConfig
     * has (type, conf) fields.
     * Key "type" -> String (e.g. "in_memory", "redis")
     * Key "conf" -> Map of configuration properties
     */
    private Map<String, Object> checkpointerConfig;

    /**
     * Get agent topic template with environment prefix.
     */
    public String agentTopicTemplate() {
        return distributedConfig.getAgentTopicTemplate(envPrefix);
    }

    /**
     * Get reply topic template with environment prefix.
     */
    public String replyTopicTemplate() {
        return distributedConfig.getReplyTopicTemplate(envPrefix);
    }

    // ========== Global Config ==========

    /** Default runner configuration (non-distributed, fake MQ). */
    public static final RunnerConfig DEFAULT = RunnerConfig.builder()
            .distributedMode(false)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(30.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    private static final AtomicReference<RunnerConfig> GLOBAL_CONFIG = new AtomicReference<>(null);

    /**
     * Set the global runner configuration.
     */
    public static void setRunnerConfig(RunnerConfig config) {
        GLOBAL_CONFIG.set(config);
    }

    /**
     * Get the global runner configuration.
     * Returns the default config if none has been set.
     */
    public static RunnerConfig getRunnerConfig() {
        RunnerConfig config = GLOBAL_CONFIG.get();
        if (config == null) {
            GLOBAL_CONFIG.compareAndSet(null, DEFAULT);
            return GLOBAL_CONFIG.get();
        }
        return config;
    }

    public static RunnerConfigBuilder builder() {
        return new RunnerConfigBuilder();
    }

    public DistributedConfig getDistributedConfig() {
        return distributedConfig;
    }

    public void setDistributedConfig(DistributedConfig distributedConfig) {
        this.distributedConfig = distributedConfig;
    }

    public boolean isDistributedMode() {
        return distributedMode;
    }

    public void setDistributedMode(boolean distributedMode) {
        this.distributedMode = distributedMode;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public void setEnvPrefix(String envPrefix) {
        this.envPrefix = envPrefix;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Map<String, Object> getCheckpointerConfig() {
        return checkpointerConfig;
    }

    public void setCheckpointerConfig(Map<String, Object> checkpointerConfig) {
        this.checkpointerConfig = checkpointerConfig;
    }

    public static final class RunnerConfigBuilder {
        private boolean distributedMode = true;
        private DistributedConfig distributedConfig = new DistributedConfig();
        private String envPrefix = "";
        private String instanceId = UUID.randomUUID().toString();
        private Map<String, Object> checkpointerConfig;

        public RunnerConfigBuilder distributedMode(boolean distributedMode) {
            this.distributedMode = distributedMode;
            return this;
        }

        public RunnerConfigBuilder distributedConfig(DistributedConfig distributedConfig) {
            this.distributedConfig = distributedConfig;
            return this;
        }

        public RunnerConfigBuilder envPrefix(String envPrefix) {
            this.envPrefix = envPrefix;
            return this;
        }

        public RunnerConfigBuilder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public RunnerConfigBuilder checkpointerConfig(Map<String, Object> checkpointerConfig) {
            this.checkpointerConfig = checkpointerConfig;
            return this;
        }

        public RunnerConfig build() {
            RunnerConfig config = new RunnerConfig();
            config.distributedMode = distributedMode;
            config.setDistributedConfig(distributedConfig);
            config.envPrefix = envPrefix;
            config.instanceId = instanceId;
            config.checkpointerConfig = checkpointerConfig;
            return config;
        }
    }
}
