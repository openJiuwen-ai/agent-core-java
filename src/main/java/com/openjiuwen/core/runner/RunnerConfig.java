/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runner global configuration.
 * <p>
 * Holds distributed mode settings, MCP server configurations, and SPI injection
 * fields for checkpointer, KV store, vector store, and object storage.
 * Service adapters populate the SPI config maps before the Runner starts,
 * and the corresponding Factory classes use them to create provider instances.
 *
 * @since 0.1.12
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
     * MCP server configurations for ToolMgr initialization.
     * Service adapters register MCP client providers via
     * {@link com.openjiuwen.core.foundation.tool.mcp.McpClientFactory#register}
     * before Runner starts, then provide server configs here.
     */
    @Builder.Default
    private List<McpServerConfig> mcpServers = new ArrayList<>();

    /**
     * KV store configuration for persistence.
     * Key "type" -> String (e.g. "in_memory", "redis")
     * Key "conf" -> Map of configuration properties
     */
    private Map<String, Object> kvStoreConfig;

    /**
     * Vector store configuration for RAG/retrieval.
     * Key "type" -> String (e.g. "milvus", "chroma")
     * Key "conf" -> Map of configuration properties
     */
    private Map<String, Object> vectorStoreConfig;

    /**
     * Object storage configuration for attachments/large objects.
     * Key "type" -> String (e.g. "obs", "s3")
     * Key "conf" -> Map of configuration properties
     */
    private Map<String, Object> objectStorageConfig;

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
