/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior tests for pending delayed-judge storage.
 * <p>
 * Mirrors Python's {@code PendingJudgeStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.pending_judge_store}.
 */
class PendingJudgeStoreBehaviorTest {

    @Test
    void popEarliestReturnsNullWhenSessionHasNoSamples() {
        PendingJudgeStore store = new PendingJudgeStore(new FakeRedis(), 60);

        assertNull(store.popEarliest("missing"));
    }

    @Test
    void putUsesPythonFalsyDefaultsAndExpiresSessionIndex() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 5);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("session_id", null);
        sample.put("trajectory_id", null);
        sample.put("step_index", null);
        sample.put("_pending_created_at", 12.0);

        store.put(sample);

        String key = "pending_judge:::0";
        assertTrue(redis.kv.containsKey(key));
        assertEquals(12.0, redis.zsets.get("pending_judge_session:").get(key));
        assertEquals(5, redis.ttl.get("pending_judge_session:"));
    }

    @Test
    void popAllReturnsSamplesInScoreOrderAndRemovesThem() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 60);
        store.put(sample("s1", "late", 2, 20.0));
        store.put(sample("s1", "early", 1, 10.0));

        List<Map<String, Object>> popped = store.popAll("s1");

        assertEquals(List.of("early", "late"), popped.stream().map(item -> item.get("trajectory_id")).toList());
        assertFalse(redis.kv.containsKey("pending_judge:s1:early:1"));
        assertFalse(redis.kv.containsKey("pending_judge:s1:late:2"));
        assertTrue(redis.zsets.get("pending_judge_session:s1").isEmpty());
    }

    private static Map<String, Object> sample(String sessionId, String trajectoryId, int stepIndex, double createdAt) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("session_id", sessionId);
        sample.put("trajectory_id", trajectoryId);
        sample.put("step_index", stepIndex);
        sample.put("_pending_created_at", createdAt);
        return sample;
    }

    static final class FakeRedis implements RedisTrajectoryStoreBackend, PendingJudgeStore.TestablePendingJudgeBackend {
        final Map<String, String> kv = new LinkedHashMap<>();
        final Map<String, Map<String, Double>> zsets = new LinkedHashMap<>();
        final Map<String, Integer> ttl = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSec) {
            kv.put(key, value);
        }

        @Override
        public void expire(String key, int ttlSec) {
            ttl.put(key, ttlSec);
        }

        @Override
        public List<String> zrange(String key, int start, int end) {
            List<Map.Entry<String, Double>> entries = new ArrayList<>(zsets.getOrDefault(key, Map.of()).entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getValue));
            List<String> members = entries.stream().map(Map.Entry::getKey).toList();
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
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        @Override
        public long zcard(String key) {
            return zsets.getOrDefault(key, Map.of()).size();
        }

        @Override
        public long zrem(String key, Object... members) {
            Map<String, Double> bucket = zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            long removed = 0;
            for (Object member : members) {
                if (bucket.remove(String.valueOf(member)) != null) {
                    removed++;
                }
            }
            return removed;
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
            return new LinkedHashSet<>();
        }

        @Override
        public RedisTrajectoryStorePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline,
            PendingJudgeStore.TestablePendingJudgePipeline {
        private final FakeRedis redis;

        FakePipeline(FakeRedis redis) {
            this.redis = redis;
        }

        @Override
        public void delete(String key) {
            redis.kv.remove(key);
        }

        @Override
        public void zremSingle(String key, String member) {
            redis.zrem(key, member);
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) {
            redis.zrem(key, members);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) {
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) {
            redis.zadd(key, mapping);
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
