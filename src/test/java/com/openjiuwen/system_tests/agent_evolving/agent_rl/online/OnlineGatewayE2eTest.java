/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent_evolving.agent_rl.online;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.app.GatewayApplication;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.app.GatewayBootstrap;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.PendingJudgeStore;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System test for the agent_rl online gateway without external services.
 *
 * <p>Mirrors Python's {@code test_online_gateway_e2e.py} in
 * {@code tests/system_tests/agent_evolving/agent_rl/online}.
 */
class OnlineGatewayE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void testOnlineGatewayProxyAndRailUploadE2e(@TempDir Path tmpDir) throws Exception {
        FakeRedis redis = new FakeRedis();
        List<Map<String, Object>> upstreamRequests = new ArrayList<>();
        GatewayConfig config = new GatewayConfig(18080);
        config.setLlmUrl("http://llm.local");
        config.setJudgeUrl("");
        config.setModelId("st-model");
        config.setGatewayApiKey("gw-token");
        config.setRecordDir(tmpDir.resolve("records").toString());
        config.setDumpTokenIds(true);
        config.setSingleUserDefault(false);

        GatewayApplication application = GatewayBootstrap.buildAppFromConfig(config, request -> {
            Map<String, Object> body = applicationBody(request);
            upstreamRequests.add(body);
            return new GatewayHttpResponse(200, """
                    {"id":"chatcmpl-st","object":"chat.completion","created":123,"model":"st-model",
                     "prompt_token_ids":[101,102],
                     "choices":[{"index":0,"finish_reason":"stop",
                       "message":{"role":"assistant","content":"pong"},
                       "token_ids":[201,202],
                       "logprobs":{"content":[{"logprob":-0.1},{"logprob":-0.2}]}}],
                     "usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}
                    """);
        }, redis);

        IllegalArgumentException missingUser = assertThrows(
                IllegalArgumentException.class,
                () -> application.trajectoryRuntime().recordSample(Map.of("sample_id", "s-missing")));
        assertTrue(missingUser.getMessage().contains("missing user_id"));

        Map<String, Object> chatResponse = application.forwarder().forward(new LinkedHashMap<>(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "stream", true
        )), Map.of("Authorization", "Bearer gw-token", "x-user-id", "st-user"));

        assertEquals("chatcmpl-st", chatResponse.get("id"));
        assertEquals(List.of(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "stream", false,
                "model", "st-model",
                "logprobs", true,
                "top_logprobs", 1
        )), upstreamRequests);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("protocol_version", "rail-v1");
        payload.put("session_id", "session-st");
        payload.put("trajectory_id", "traj-st");
        payload.put("tenant_id", "st-user");
        payload.put("session_done", true);
        payload.put("samples", List.of(new LinkedHashMap<>(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "response", Map.of("role", "assistant", "content", "pong", "finish_reason", "stop"),
                "prompt_ids", List.of(101, 102),
                "response_tokens", List.of(201, 202),
                "logprobs", List.of(-0.1, -0.2)
        ))));

        Map<String, Object> ingestResult = application.trajectoryRuntime().getRailIngestor().ingestRailBatch(payload);

        assertEquals(1, ingestResult.get("accepted"));
        assertEquals(1, ingestResult.get("session_flushed"));
        assertEquals(1, application.trajectoryRuntime().snapshotStats().get("trajectory_store_total"));

        String rawSample = Files.readString(tmpDir.resolve("records").resolve("samples.jsonl")).strip();
        Map<String, Object> storedSample = MAPPER.readValue(rawSample, MAP_TYPE);
        assertEquals("st-user", storedSample.get("user_id"));
        assertEquals(Map.of("tag", "session_done", "followup_user_feedback", ""), storedSample.get("judge_feedback"));
    }

    private static Map<String, Object> applicationBody(java.net.http.HttpRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        request.bodyPublisher().ifPresent(publisher -> {
            BodySubscriber subscriber = new BodySubscriber();
            publisher.subscribe(subscriber);
            try {
                body.putAll(MAPPER.readValue(subscriber.body(), MAP_TYPE));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        return body;
    }

    static final class BodySubscriber implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
        private final StringBuilder body = new StringBuilder();
        private java.util.concurrent.Flow.Subscription subscription;

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.nio.ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            body.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
            if (subscription != null) {
                subscription.cancel();
            }
        }

        String body() {
            return body.toString();
        }
    }

    static final class FakeRedis implements RedisTrajectoryStoreBackend, PendingJudgeStore.TestablePendingJudgeBackend {
        final Map<String, String> kv = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        final Map<String, Set<Object>> sets = new LinkedHashMap<>();
        final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSec) {
            kv.put(key, value);
        }

        @Override
        public void expire(String key, int ttlSec) {
        }

        @Override
        public List<String> zrange(String key, int start, int end) {
            List<Map.Entry<Object, Double>> entries = new ArrayList<>(zsets.getOrDefault(key, Map.of()).entrySet());
            entries.sort(Map.Entry.comparingByValue());
            List<String> members = entries.stream().map(entry -> String.valueOf(entry.getKey())).toList();
            int normalizedEnd = end == -1 ? members.size() - 1 : end;
            if (members.isEmpty() || start > normalizedEnd) {
                return List.of();
            }
            return new ArrayList<>(members.subList(start, Math.min(normalizedEnd + 1, members.size())));
        }

        @Override
        public Object get(String key) {
            return kv.get(key);
        }

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
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        @Override
        public long zcard(String key) {
            return zsets.getOrDefault(key, Map.of()).size();
        }

        @Override
        public long zrem(String key, Object... members) {
            long removed = 0;
            Map<Object, Double> zset = zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            for (Object member : members) {
                if (zset.remove(member) != null) {
                    removed++;
                }
            }
            return removed;
        }

        @Override
        public long sadd(String key, Object... members) {
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (Object member : members) {
                set.add(member);
            }
            return members.length;
        }

        @Override
        public long srem(String key, Object... members) {
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            long removed = 0;
            for (Object member : members) {
                if (set.remove(member)) {
                    removed++;
                }
            }
            return removed;
        }

        @Override
        public Set<Object> smembers(String key) {
            return new LinkedHashSet<>(sets.getOrDefault(key, Set.of()));
        }

        @Override
        public RedisTrajectoryStorePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline,
            PendingJudgeStore.TestablePendingJudgePipeline {
        private final FakeRedis redis;
        private final List<java.util.function.Supplier<Object>> operations = new ArrayList<>();

        FakePipeline(FakeRedis redis) {
            this.redis = redis;
        }

        @Override
        public void delete(String key) {
            operations.add(() -> redis.kv.remove(key));
        }

        @Override
        public void zremSingle(String key, String member) {
            operations.add(() -> redis.zrem(key, member));
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) {
            operations.add(() -> redis.zrem(key, members));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) {
            operations.add(() -> redis.hset(key, mapping));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) {
            operations.add(() -> redis.zadd(key, mapping));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline sadd(String key, Object... members) {
            operations.add(() -> redis.sadd(key, members));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zcard(String key) {
            operations.add(() -> redis.zcard(key));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hget(String key, String field) {
            operations.add(() -> redis.hget(key, field));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hmget(String key, List<String> fields) {
            operations.add(() -> redis.hmget(key, fields));
            return this;
        }

        @Override
        public List<Object> execute() {
            List<Object> results = operations.stream()
                    .map(java.util.function.Supplier::get)
                    .toList();
            operations.clear();
            return results;
        }
    }
}
