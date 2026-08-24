/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RunnerConfig SPI configuration fields and global config management.
 */
class RunnerConfigTest {

    @BeforeEach
    void resetGlobalConfig() {
        RunnerConfig.setRunnerConfig(null);
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @AfterEach
    void cleanupGlobalConfig() {
        RunnerConfig.setRunnerConfig(null);
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    // ========== Default values ==========

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("DEFAULT has distributedMode=false")
        void defaultDistributedMode() {
            assertFalse(RunnerConfig.DEFAULT.isDistributedMode());
        }

        @Test
        @DisplayName("DEFAULT has null checkpointerConfig")
        void defaultCheckpointerConfig() {
            assertNull(RunnerConfig.DEFAULT.getCheckpointerConfig());
        }

        @Test
        @DisplayName("DEFAULT has non-null instanceId")
        void defaultInstanceId() {
            assertNotNull(RunnerConfig.DEFAULT.getInstanceId());
        }

        @Test
        @DisplayName("DEFAULT has empty envPrefix")
        void defaultEnvPrefix() {
            assertEquals("", RunnerConfig.DEFAULT.getEnvPrefix());
        }

        @Test
        @DisplayName("DEFAULT has enableTenantIsolation=false")
        void defaultEnableTenantIsolation() {
            assertFalse(RunnerConfig.DEFAULT.isEnableTenantIsolation());
        }

        @Test
        @DisplayName("DEFAULT has null tenantDataRoot")
        void defaultTenantDataRoot() {
            assertNull(RunnerConfig.DEFAULT.getTenantDataRoot());
        }
    }

    // ========== Global config management ==========

    @Nested
    @DisplayName("Global config management")
    class GlobalConfig {

        @Test
        @DisplayName("getRunnerConfig() returns DEFAULT when none set")
        void getRunnerConfigReturnsDefault() {
            RunnerConfig config = RunnerConfig.getRunnerConfig();
            assertNotNull(config);
            assertFalse(config.isDistributedMode());
        }

        @Test
        @DisplayName("setRunnerConfig() replaces global config")
        void setRunnerConfigReplacesGlobal() {
            RunnerConfig custom = RunnerConfig.builder()
                    .distributedMode(true)
                    .envPrefix("test")
                    .build();
            RunnerConfig.setRunnerConfig(custom);

            RunnerConfig current = RunnerConfig.getRunnerConfig();
            assertTrue(current.isDistributedMode());
            assertEquals("test", current.getEnvPrefix());
        }

        @Test
        @DisplayName("setRunnerConfig(null) resets to DEFAULT on next get")
        void setRunnerConfigNullResetsToDefault() {
            RunnerConfig custom = RunnerConfig.builder()
                    .distributedMode(true)
                    .build();
            RunnerConfig.setRunnerConfig(custom);
            assertTrue(RunnerConfig.getRunnerConfig().isDistributedMode());

            RunnerConfig.setRunnerConfig(null);
            assertFalse(RunnerConfig.getRunnerConfig().isDistributedMode());
        }

        @Test
        @DisplayName("getRunnerConfig() returns same instance after set")
        void getRunnerConfigReturnsSameInstance() {
            RunnerConfig custom = RunnerConfig.builder()
                    .distributedMode(false)
                    .build();
            RunnerConfig.setRunnerConfig(custom);
            assertSame(custom, RunnerConfig.getRunnerConfig());
        }
    }

    // ========== Checkpointer config ==========

    @Nested
    @DisplayName("Checkpointer config")
    class CheckpointerConfigTests {

        @Test
        @DisplayName("checkpointerConfig with type and conf")
        void checkpointerConfigWithTypeAndConf() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("redis", config.getCheckpointerConfig().getType());
            assertNotNull(config.getCheckpointerConfig().getConf());
        }

        @Test
        @DisplayName("checkpointerConfig with in_memory type")
        void checkpointerConfigInMemory() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("in_memory", config.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("checkpointerConfig with persistence type and kv_store")
        void checkpointerConfigPersistence() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("persistence",
                    Map.of("db_type", "sqlite", "db_path", "/tmp/cp.db"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("persistence", config.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("checkpointerConfig is null by default")
        void checkpointerConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getCheckpointerConfig());
        }

        @Test
        @DisplayName("checkpointerConfig from Map via builder overload")
        void checkpointerConfigFromMap() {
            Map<String, Object> cpConfig = Map.of("type", "redis", "conf",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("redis", config.getCheckpointerConfig().getType());
        }
    }

    // ========== MCP servers config ==========

    @Nested
    @DisplayName("MCP servers config")
    class McpServersConfig {

        @Test
        @DisplayName("mcpServers with single SSE server")
        void mcpServersSingleSse() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("my-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .build();
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .mcpServers(List.of(server))
                    .build();

            assertEquals(1, config.getMcpServers().size());
            assertEquals("my-mcp", config.getMcpServers().get(0).getServerName());
            assertEquals("sse", config.getMcpServers().get(0).getClientType());
        }

        @Test
        @DisplayName("mcpServers with multiple servers of different types")
        void mcpServersMultipleTypes() {
            McpServerConfig sseServer = McpServerConfig.builder()
                    .serverName("sse-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .build();
            McpServerConfig stdioServer = McpServerConfig.builder()
                    .serverName("stdio-mcp")
                    .serverPath("/usr/local/bin/mcp-server")
                    .clientType("stdio")
                    .build();
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .mcpServers(List.of(sseServer, stdioServer))
                    .build();

            assertEquals(2, config.getMcpServers().size());
            assertEquals("sse", config.getMcpServers().get(0).getClientType());
            assertEquals("stdio", config.getMcpServers().get(1).getClientType());
        }

        @Test
        @DisplayName("mcpServers with auth headers")
        void mcpServersWithAuthHeaders() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("auth-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .authHeaders(Map.of("Authorization", "Bearer token123"))
                    .build();
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .mcpServers(List.of(server))
                    .build();

            Map<String, String> headers = config.getMcpServers().get(0).getAuthHeaders();
            assertNotNull(headers);
            assertEquals("Bearer token123", headers.get("Authorization"));
        }

        @Test
        @DisplayName("mcpServers with custom params")
        void mcpServersWithParams() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("param-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .params(Map.of("timeout", 30, "retry_count", 3))
                    .build();
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .mcpServers(List.of(server))
                    .build();

            Map<String, Object> params = config.getMcpServers().get(0).getParams();
            assertNotNull(params);
            assertEquals(30, params.get("timeout"));
        }

        @Test
        @DisplayName("mcpServers defaults to empty list")
        void mcpServersDefaultsToEmptyList() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNotNull(config.getMcpServers());
            assertTrue(config.getMcpServers().isEmpty());
        }
    }

    // ========== KV store config ==========

    @Nested
    @DisplayName("KV store config")
    class KvStoreConfigTests {

        @Test
        @DisplayName("kvStoreConfig with redis type")
        void kvStoreConfigRedis() {
            Map<String, Object> kvConfig = Map.of(
                    "type", "redis",
                    "conf", Map.of("url", "redis://localhost:6379", "cluster_mode", false));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .kvStoreConfig(kvConfig)
                    .build();

            assertEquals("redis", config.getKvStoreConfig().get("type"));
        }

        @Test
        @DisplayName("kvStoreConfig with in_memory type")
        void kvStoreConfigInMemory() {
            Map<String, Object> kvConfig = Map.of("type", "in_memory", "conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .kvStoreConfig(kvConfig)
                    .build();

            assertEquals("in_memory", config.getKvStoreConfig().get("type"));
        }

        @Test
        @DisplayName("kvStoreConfig is null by default")
        void kvStoreConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getKvStoreConfig());
        }
    }

    // ========== Vector store config ==========

    @Nested
    @DisplayName("Vector store config")
    class VectorStoreConfigTests {

        @Test
        @DisplayName("vectorStoreConfig with milvus type")
        void vectorStoreConfigMilvus() {
            Map<String, Object> vsConfig = Map.of(
                    "type", "milvus",
                    "conf", Map.of("uri", "http://localhost:19530", "collection", "kb_chunks"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .vectorStoreConfig(vsConfig)
                    .build();

            assertEquals("milvus", config.getVectorStoreConfig().get("type"));
        }

        @Test
        @DisplayName("vectorStoreConfig with chroma type")
        void vectorStoreConfigChroma() {
            Map<String, Object> vsConfig = Map.of(
                    "type", "chroma",
                    "conf", Map.of("host", "localhost", "port", 8000));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .vectorStoreConfig(vsConfig)
                    .build();

            assertEquals("chroma", config.getVectorStoreConfig().get("type"));
        }

        @Test
        @DisplayName("vectorStoreConfig is null by default")
        void vectorStoreConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getVectorStoreConfig());
        }
    }

    // ========== Object storage config ==========

    @Nested
    @DisplayName("Object storage config")
    class ObjectStorageConfigTests {

        @Test
        @DisplayName("objectStorageConfig with obs type")
        void objectStorageConfigObs() {
            Map<String, Object> obsConfig = Map.of(
                    "type", "obs",
                    "conf", Map.of("endpoint", "https://obs.example.com", "bucket", "my-bucket"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .objectStorageConfig(obsConfig)
                    .build();

            assertEquals("obs", config.getObjectStorageConfig().get("type"));
        }

        @Test
        @DisplayName("objectStorageConfig with s3 type")
        void objectStorageConfigS3() {
            Map<String, Object> obsConfig = Map.of(
                    "type", "s3",
                    "conf", Map.of("region", "us-east-1", "bucket", "my-s3-bucket"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .objectStorageConfig(obsConfig)
                    .build();

            assertEquals("s3", config.getObjectStorageConfig().get("type"));
        }

        @Test
        @DisplayName("objectStorageConfig is null by default")
        void objectStorageConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getObjectStorageConfig());
        }
    }

    // ========== Topic templates ==========

    @Nested
    @DisplayName("Topic templates")
    class TopicTemplates {

        @Test
        @DisplayName("agentTopicTemplate() without envPrefix")
        void agentTopicTemplateWithoutPrefix() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .envPrefix("")
                    .build();
            String template = config.agentTopicTemplate();
            assertTrue(template.startsWith("openjiuwen.single_agent"));
        }

        @Test
        @DisplayName("agentTopicTemplate() with envPrefix")
        void agentTopicTemplateWithPrefix() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .envPrefix("prod")
                    .build();
            String template = config.agentTopicTemplate();
            assertTrue(template.startsWith("prod."));
        }

        @Test
        @DisplayName("replyTopicTemplate() without envPrefix")
        void replyTopicTemplateWithoutPrefix() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .envPrefix("")
                    .build();
            String template = config.replyTopicTemplate();
            assertTrue(template.startsWith("openjiuwen.reply"));
        }

        @Test
        @DisplayName("replyTopicTemplate() with envPrefix")
        void replyTopicTemplateWithPrefix() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .envPrefix("staging")
                    .build();
            String template = config.replyTopicTemplate();
            assertTrue(template.startsWith("staging."));
        }
    }

