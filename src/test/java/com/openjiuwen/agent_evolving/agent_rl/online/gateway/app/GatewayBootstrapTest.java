/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayBootstrapTest {

    @Test
    void buildConfigFromEnvMirrorsPythonDefaultsAndOverrides() {
        GatewayConfig config = GatewayBootstrap.buildConfigFromEnv(Map.of(
                "GATEWAY_PORT", "18090",
                "INFERENCE_URL", "http://llm.local:18000",
                "JUDGE_URL", "http://judge.local:19000",
                "MODEL_ID", "model-a",
                "JUDGE_MODEL", "judge-a",
                "REQUEST_TIMEOUT", "42.5",
                "LLM_API_KEY", "llm-key",
                "JUDGE_API_KEY", "judge-key",
                "GATEWAY_API_KEY", "gw-key",
                "RECORD_DIR", "tmp-records",
                "LOG_LEVEL", "DEBUG",
                "DUMP_TOKEN_IDS", "true",
                "LORA_REPO_ROOT", "lora-root",
                "REDIS_URL", "redis://local",
                "UPSTREAM_MAX_RETRIES", "5",
                "UPSTREAM_RETRY_BACKOFF_SEC", "0.7",
                "UPSTREAM_RETRY_MAX_BACKOFF_SEC", "3.2",
                "DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "1"
        ));

        assertEquals(18090, config.getPort());
        assertEquals("127.0.0.1", config.getHost());
        assertEquals("http://llm.local:18000", config.getLlmUrl());
        assertEquals("http://judge.local:19000", config.getJudgeUrl());
        assertEquals("model-a", config.getModelId());
        assertEquals("judge-a", config.getJudgeModel());
        assertEquals(42.5, config.getRequestTimeout());
        assertEquals("llm-key", config.getLlmApiKey());
        assertEquals("judge-key", config.getJudgeApiKey());
        assertEquals("gw-key", config.getGatewayApiKey());
        assertEquals("tmp-records", config.getRecordDir());
        assertEquals("DEBUG", config.getLogLevel());
        assertTrue(config.isDumpTokenIds());
        assertEquals("lora-root", config.getLoraRepoRoot());
        assertEquals("redis://local", config.getRedisUrl());
        assertEquals(5, config.getUpstreamMaxRetries());
        assertEquals(0.7, config.getUpstreamRetryBackoffSec());
        assertEquals(3.2, config.getUpstreamRetryMaxBackoffSec());
        assertTrue(config.isDisableGatewayTrajectoryCollection());
    }

    @Test
    void buildConfigFromEnvRequiresGatewayPort() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayBootstrap.buildConfigFromEnv(Map.of())
        );

        assertEquals("GATEWAY_PORT is required", error.getMessage());
    }

    @Test
    void buildAppFromConfigWiresGatewayRuntimeAndJudgeScorer() throws Exception {
        GatewayConfig config = new GatewayConfig(18080);
        config.setLlmUrl("http://llm.local");
        config.setJudgeUrl("http://judge.local");
        config.setModelId("main-model");
        config.setJudgeModel("judge-model");
        config.setJudgeApiKey("judge-token");
        config.setRecordDir(Files.createTempDirectory("gateway-bootstrap").toString());

        RecordingTransport transport = new RecordingTransport();
        GatewayApplication app = GatewayBootstrap.buildAppFromConfig(config, transport, new FakeRedis());

        assertNotNull(app.forwarder());
        assertNotNull(app.upstreamClient());
        assertNotNull(app.trajectoryRuntime());
        assertEquals("RedisTrajectoryStore", app.trajectoryRuntime().getStoreBackend());

        Map<String, Object> result = app.trajectoryRuntime().getRailIngestor().ingestRailBatch(new LinkedHashMap<>(Map.of(
                "protocol_version", "rail-v1",
                "session_id", "s1",
                "trajectory_id", "traj-1",
                "session_done", true,
                "samples", List.of(new LinkedHashMap<>(Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "ping")),
                        "response", Map.of("role", "assistant", "content", "pong"),
                        "prompt_ids", List.of(101, 102),
                        "response_tokens", List.of(201, 202),
                        "logprobs", List.of(-0.1, -0.2),
                        "user_id", "user-1"
                )))
        )));

        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("session_flushed"));
        assertEquals(1, transport.requests.size());
        assertTrue(transport.requests.getFirst().uri().toString().contains("http://judge.local/v1/chat/completions"));

        Map<String, Object> stored = app.trajectoryRuntime().snapshotStats();
        assertEquals(1, stored.get("trajectory_store_pending"));
    }

    @Test
    void buildAppFromConfigRequiresRedisBackend() {
        GatewayConfig config = new GatewayConfig(18080);
        config.setLlmUrl("http://llm.local");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayBootstrap.buildAppFromConfig(config, new RecordingTransport(), null)
        );

        assertEquals("gateway requires redis_url or injected redis_client", error.getMessage());
    }

    static final class RecordingTransport implements GatewayHttpTransport {
        final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public GatewayHttpResponse send(HttpRequest request) throws IOException {
            requests.add(request);
            return new GatewayHttpResponse(200,
                    "{" +
                            "\"choices\":[{" +
                            "\"message\":{\"content\":\"{\\\"overall\\\":8.0,\\\"reason\\\":\\\"ok\\\"}\"}," +
                            "\"finish_reason\":\"stop\"" +
                            "}]," +
                            "\"model\":\"judge-model\"" +
                            "}");
        }
    }

    static final class FakeRedis implements RedisTrajectoryStoreBackend {
        private final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        private final Map<String, LinkedHashMap<Object, Double>> zsets = new LinkedHashMap<>();
        private final Map<String, LinkedHashSet<Object>> sets = new LinkedHashMap<>();

        @Override
        public RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
            return (keys, args) -> List.of();
        }

        @Override
        public List<Object> hmget(String key, List<String> fields) {
            Map<String, Object> hash = hashes.getOrDefault(key, Map.of());
            List<Object> values = new ArrayList<>();
            for (String field : fields) {
                values.add(hash.get(field));
            }
            return values;
        }

        @Override
        public Object hget(String key, String field) {
            return hashes.getOrDefault(key, Map.of()).get(field);
        }

        @Override
        public long hset(String key, Map<String, Object> mapping) {
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            LinkedHashMap<Object, Double> zset = zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            zset.putAll(mapping);
            return mapping.size();
        }

        @Override
        public long zcard(String key) {
            return zsets.getOrDefault(key, new LinkedHashMap<>()).size();
        }

        @Override
        public long zrem(String key, Object... members) {
            LinkedHashMap<Object, Double> zset = zsets.get(key);
            if (zset == null) {
                return 0;
            }
            long removed = 0;
            for (Object member : members) {
                if (zset.remove(member) != null) {
                    removed += 1;
                }
            }
            return removed;
        }

        @Override
        public long sadd(String key, Object... members) {
            LinkedHashSet<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            long added = 0;
            for (Object member : members) {
                if (set.add(member)) {
                    added += 1;
                }
            }
            return added;
        }

        @Override
        public long srem(String key, Object... members) {
            LinkedHashSet<Object> set = sets.get(key);
            if (set == null) {
                return 0;
            }
            long removed = 0;
            for (Object member : members) {
                if (set.remove(member)) {
                    removed += 1;
                }
            }
            return removed;
        }

        @Override
        public Set<Object> smembers(String key) {
            return new LinkedHashSet<>(sets.getOrDefault(key, new LinkedHashSet<>()));
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline {
        private final FakeRedis redis;
        private final List<Runnable> ops = new ArrayList<>();
        private final List<Object> results = new ArrayList<>();

        FakePipeline(FakeRedis redis) {
            this.redis = redis;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) {
            ops.add(() -> results.add(redis.zrem(key, members)));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) {
            ops.add(() -> results.add(redis.hset(key, mapping)));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) {
            ops.add(() -> results.add(redis.zadd(key, mapping)));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline sadd(String key, Object... members) {
            ops.add(() -> results.add(redis.sadd(key, members)));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zcard(String key) {
            ops.add(() -> results.add(redis.zcard(key)));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hget(String key, String field) {
            ops.add(() -> results.add(redis.hget(key, field)));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hmget(String key, List<String> fields) {
            ops.add(() -> results.add(redis.hmget(key, fields)));
            return this;
        }

        @Override
        public List<Object> execute() {
            for (Runnable op : ops) {
                op.run();
            }
            return List.copyOf(results);
        }
    }
}
