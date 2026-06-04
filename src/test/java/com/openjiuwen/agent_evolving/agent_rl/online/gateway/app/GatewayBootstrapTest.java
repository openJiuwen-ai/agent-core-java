/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for GatewayBootstrap.
 * 
 * <p>Mirrors Python's {@code build_app_from_config} and environment bootstrap in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.app.bootstrap}.</p>
 */
class GatewayBootstrapTest {

    @Nested
    @DisplayName("buildConfigFromEnv tests")
    class BuildConfigFromEnvTests {

        @Test
        @DisplayName("Test build config with minimal required env")
        void testBuildConfigMinimal() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_PORT", "8080");
            
            GatewayConfig config = GatewayBootstrap.buildConfigFromEnv(env);
            
            assertEquals(8080, config.getPort());
            assertEquals("127.0.0.1", config.getHost());
            assertEquals("http://127.0.0.1:18000", config.getLlmUrl());
            assertEquals("INFO", config.getLogLevel());
        }

        @Test
        @DisplayName("Test build config with all env variables")
        void testBuildConfigFull() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_HOST", "192.168.1.1");
            env.put("GATEWAY_PORT", "9000");
            env.put("INFERENCE_URL", "http://inference.example.com");
            env.put("JUDGE_URL", "http://judge.example.com");
            env.put("MODEL_ID", "test-model");
            env.put("JUDGE_MODEL", "judge-model");
            env.put("REQUEST_TIMEOUT", "60.0");
            env.put("LLM_API_KEY", "llm-key");
            env.put("JUDGE_API_KEY", "judge-key");
            env.put("GATEWAY_API_KEY", "gateway-key");
            env.put("RECORD_DIR", "/tmp/records");
            env.put("LOG_LEVEL", "DEBUG");
            env.put("DUMP_TOKEN_IDS", "true");
            env.put("REDIS_URL", "redis://localhost");
            env.put("UPSTREAM_MAX_RETRIES", "3");
            env.put("UPSTREAM_RETRY_BACKOFF_SEC", "0.5");
            env.put("UPSTREAM_RETRY_MAX_BACKOFF_SEC", "5.0");
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "1");
            
            GatewayConfig config = GatewayBootstrap.buildConfigFromEnv(env);
            
            assertEquals("192.168.1.1", config.getHost());
            assertEquals(9000, config.getPort());
            assertEquals("http://inference.example.com", config.getLlmUrl());
            assertEquals("http://judge.example.com", config.getJudgeUrl());
            assertEquals("test-model", config.getModelId());
            assertEquals("judge-model", config.getJudgeModel());
            assertEquals(60.0, config.getRequestTimeout());
            assertEquals("llm-key", config.getLlmApiKey());
            assertEquals("judge-key", config.getJudgeApiKey());
            assertEquals("gateway-key", config.getGatewayApiKey());
            assertEquals("/tmp/records", config.getRecordDir());
            assertEquals("DEBUG", config.getLogLevel());
            assertTrue(config.isDumpTokenIds());
            assertEquals("redis://localhost", config.getRedisUrl());
            assertEquals(3, config.getUpstreamMaxRetries());
            assertEquals(0.5, config.getUpstreamRetryBackoffSec());
            assertEquals(5.0, config.getUpstreamRetryMaxBackoffSec());
            assertTrue(config.isDisableGatewayTrajectoryCollection());
        }

        @Test
        @DisplayName("Test GATEWAY_PORT is required")
        void testPortRequired() {
            Map<String, String> env = new HashMap<>();
            // No GATEWAY_PORT
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayBootstrap.buildConfigFromEnv(env)
            );
            assertTrue(ex.getMessage().contains("GATEWAY_PORT"));
        }

        @Test
        @DisplayName("Test INFERENCE_URL fallback to LLM_URL")
        void testInferenceUrlFallback() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_PORT", "8080");
            env.put("LLM_URL", "http://llm.example.com");
            // No INFERENCE_URL
            
            GatewayConfig config = GatewayBootstrap.buildConfigFromEnv(env);
            assertEquals("http://llm.example.com", config.getLlmUrl());
        }

        @Test
        @DisplayName("Test MODEL_ID fallback to SERVED_MODEL_NAME")
        void testModelIdFallback() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_PORT", "8080");
            env.put("SERVED_MODEL_NAME", "served-model");
            // No MODEL_ID
            
            GatewayConfig config = GatewayBootstrap.buildConfigFromEnv(env);
            assertEquals("served-model", config.getModelId());
        }

        @Test
        @DisplayName("Test JUDGE_URL defaults to inference URL")
        void testJudgeUrlDefaultsToInference() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_PORT", "8080");
            env.put("INFERENCE_URL", "http://inference.example.com");
            // No JUDGE_URL
            
            GatewayConfig config = GatewayBootstrap.buildConfigFromEnv(env);
            assertEquals("http://inference.example.com", config.getJudgeUrl());
        }

        @Test
        @DisplayName("Test DUMP_TOKEN_IDS boolean parsing")
        void testDumpTokenIdsBooleanParsing() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_PORT", "8080");
            
            // Test "true"
            env.put("DUMP_TOKEN_IDS", "true");
            assertTrue(GatewayBootstrap.buildConfigFromEnv(env).isDumpTokenIds());
            
            // Test "1"
            env.put("DUMP_TOKEN_IDS", "1");
            assertTrue(GatewayBootstrap.buildConfigFromEnv(env).isDumpTokenIds());
            
            // Test empty string
            env.put("DUMP_TOKEN_IDS", "");
            assertFalse(GatewayBootstrap.buildConfigFromEnv(env).isDumpTokenIds());
            
            // Test "false"
            env.put("DUMP_TOKEN_IDS", "false");
            assertFalse(GatewayBootstrap.buildConfigFromEnv(env).isDumpTokenIds());
        }

        @Test
        @DisplayName("Test DISABLE_GATEWAY_TRAJECTORY_COLLECTION boolean parsing")
        void testDisableTrajectoryCollectionBooleanParsing() {
            Map<String, String> env = new HashMap<>();
            env.put("GATEWAY_PORT", "8080");
            
            // Test "true"
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "true");
            assertTrue(GatewayBootstrap.buildConfigFromEnv(env).isDisableGatewayTrajectoryCollection());
            
            // Test "1"
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "1");
            assertTrue(GatewayBootstrap.buildConfigFromEnv(env).isDisableGatewayTrajectoryCollection());
            
            // Test empty string
            env.put("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "");
            assertFalse(GatewayBootstrap.buildConfigFromEnv(env).isDisableGatewayTrajectoryCollection());
        }
    }

    @Nested
    @DisplayName("buildAppFromConfig tests")
    class BuildAppFromConfigTests {

        @Test
        @DisplayName("Test buildAppFromConfig requires redis backend")
        void testBuildAppRequiresRedis() {
            GatewayConfig config = new GatewayConfig();
            config.setPort(8080);
            
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayBootstrap.buildAppFromConfig(config, null)
            );
            assertTrue(ex.getMessage().contains("redis"));
        }

        @Test
        @DisplayName("Test buildAppFromConfig wires configured LoRA repository")
        void testBuildAppWiresLoraRepository() throws Exception {
            Path recordDir = Files.createTempDirectory("gateway-bootstrap-records");
            Path loraRoot = Files.createTempDirectory("gateway-bootstrap-lora");
            GatewayConfig config = new GatewayConfig();
            config.setPort(8080);
            config.setRecordDir(recordDir.toString());
            config.setLoraRepoRoot(loraRoot.toString());
            config.setJudgeUrl("");

            GatewayApplication application = GatewayBootstrap.buildAppFromConfig(
                    config,
                    ignored -> new GatewayHttpResponse(200, "{}"),
                    new FakeRedisBackend()
            );

            assertNotNull(application.loraRepository());
            assertEquals(loraRoot, application.loraRepository().getRepoPath());
        }
    }

    static final class FakeRedisBackend implements RedisTrajectoryStoreBackend {
        @Override
        public RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
            return (keys, args) -> List.of();
        }

        @Override
        public List<Object> hmget(String key, List<String> fields) {
            return List.of();
        }

        @Override
        public Object hget(String key, String field) {
            return null;
        }

        @Override
        public long hset(String key, Map<String, Object> mapping) {
            return mapping.size();
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            return mapping.size();
        }

        @Override
        public long zcard(String key) {
            return 0;
        }

        @Override
        public long zrem(String key, Object... members) {
            return 0;
        }

        @Override
        public long sadd(String key, Object... members) {
            return members.length;
        }

        @Override
        public long srem(String key, Object... members) {
            return 0;
        }

        @Override
        public Set<Object> smembers(String key) {
            return Set.of();
        }

        @Override
        public RedisTrajectoryStorePipeline pipeline() {
            return new FakePipeline();
        }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline {
        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline sadd(String key, Object... members) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zcard(String key) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hget(String key, String field) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hmget(String key, List<String> fields) {
            return this;
        }

        @Override
        public List<Object> execute() {
            return List.of();
        }
    }
}