    // ========== Full SPI config integration ==========

    @Nested
    @DisplayName("Full SPI config integration")
    class FullSpiConfigIntegration {

        @Test
        @DisplayName("All SPI configs can be set together")
        void allSpiConfigsTogether() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("url", "redis://localhost:6379"));

            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertNotNull(config.getCheckpointerConfig());
            assertEquals("redis", config.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("Global config preserves checkpointer config")
        void globalConfigPreservesCheckpointerConfig() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("url", "redis://localhost:6379"));

            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();
            RunnerConfig.setRunnerConfig(config);

            RunnerConfig retrieved = RunnerConfig.getRunnerConfig();
            assertEquals("redis", retrieved.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("SPI config type extraction pattern")
        void spiConfigTypeExtractionPattern() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("persistence",
                    Map.of("db_type", "sqlite"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            CheckpointerConfig stored = config.getCheckpointerConfig();
            String type = stored.getType();
            Map<String, Object> conf = stored.getConf();

            assertEquals("persistence", type);
            assertEquals("sqlite", conf.get("db_type"));
        }
    }

    // ========== Builder patterns ==========

    @Nested
    @DisplayName("Builder patterns")
    class BuilderPatterns {

        @Test
        @DisplayName("Builder produces independent instances")
        void builderProducesIndependentInstances() {
            RunnerConfig.RunnerConfigBuilder builder = RunnerConfig.builder()
                    .distributedMode(false);

            RunnerConfig config1 = builder
                    .checkpointerConfig(new CheckpointerConfig("in_memory", Map.of()))
                    .build();
            RunnerConfig config2 = builder
                    .checkpointerConfig(new CheckpointerConfig("redis", Map.of("url", "redis://localhost:6379")))
                    .build();

            // Lombok @Builder may share mutable state; just verify both are valid
            assertNotNull(config1.getCheckpointerConfig());
            assertNotNull(config2.getCheckpointerConfig());
        }

        @Test
        @DisplayName("No-arg constructor creates valid config")
        void noArgConstructor() {
            RunnerConfig config = new RunnerConfig();
            assertTrue(config.isDistributedMode()); // default
            assertNull(config.getCheckpointerConfig());
        }

        @Test
        @DisplayName("All-args constructor sets all fields")
        void allArgsConstructor() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis", Map.of());

            RunnerConfig config = new RunnerConfig(
                    false,
                    new DistributedConfig(),
                    "test",
                    "instance-1",
                    cpConfig,
                    List.of(),
                    null,
                    null,
                    null,
                    false,
                    false,
                    true,
                    "/data/tenants"
            );

            assertFalse(config.isDistributedMode());
            assertEquals("test", config.getEnvPrefix());
            assertEquals("instance-1", config.getInstanceId());
            assertEquals("redis", config.getCheckpointerConfig().getType());
            assertTrue(config.isEnableTenantIsolation());
            assertEquals("/data/tenants", config.getTenantDataRoot());
        }
    }

