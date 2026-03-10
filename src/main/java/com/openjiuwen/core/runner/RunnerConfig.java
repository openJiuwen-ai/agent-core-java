// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

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
}
