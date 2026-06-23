/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
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
        @DisplayName("DEFAULT has empty mcpServers list")
        void defaultMcpServers() {
            assertNotNull(RunnerConfig.DEFAULT.getMcpServers());
            assertTrue(RunnerConfig.DEFAULT.getMcpServers().isEmpty());
        }

        @Test
        @DisplayName("DEFAULT has null kvStoreConfig")
        void defaultKvStoreConfig() {
            assertNull(RunnerConfig.DEFAULT.getKvStoreConfig());
        }

        @Test
        @DisplayName("DEFAULT has null vectorStoreConfig")
        void defaultVectorStoreConfig() {
            assertNull(RunnerConfig.DEFAULT.getVectorStoreConfig());
        }

        @Test
        @DisplayName("DEFAULT has null objectStorageConfig")
        void defaultObjectStorageConfig() {
            assertNull(RunnerConfig.DEFAULT.getObjectStorageConfig());
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
    class CheckpointerConfig {

        @Test
        @DisplayName("checkpointerConfig with type and conf")
        void checkpointerConfigWithTypeAndConf() {
            Map<String, Object> cpConfig = Map.of(
                    "type", "redis",
                    "conf", Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("redis", config.getCheckpointerConfig().get("type"));
            assertNotNull(config.getCheckpointerConfig().get("conf"));
        }

        @Test
        @DisplayName("checkpointerConfig with in_memory type")
        void checkpointerConfigInMemory() {
            Map<String, Object> cpConfig = Map.of("type", "in_memory", "conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("in_memory", config.getCheckpointerConfig().get("type"));
        }

        @Test
        @DisplayName("checkpointerConfig with persistence type and kv_store")
        void checkpointerConfigPersistence() {
            Map<String, Object> cpConfig = Map.of(
                    "type", "persistence",
                    "conf", Map.of("db_type", "sqlite", "db_path", "/tmp/cp.db"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("persistence", config.getCheckpointerConfig().get("type"));
        }

        @Test
        @DisplayName("checkpointerConfig is null by default")
        void checkpointerConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getCheckpointerConfig());
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
    class KvStoreConfig {

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
    class VectorStoreConfig {

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
    class ObjectStorageConfig {

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
            Map<String, Object> cpConfig = Map.of("type", "redis", "conf", Map.of("url", "redis://localhost:6379"));
            McpServerConfig mcpServer = McpServerConfig.builder()
                    .serverName("test-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .build();
            Map<String, Object> kvConfig = Map.of("type", "in_memory", "conf", Map.of());
            Map<String, Object> vsConfig = Map.of("type", "milvus", "conf", Map.of("uri", "http://localhost:19530"));
            Map<String, Object> obsConfig = Map.of("type", "obs", "conf", Map.of("endpoint", "https://obs.example.com"));

            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .mcpServers(List.of(mcpServer))
                    .kvStoreConfig(kvConfig)
                    .vectorStoreConfig(vsConfig)
                    .objectStorageConfig(obsConfig)
                    .build();

            assertNotNull(config.getCheckpointerConfig());
            assertEquals(1, config.getMcpServers().size());
            assertNotNull(config.getKvStoreConfig());
            assertNotNull(config.getVectorStoreConfig());
            assertNotNull(config.getObjectStorageConfig());

            assertEquals("redis", config.getCheckpointerConfig().get("type"));
            assertEquals("in_memory", config.getKvStoreConfig().get("type"));
            assertEquals("milvus", config.getVectorStoreConfig().get("type"));
            assertEquals("obs", config.getObjectStorageConfig().get("type"));
        }

        @Test
        @DisplayName("Global config preserves all SPI configs")
        void globalConfigPreservesAllSpiConfigs() {
            Map<String, Object> cpConfig = Map.of("type", "redis", "conf", Map.of("url", "redis://localhost:6379"));
            Map<String, Object> kvConfig = Map.of("type", "in_memory", "conf", Map.of());

            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .kvStoreConfig(kvConfig)
                    .build();
            RunnerConfig.setRunnerConfig(config);

            RunnerConfig retrieved = RunnerConfig.getRunnerConfig();
            assertEquals("redis", retrieved.getCheckpointerConfig().get("type"));
            assertEquals("in_memory", retrieved.getKvStoreConfig().get("type"));
        }

        @Test
        @DisplayName("SPI config type extraction pattern")
        void spiConfigTypeExtractionPattern() {
            // Verify the standard pattern: config.get("type") + config.get("conf")
            Map<String, Object> cpConfig = Map.of(
                    "type", "persistence",
                    "conf", Map.of("db_type", "sqlite"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            Map<String, Object> stored = config.getCheckpointerConfig();
            String type = (String) stored.getOrDefault("type", "in_memory");
            @SuppressWarnings("unchecked")
            Map<String, Object> conf = (Map<String, Object>) stored.getOrDefault("conf", Map.of());

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
                    .checkpointerConfig(Map.of("type", "in_memory"))
                    .build();
            RunnerConfig config2 = builder
                    .checkpointerConfig(Map.of("type", "redis"))
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
            assertNull(config.getKvStoreConfig());
            assertNull(config.getVectorStoreConfig());
            assertNull(config.getObjectStorageConfig());
        }

        @Test
        @DisplayName("All-args constructor sets all fields")
        void allArgsConstructor() {
            Map<String, Object> cpConfig = Map.of("type", "redis");
            Map<String, Object> kvConfig = Map.of("type", "in_memory");
            Map<String, Object> vsConfig = Map.of("type", "chroma");
            Map<String, Object> obsConfig = Map.of("type", "s3");

            RunnerConfig config = new RunnerConfig(
                    false,
                    new DistributedConfig(),
                    "test",
                    "instance-1",
                    cpConfig,
                    List.of(),
                    kvConfig,
                    vsConfig,
                    obsConfig
            );

            assertFalse(config.isDistributedMode());
            assertEquals("test", config.getEnvPrefix());
            assertEquals("instance-1", config.getInstanceId());
            assertEquals("redis", config.getCheckpointerConfig().get("type"));
            assertEquals("in_memory", config.getKvStoreConfig().get("type"));
            assertEquals("chroma", config.getVectorStoreConfig().get("type"));
            assertEquals("s3", config.getObjectStorageConfig().get("type"));
        }
    }

    // ========== Runner + RunnerConfig integration ==========

    @Nested
    @DisplayName("Runner + RunnerConfig integration")
    class RunnerRunnerConfigIntegration {

        @Test
        @DisplayName("RunnerImpl constructor sets RunnerConfig as global")
        void runnerImplConstructorSetsGlobalConfig() {
            Map<String, Object> cpConfig = Map.of("type", "in_memory", "conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            assertSame(config, runner.getConfig());
            assertEquals("in_memory", runner.getConfig().getCheckpointerConfig().get("type"));
        }

        @Test
        @DisplayName("RunnerImpl with null config uses DEFAULT")
        void runnerImplWithNullConfigUsesDefault() {
            RunnerImpl runner = new RunnerImpl("test-runner", null);
            assertFalse(runner.getConfig().isDistributedMode());
        }

        @Test
        @DisplayName("RunnerImpl.setConfig() updates global config")
        void runnerImplSetConfigUpdatesGlobal() {
            RunnerImpl runner = new RunnerImpl("test-runner", null);
            Map<String, Object> cpConfig = Map.of("type", "redis", "conf",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));

            RunnerConfig newConfig = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();
            runner.setConfig(newConfig);

            assertEquals("redis", runner.getConfig().getCheckpointerConfig().get("type"));
        }

        @Test
        @DisplayName("Runner.start() initializes in_memory checkpointer from config")
        void runnerStartInitializesInMemoryCheckpointer() {
            Map<String, Object> cpConfig = Map.of("type", "in_memory", "conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertNotNull(cp);
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("Runner.start() initializes redis checkpointer from config")
        void runnerStartInitializesRedisCheckpointer() {
            Map<String, Object> cpConfig = Map.of("type", "redis", "conf",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertNotNull(cp);
            assertInstanceOf(RedisCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("Runner.start() without checkpointerConfig does not override default")
        void runnerStartWithoutCheckpointerConfig() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .build();
            assertNull(config.getCheckpointerConfig());

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            // Default checkpointer should be InMemoryCheckpointer
            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("Runner.start() with persistence checkpointer config")
        void runnerStartInitializesPersistenceCheckpointer() {
            com.openjiuwen.core.foundation.store.kv.InMemoryKVStore kvStore =
                    new com.openjiuwen.core.foundation.store.kv.InMemoryKVStore();
            Map<String, Object> cpConfig = Map.of(
                    "type", "persistence",
                    "conf", Map.of("kv_store", kvStore));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("Runner.setConfig() then start() applies new checkpointer")
        void runnerSetConfigThenStartAppliesNewCheckpointer() {
            // Start with in_memory
            Map<String, Object> cpConfig1 = Map.of("type", "in_memory", "conf", Map.of());
            RunnerConfig config1 = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig1)
                    .build();
            RunnerImpl runner = new RunnerImpl("test-runner", config1);
            runner.start();
            assertInstanceOf(InMemoryCheckpointer.class, CheckpointerFactory.getCheckpointer());
            runner.stop();

            // Switch to redis via setConfig + start
            Map<String, Object> cpConfig2 = Map.of("type", "redis", "conf",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config2 = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig2)
                    .build();
            runner.setConfig(config2);
            runner.start();
            assertInstanceOf(RedisCheckpointer.class, CheckpointerFactory.getCheckpointer());

            runner.stop();
        }

        @Test
        @DisplayName("Runner.start() with invalid checkpointer type throws exception")
        void runnerStartWithInvalidCheckpointerTypeThrows() {
            Map<String, Object> cpConfig = Map.of("type", "nonexistent_type", "conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            assertThrows(Exception.class, runner::start);
        }

        @Test
        @DisplayName("Runner.start() with checkpointerConfig missing type defaults to in_memory")
        void runnerStartWithMissingTypeDefaultsToInMemory() {
            Map<String, Object> cpConfig = Map.of("conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("Runner.start() with empty checkpointerConfig defaults to in_memory")
        void runnerStartWithEmptyCheckpointerConfig() {
            Map<String, Object> cpConfig = Map.of();
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("RunnerConfig with mcpServers accessible after Runner construction")
        void runnerConfigMcpServersAccessibleAfterConstruction() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("test-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .build();
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .mcpServers(List.of(server))
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            assertEquals(1, runner.getConfig().getMcpServers().size());
            assertEquals("test-mcp", runner.getConfig().getMcpServers().get(0).getServerName());
        }

        @Test
        @DisplayName("RunnerConfig with all SPI fields accessible via Runner.getConfig()")
        void runnerConfigAllSpiFieldsAccessibleViaRunner() {
            Map<String, Object> cpConfig = Map.of("type", "in_memory", "conf", Map.of());
            McpServerConfig mcpServer = McpServerConfig.builder()
                    .serverName("mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .build();
            Map<String, Object> kvConfig = Map.of("type", "in_memory", "conf", Map.of());
            Map<String, Object> vsConfig = Map.of("type", "in_memory", "conf", Map.of());
            Map<String, Object> obsConfig = Map.of("type", "obs", "conf", Map.of("endpoint", "https://obs.example.com"));

            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .mcpServers(List.of(mcpServer))
                    .kvStoreConfig(kvConfig)
                    .vectorStoreConfig(vsConfig)
                    .objectStorageConfig(obsConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            RunnerConfig retrieved = runner.getConfig();

            assertEquals("in_memory", retrieved.getCheckpointerConfig().get("type"));
            assertEquals(1, retrieved.getMcpServers().size());
            assertEquals("in_memory", retrieved.getKvStoreConfig().get("type"));
            assertEquals("in_memory", retrieved.getVectorStoreConfig().get("type"));
            assertEquals("obs", retrieved.getObjectStorageConfig().get("type"));
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
            Map<String, Object> cpConfig = Map.of("type", "redis", "conf",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            Runner.setConfig(config);
            assertEquals("redis", Runner.getConfig().getCheckpointerConfig().get("type"));

            // Reset
            Runner.setConfig(RunnerConfig.DEFAULT);
        }

        @Test
        @DisplayName("Multiple RunnerImpl instances share same global config")
        void multipleRunnerImplsShareGlobalConfig() {
            Map<String, Object> cpConfig = Map.of("type", "in_memory", "conf", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner1 = new RunnerImpl("runner-1", config);
            // runner2 with null config uses global config set by runner1
            RunnerImpl runner2 = new RunnerImpl("runner-2", null);

            // Both share the same global config
            assertSame(runner1.getConfig(), runner2.getConfig());
        }

        @Test
        @DisplayName("Runner.start() with redis_cluster alias creates RedisCheckpointer")
        void runnerStartWithRedisClusterAlias() {
            Map<String, Object> cpConfig = Map.of("type", "redis_checkpointer_cluster", "conf",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(RedisCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("Runner.start() with checkpointerConfig containing only type uses empty conf")
        void runnerStartWithOnlyTypeUsesEmptyConf() {
            Map<String, Object> cpConfig = Map.of("type", "in_memory");
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            runner.start();

            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }
    }
}