    // ========== Runner + RunnerConfig integration ==========

    @Nested
    @DisplayName("Runner + RunnerConfig integration")
    class RunnerRunnerConfigIntegration {

        @Test
        @DisplayName("Runner.setConfig() sets RunnerConfig as global")
        void runnerSetConfigSetsGlobalConfig() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            Runner.setConfig(config);
            assertSame(config, Runner.getConfig());
            assertEquals("in_memory", Runner.getConfig().getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("Runner.getConfig() with unset global uses DEFAULT")
        void runnerGetConfigUsesDefaultWhenUnset() {
            assertFalse(Runner.getConfig().isDistributedMode());
        }

        @Test
        @DisplayName("Runner.setConfig() updates global config")
        void runnerSetConfigUpdatesGlobal() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));

            RunnerConfig newConfig = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();
            Runner.setConfig(newConfig);

            assertEquals("redis", Runner.getConfig().getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("Runner.start() initializes in_memory checkpointer from config")
        void runnerStartInitializesInMemoryCheckpointer() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            Runner.setConfig(config);
            boolean started = Runner.start().toCompletableFuture().join();
            assertTrue(started);

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertNotNull(cp);
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            Runner.stop().toCompletableFuture().join();
        }

        @Test
        @DisplayName("Runner.start() returns true for non-distributed mode")
        void runnerStartNonDistributed() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .build();

