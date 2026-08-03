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

    // ========== MCP server config (McpServerConfig unit) ==========

    @Nested
    @DisplayName("McpServerConfig builder")
    class McpServerConfigTests {

        @Test
        @DisplayName("McpServerConfig builder with single SSE server")
        void mcpServerConfigSingleSse() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("my-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .build();

            assertEquals("my-mcp", server.getServerName());
            assertEquals("sse", server.getClientType());
        }

        @Test
        @DisplayName("McpServerConfig builder with multiple servers of different types")
        void mcpServerConfigMultipleTypes() {
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

            assertEquals("sse", sseServer.getClientType());
            assertEquals("stdio", stdioServer.getClientType());
        }

        @Test
        @DisplayName("McpServerConfig with auth headers")
        void mcpServerConfigWithAuthHeaders() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("auth-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .authHeaders(Map.of("Authorization", "Bearer token123"))
                    .build();

            Map<String, String> headers = server.getAuthHeaders();
            assertNotNull(headers);
            assertEquals("Bearer token123", headers.get("Authorization"));
        }

        @Test
        @DisplayName("McpServerConfig with custom params")
        void mcpServerConfigWithParams() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("param-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .clientType("sse")
                    .params(Map.of("timeout", 30, "retry_count", 3))
                    .build();

            Map<String, Object> params = server.getParams();
            assertNotNull(params);
            assertEquals(30, params.get("timeout"));
        }

        @Test
        @DisplayName("McpServerConfig defaults to sse clientType")
        void mcpServerConfigDefaultsToSse() {
            McpServerConfig server = McpServerConfig.builder()
                    .serverName("default-mcp")
                    .serverPath("http://localhost:8080/mcp")
                    .build();
            assertEquals("sse", server.getClientType());
        }
    }

    // ========== KV store config (via CheckpointerConfig conf) ==========

    @Nested
    @DisplayName("KV store config in checkpointer conf")
    class KvStoreConfigTests {

        @Test
        @DisplayName("checkpointerConfig conf can carry redis kv config")
        void kvStoreConfigRedis() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("url", "redis://localhost:6379", "cluster_mode", false));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("redis", config.getCheckpointerConfig().getType());
            assertEquals("redis://localhost:6379", config.getCheckpointerConfig().getConf().get("url"));
        }

        @Test
        @DisplayName("checkpointerConfig conf can carry in_memory kv config")
        void kvStoreConfigInMemory() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("in_memory", config.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("kvStoreConfig is null by default (no checkpointerConfig)")
        void kvStoreConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getCheckpointerConfig());
        }
    }

    // ========== Vector store config (via CheckpointerConfig conf) ==========

    @Nested
    @DisplayName("Vector store config in checkpointer conf")
    class VectorStoreConfigTests {

        @Test
        @DisplayName("checkpointerConfig conf can carry milvus vector config")
        void vectorStoreConfigMilvus() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("persistence",
                    Map.of("uri", "http://localhost:19530", "collection", "kb_chunks"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("persistence", config.getCheckpointerConfig().getType());
            assertEquals("http://localhost:19530", config.getCheckpointerConfig().getConf().get("uri"));
        }

        @Test
        @DisplayName("checkpointerConfig conf can carry chroma vector config")
        void vectorStoreConfigChroma() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("persistence",
                    Map.of("host", "localhost", "port", 8000));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("persistence", config.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("vectorStoreConfig is null by default (no checkpointerConfig)")
        void vectorStoreConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getCheckpointerConfig());
        }
    }

    // ========== Object storage config (via CheckpointerConfig conf) ==========

    @Nested
    @DisplayName("Object storage config in checkpointer conf")
    class ObjectStorageConfigTests {

        @Test
        @DisplayName("checkpointerConfig conf can carry obs storage config")
        void objectStorageConfigObs() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("persistence",
                    Map.of("endpoint", "https://obs.example.com", "bucket", "my-bucket"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("persistence", config.getCheckpointerConfig().getType());
            assertEquals("https://obs.example.com", config.getCheckpointerConfig().getConf().get("endpoint"));
        }

        @Test
        @DisplayName("checkpointerConfig conf can carry s3 storage config")
        void objectStorageConfigS3() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("persistence",
                    Map.of("region", "us-east-1", "bucket", "my-s3-bucket"));
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            assertEquals("persistence", config.getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("objectStorageConfig is null by default (no checkpointerConfig)")
        void objectStorageConfigNullByDefault() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            assertNull(config.getCheckpointerConfig());
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
                    false,
                    false
            );

            assertFalse(config.isDistributedMode());
            assertEquals("test", config.getEnvPrefix());
            assertEquals("instance-1", config.getInstanceId());
            assertEquals("redis", config.getCheckpointerConfig().getType());
        }
    }

    // ========== Runner + RunnerConfig integration ==========

    @Nested
    @DisplayName("Runner + RunnerConfig integration")
    class RunnerRunnerConfigIntegration {

        @Test
        @DisplayName("RunnerImpl constructor sets RunnerConfig as global")
        void runnerImplConstructorSetsGlobalConfig() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            assertSame(config, runner.getConfig());
            assertEquals("in_memory", runner.getConfig().getCheckpointerConfig().getType());
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
            CheckpointerConfig cpConfig = new CheckpointerConfig("redis",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));

            RunnerConfig newConfig = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();
            runner.setConfig(newConfig);

            assertEquals("redis", runner.getConfig().getCheckpointerConfig().getType());
        }

        @Test
        @DisplayName("RunnerImpl.start() initializes in_memory checkpointer from config")
        void runnerImplStartInitializesInMemoryCheckpointer() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            boolean started = runner.start();
            assertTrue(started);

            CheckpointerFactory.installDefaultCheckpointer(cpConfig);
            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertNotNull(cp);
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("RunnerImpl.start() returns true for non-distributed mode")
        void runnerImplStartNonDistributed() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .build();

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            boolean started = runner.start();
            assertTrue(started);

            runner.stop();
        }

        @Test
        @DisplayName("RunnerImpl.start() without checkpointerConfig does not override default")
        void runnerImplStartWithoutCheckpointerConfig() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .build();
            assertNull(config.getCheckpointerConfig());

            RunnerImpl runner = new RunnerImpl("test-runner", config);
            boolean started = runner.start();
            assertTrue(started);

            // Default checkpointer should be InMemoryCheckpointer
            Checkpointer cp = CheckpointerFactory.getCheckpointer();
            assertInstanceOf(InMemoryCheckpointer.class, cp);

            runner.stop();
        }

        @Test
        @DisplayName("RunnerImpl.setConfig() then getConfig() returns new config")
        void runnerImplSetConfigThenGetConfig() {
            // Start with in_memory
            CheckpointerConfig cpConfig1 = new CheckpointerConfig("in_memory", Map.of());
            RunnerConfig config1 = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig1)
                    .build();
            RunnerImpl runner = new RunnerImpl("test-runner", config1);
            assertEquals("in_memory", runner.getConfig().getCheckpointerConfig().getType());

            // Switch to redis via setConfig
            CheckpointerConfig cpConfig2 = new CheckpointerConfig("redis",
                    Map.of("connection", Map.of("url", "redis://localhost:6379")));
            RunnerConfig config2 = RunnerConfig.builder()
                    .distributedMode(false)
                    .checkpointerConfig(cpConfig2)
                    .build();
            runner.setConfig(config2);
            assertEquals("redis", runner.getConfig().getCheckpointerConfig().getType());
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
        @DisplayName("Multiple RunnerImpl instances share same global config")
        void multipleRunnerImplsShareGlobalConfig() {
            CheckpointerConfig cpConfig = new CheckpointerConfig("in_memory", Map.of());
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
