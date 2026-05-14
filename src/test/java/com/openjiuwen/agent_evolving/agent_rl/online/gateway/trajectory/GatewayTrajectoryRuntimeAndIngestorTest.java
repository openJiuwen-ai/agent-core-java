/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayTrajectoryRuntimeAndIngestorTest {

    @Test
    void gatewayTrajectoryRuntimeFillsSingleUserDefaultOnRecord() throws Exception {
        Path tmp = Files.createTempDirectory("gateway-runtime");
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tmp.toString());
        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, new FakeRedis());

        runtime.recordSample(new LinkedHashMap<>(Map.of("sample_id", "s1")));

        Path sampleFile = tmp.resolve("samples.jsonl");
        String raw = Files.readString(sampleFile).trim();
        assertEquals(true, raw.contains("\"user_id\":\"jiuwenclaw-web\""));
    }

    @Test
    void normalizeRailSamplePreservesPromptResponseAndStreamingLogprobs() {
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("protocol_version", "rail-v1");
        batch.put("session_id", "session-1");
        batch.put("trajectory_id", "traj-stream");
        batch.put("samples", List.of(new LinkedHashMap<>(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hello")),
                "response", Map.of("role", "assistant", "content", "pong"),
                "prompt_ids", List.of(1, 2, 3),
                "response_tokens", List.of(4, 5),
                "logprobs", Map.of("content", List.of(Map.of("logprob", -0.1), Map.of("logprob", -0.2))),
                "user_id", "user-1"
        ))));

        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(batch, (Map<String, Object>) ((List<?>) batch.get("samples")).getFirst(), "");

        @SuppressWarnings("unchecked")
        Map<String, Object> trajectory = (Map<String, Object>) normalized.get("trajectory");
        assertEquals(List.of(1, 2, 3), trajectory.get("prompt_ids"));
        assertEquals(List.of(4, 5), trajectory.get("response_ids"));
        assertEquals(List.of(-0.1, -0.2), trajectory.get("response_logprobs"));
    }

    static final class FakeRedis implements RedisTrajectoryStoreBackend {
        final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        final Map<String, Set<Object>> sets = new LinkedHashMap<>();
        final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();

        @Override
        public RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
            return (keys, args) -> {
                String pendingKey = keys.get(0);
                String trainingKey = keys.get(1);
                int limit = ((Number) args.get(0)).intValue();
                double nowScore = ((Number) args.get(1)).doubleValue();
                String newStatus = String.valueOf(args.get(2));
                String trajPrefix = String.valueOf(args.get(3));
                List<Map.Entry<Object, Double>> ordered = new ArrayList<>(zsets.getOrDefault(pendingKey, Map.of()).entrySet());
                ordered.sort(Map.Entry.comparingByValue());
                List<Object> ids = ordered.stream().limit(limit).map(Map.Entry::getKey).toList();
                for (Object sampleId : ids) {
                    zsets.getOrDefault(pendingKey, new LinkedHashMap<>()).remove(sampleId);
                    zsets.computeIfAbsent(trainingKey, ignored -> new LinkedHashMap<>()).put(sampleId, nowScore);
                    hashes.computeIfAbsent(trajPrefix + sampleId, ignored -> new LinkedHashMap<>()).put("status", newStatus);
                }
                return new ArrayList<>(ids);
            };
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
            return 1;
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
            long removed = 0;
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
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

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline {
        private final FakeRedis redis;
        private final List<Operation> operations = new ArrayList<>();

        FakePipeline(FakeRedis redis) {
            this.redis = redis;
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
            List<Object> results = new ArrayList<>();
            for (Operation operation : operations) {
                results.add(operation.run());
            }
            operations.clear();
            return results;
        }
    }

    interface Operation {
        Object run();
    }
}