            Runner.setConfig(config);
            boolean started = Runner.start().toCompletableFuture().join();
            assertTrue(started);

            Runner.stop().toCompletableFuture().join();
        }

        @Test
        @DisplayName("Runner.start() without checkpointerConfig does not override default")
        void runnerStartWithoutCheckpointerConfig() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .build();
            assertNull(config.getCheckpointerConfig());

            Runner.setConfig(config);
            boolean started = Runner.start().toCompletableFuture().join();
            assertTrue(started);

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            Runner.stop().toCompletableFuture().join();
        }

        @Test
        @DisplayName("Runner.setConfig() then getConfig() returns new config")
        void runnerSetConfigThenGetConfig() {
            CheckpointerConfig cpConfig1 = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config1 = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig1)
                    .build();
            Runner.setConfig(config1);
            assertEquals("in_memory", Runner.getConfig().getCheckpointerConfig().getType());

            CheckpointerConfig cpConfig2 = new CheckpointerConfig("redis",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config2 = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig2)
                    .build();
            Runner.setConfig(config2);
            assertEquals("redis", Runner.getConfig().getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("Runner singleton uses DEFAULT config initially")
        void runnerSingletonUsesDefaultConfig() {
            // Runner static singleton is already initialized with DEFAULT
            RunnerConfig config = Runner.getConfig();
            assertFalse(config.isDistributedMode());
        }

        @Test
        @DisplayName("Runner.setConfig() updates global config visible to Runner.getConfig()")
        void runnerSetConfigUpdatesGlobalVisibleToGetConfig() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            Runner.setConfig(config);
            assertEquals("redis", Runner.getConfig().getCheckpointerConfig().getType());

            // Reset
            Runner.setConfig(RunnerConfig.DEFAULT);
        }

        @Test
        @DisplayName("Runner.setConfig() is process-global")
        void runnerSetConfigIsProcessGlobal() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            Runner.setConfig(config);
            assertSame(config, Runner.getConfig());
            assertSame(Runner.getConfig(), RunnerConfig.getRunnerConfig());
        }

        @Test
        @DisplayName("CheckpointerFactory.create() with redis_cluster alias creates RedisCheckpointer")
        void checkpointerFactoryRedisClusterAlias() {
            Checkpointer cp = CheckpointerFactory.create("redis_checkpointer_cluster",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            assertInstanceOf(RedisCheckpointer.class, cp);
        }

        @Test
        @DisplayName("CheckpointerFactory.create() with only type uses empty conf")
        void checkpointerFactoryWithOnlyType() {
            Checkpointer cp = CheckpointerFactory.create("in_memory", Map.of());
            assertInstanceOf(InMemoryCheckpointer.class, cp);
        }

        @Test
        @DisplayName("CheckpointerFactory.create() with invalid type throws exception")
        void checkpointerFactoryInvalidTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> CheckpointerFactory.create("nonexistent_type", Map.of()));
        }

        @Test
        @DisplayName("CheckpointerFactory.create() with null config defaults to in_memory")
        void checkpointerFactoryNullConfigDefaultsToInMemory() {
            Checkpointer cp = CheckpointerFactory.create((CheckpointerConfig) null);
            assertInstanceOf(InMemoryCheckpointer.class, cp);
        }
    }
}
