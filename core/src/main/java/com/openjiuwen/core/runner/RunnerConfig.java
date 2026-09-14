/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runner global configuration.
 *
 * <p>Holds distributed mode settings, MCP server configurations, and SPI injection
 * fields for checkpointer, KV store, vector store, and object storage.</p>
 *
 * <p>Mirrors Python's {@code RunnerConfig} in
 * {@code openjiuwen/core/runner/runner_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunnerConfig {

    @Builder.Default
    @JsonProperty("distributed_mode")
    private boolean distributedMode = true;

    @Builder.Default
    @JsonProperty("distributed_config")
    private DistributedConfig distributedConfig = new DistributedConfig();

    @Builder.Default
    @JsonProperty("env_prefix")
    private String envPrefix = "";

    @Builder.Default
    @JsonProperty("instance_id")
    private String instanceId = UUID.randomUUID().toString();

    @JsonProperty("checkpointer_config")
    private CheckpointerConfig checkpointerConfig;

    /**
     * MCP server configurations for ToolManager initialization.
     * Service adapters register MCP client providers via
     * {@link com.openjiuwen.core.foundation.tool.mcp.McpClientFactory#register}
     * before Runner starts, then provide server configs here.
     */
    @Builder.Default
    @JsonProperty("mcp_servers")
    private List<McpServerConfig> mcpServers = new ArrayList<>();

    /**
     * KV store configuration for persistence.
     * Key "type" -&gt; String (e.g. "in_memory", "redis");
     * Key "conf" -&gt; Map of configuration properties.
     */
    @JsonProperty("kv_store_config")
    private Map<String, Object> kvStoreConfig;

    /**
     * Vector store configuration for RAG/retrieval.
     * Key "type" -&gt; String (e.g. "milvus", "chroma");
     * Key "conf" -&gt; Map of configuration properties.
     */
    @JsonProperty("vector_store_config")
    private Map<String, Object> vectorStoreConfig;

    /**
     * Object storage configuration for attachments/large objects.
     * Key "type" -&gt; String (e.g. "obs", "s3");
     * Key "conf" -&gt; Map of configuration properties.
     */
    @JsonProperty("object_storage_config")
    private Map<String, Object> objectStorageConfig;

    @Builder.Default
    @JsonProperty("enable_session_controller")
    private boolean enableSessionController = false;

    @Builder.Default
    @JsonProperty("enable_a2a")
    private boolean enableA2a = false;

    @Builder.Default
    @JsonProperty("enable_tenant_isolation")
    private boolean enableTenantIsolation = false;

    @JsonProperty("tenant_data_root")
    private String tenantDataRoot;

    public String agentTopicTemplate() {
        if (distributedConfig == null) {
            return "";
        }
        return distributedConfig.getAgentTopicTemplate(envPrefix);
    }

    public String replyTopicTemplate() {
        if (distributedConfig == null) {
            return "";
        }
        return distributedConfig.getReplyTopicTemplate(envPrefix);
    }

    public RunnerConfig copy() {
        return RunnerConfig.builder()
                .distributedMode(distributedMode)
                .distributedConfig(distributedConfig == null ? null : distributedConfig.copy())
                .envPrefix(envPrefix)
                .instanceId(instanceId)
                .checkpointerConfig(copyCheckpointerConfig(checkpointerConfig))
                .mcpServers(mcpServers == null ? new ArrayList<>() : new ArrayList<>(mcpServers))
                .kvStoreConfig(copyConfigMap(kvStoreConfig))
                .vectorStoreConfig(copyConfigMap(vectorStoreConfig))
                .objectStorageConfig(copyConfigMap(objectStorageConfig))
                .enableSessionController(enableSessionController)
                .enableA2a(enableA2a)
                .enableTenantIsolation(enableTenantIsolation)
                .tenantDataRoot(tenantDataRoot)
                .build();
    }

    @Override
    public String toString() {
        return "RunnerConfig(distributed_mode=" + distributedMode
                + ", distributed_config=" + distributedConfig
                + ", env_prefix='" + envPrefix + '\''
                + ", instance_id='" + instanceId + '\''
                + ", checkpointer_config=" + checkpointerConfig
                + ", mcp_servers=" + mcpServers
                + ", kv_store_config=" + kvStoreConfig
                + ", vector_store_config=" + vectorStoreConfig
                + ", object_storage_config=" + objectStorageConfig
                + ", enable_session_controller=" + enableSessionController
                + ", enable_a2a=" + enableA2a
                + ", enable_tenant_isolation=" + enableTenantIsolation
                + ", tenant_data_root='" + tenantDataRoot + '\''
                + ")";
    }

    public static final RunnerConfig DEFAULT_RUNNER_CONFIG = RunnerConfig.builder()
            .distributedMode(false)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(30.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    public static final RunnerConfig DEFAULT = DEFAULT_RUNNER_CONFIG;

    private static final AtomicReference<RunnerConfig> GLOBAL_CONFIG = new AtomicReference<>(null);

    public static void setRunnerConfig(RunnerConfig config) {
        GLOBAL_CONFIG.set(config);
    }

    public static RunnerConfig getRunnerConfig() {
        RunnerConfig config = GLOBAL_CONFIG.get();
        if (config == null) {
            GLOBAL_CONFIG.compareAndSet(null, DEFAULT_RUNNER_CONFIG.copy());
            return GLOBAL_CONFIG.get();
        }
        return config;
    }

    public static class RunnerConfigBuilder {
        public RunnerConfigBuilder checkpointerConfig(CheckpointerConfig checkpointerConfig) {
            this.checkpointerConfig = checkpointerConfig;
            return this;
        }

        public RunnerConfigBuilder checkpointerConfig(Map<String, ?> checkpointerConfig) {
            this.checkpointerConfig = CheckpointerConfig.fromMap(checkpointerConfig);
            return this;
        }
    }

    private static CheckpointerConfig copyCheckpointerConfig(CheckpointerConfig source) {
        if (source == null) {
            return null;
        }
        return new CheckpointerConfig(source.getType(), source.getConf());
    }

    private static Map<String, Object> copyConfigMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        return new LinkedHashMap<>(source);
    }
}
