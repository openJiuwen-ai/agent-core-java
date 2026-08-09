/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * <p>Mirrors Python's {@code RedisTrajectoryStore} tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/test_storage.py}.</p>
 */
class RedisTrajectoryStoreTest {

    @Test
    void statusFlowMatchesPythonSlice() {
        RedisTrajectoryStore store = new RedisTrajectoryStore(new FakeRedis());
        store.saveSample(sample("s1"), "online");
        store.saveSample(sample("s2"), "online");

        assertEquals(2, store.getPendingCount("online"));
        assertEquals(List.of("online"), store.getUsersAboveThreshold(2));

        List<Map<String, Object>> samples = store.fetchAndMarkTraining("online", 2);
        assertEquals(List.of("s1", "s2"), samples.stream().map(item -> String.valueOf(item.get("sample_id"))).toList());

        store.markTrained(List.of("s1"));
        store.resetToPending(List.of("s2"));
        Map<String, Integer> stats = store.stats();
        assertEquals(1, stats.get("pending_samples"));
        assertEquals(1, stats.get("trained_samples"));
        assertEquals(0, stats.get("failed_samples"));
    }

    @Test
    void saveSampleReplacesOldStatusIndex() {
        FakeRedis redis = new FakeRedis();
        RedisTrajectoryStore store = new RedisTrajectoryStore(redis);
        store.saveSample(sample("s1"), "online");
        store.fetchAndMarkTraining("online", 1);

        store.saveSample(sample("s1"), "online");

        Map<String, Integer> stats = store.stats();
        assertEquals(1, stats.get("pending_samples"));
        assertEquals(0, stats.get("training_samples"));
    }

    @Test
    void updateStatusToleratesMissingPayload() {
        FakeRedis redis = new FakeRedis();
        RedisTrajectoryStore store = new RedisTrajectoryStore(redis);
        store.saveSample(sample("s1"), "online");
        store.fetchAndMarkTraining("online", 1);

        redis.hashes.get("rl:traj:s1").put("sample_json", null);
        store.markTrained(List.of("s1"));

        Map<String, Integer> stats = store.stats();
        assertEquals(0, stats.get("pending_samples"));
        assertEquals(1, stats.get("training_samples"));
        assertEquals(0, stats.get("trained_samples"));
    }

    @Test
    void staleUsersAreRemovedWhenAllBucketsAreEmpty() {
        FakeRedis redis = new FakeRedis();
        RedisTrajectoryStore store = new RedisTrajectoryStore(redis);
        redis.sets.computeIfAbsent("rl:traj_users", ignored -> new LinkedHashSet<>()).add("stale-user");

        assertEquals(List.of(), store.getUsersAboveThreshold(1));
        assertEquals(Set.of(), redis.smembers("rl:traj_users"));
    }

    @Test
    void saveSampleUsesPythonUserIdOrFallbackSemantics() {
        FakeRedis redis = new FakeRedis();
        RedisTrajectoryStore store = new RedisTrajectoryStore(redis);

        Map<String, Object> blankUser = sample("s1");
        blankUser.put("user_id", "");
        store.saveSample(blankUser, "fallback-user");

        Map<String, Object> whitespaceUser = sample("s2");
        whitespaceUser.put("user_id", "   ");
        store.saveSample(whitespaceUser, "fallback-user");

        assertEquals("fallback-user", redis.hashes.get("rl:traj:s1").get("user_id"));
        assertEquals("   ", redis.hashes.get("rl:traj:s2").get("user_id"));
        assertEquals(1, store.getPendingCount("fallback-user"));
        assertEquals(1, store.getPendingCount("   "));
        assertEquals(0, store.getPendingCount("online"));
    }

    @Test
    void saveSampleFallsBackForPythonFalseyMetadataValues() {
        FakeRedis redis = new FakeRedis();
        RedisTrajectoryStore store = new RedisTrajectoryStore(redis);
        Map<String, Object> source = sample("s1");
        source.put("created_at", "");
        source.put("session_id", "");

        store.saveSample(source, "online");

        Map<String, Object> hash = redis.hashes.get("rl:traj:s1");
        assertEquals("default", hash.get("session_id"));
        assertFalse(String.valueOf(hash.get("created_at")).isEmpty());
    }

    private static Map<String, Object> sample(String sampleId) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", sampleId);
        sample.put("user_id", "online");
        sample.put("session_id", "sess-1");
        sample.put("created_at", "2026-01-01T00:00:00+00:00");
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("response", Map.of("message", Map.of("role", "assistant", "content", "world")));
        sample.put("trajectory", Map.of(
                "input_ids", List.of(1, 2, 3),
                "response_ids", List.of(4, 5),
                "response_logprobs", List.of(-0.1, -0.2)
        ));
        sample.put("judge", Map.of("score", 0.5));
        return sample;
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
