/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior tests for PendingJudgeStore.
 *
 * <p>Mirrors Python's {@code PendingJudgeStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.pending_judge_store}.</p>
 */
class PendingJudgeStoreAndDispatcherTest {

    @Test
    @Tag("level0")
    @DisplayName("Test PendingJudgeStore constructor requires redis")
    void testConstructorRequiresRedis() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new PendingJudgeStore(null)
        );
        assertTrue(ex.getMessage().contains("redis"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test PendingJudgeStore constructor with valid redis")
    void testConstructorWithValidRedis() {
        PendingJudgeStore store = new PendingJudgeStore(new FakeRedis());

        assertNotNull(store);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test put writes pending payload, index, and default TTL")
    void testPutWritesPayloadAndDefaultTtl() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis);

        store.put(sample("session123", "trajectory456", 5, 12.0));

        String key = "pending_judge:session123:trajectory456:5";
        assertTrue(redis.kv.containsKey(key));
        assertEquals(12.0, redis.zsets.get("pending_judge_session:session123").get(key));
        assertEquals(24 * 3600, redis.ttl.get("pending_judge_session:session123"));
        Map<String, Object> payload = store.getBySession("session123").getFirst();
        assertEquals(key, payload.get("_pending_key"));
        assertEquals(12.0, payload.get("_pending_created_at"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test custom TTL is used for session index")
    void testCustomTtl() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 3600);

        store.put(sample("s1", "t1", 0, 1.0));

        assertEquals(3600, redis.ttl.get("pending_judge_session:s1"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test getBySession skips missing payload rows")
    void testGetBySessionSkipsMissingRows() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 60);
        store.put(sample("s1", "kept", 1, 10.0));
        store.put(sample("s1", "missing", 2, 20.0));
        redis.kv.remove("pending_judge:s1:missing:2");

        List<Map<String, Object>> samples = store.getBySession("s1");

        assertEquals(1, samples.size());
        assertEquals("kept", samples.getFirst().get("trajectory_id"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test popOne returns payload and removes sample/index entries")
    void testPopOneReturnsPayloadAndRemovesEntries() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 60);
        store.put(sample("s1", "t1", 3, 10.0));

        Map<String, Object> popped = store.popOne("s1", "t1", 3);

        assertEquals("t1", popped.get("trajectory_id"));
        assertFalse(redis.kv.containsKey("pending_judge:s1:t1:3"));
        assertTrue(redis.zsets.get("pending_judge_session:s1").isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test popOne returns null for missing sample")
    void testPopOneReturnsNullForMissingSample() {
        PendingJudgeStore store = new PendingJudgeStore(new FakeRedis(), 60);

        assertNull(store.popOne("s1", "missing", 0));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test popEarliest uses created_at then step_index order")
    void testPopEarliestUsesSortKey() {
        FakeRedis redis = new FakeRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 60);
        store.put(sample("s1", "late", 2, 10.0));
        store.put(sample("s1", "early", 1, 10.0));

        Map<String, Object> popped = store.popEarliest("s1");

        assertEquals("early", popped.get("trajectory_id"));
        assertFalse(redis.kv.containsKey("pending_judge:s1:early:1"));
        assertTrue(redis.kv.containsKey("pending_judge:s1:late:2"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test popEarliest returns null when session has no samples")
    void testPopEarliestReturnsNullForEmptySession() {
        PendingJudgeStore store = new PendingJudgeStore(new FakeRedis(), 60);

        assertNull(store.popEarliest("missing"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test popAll returns samples in order and removes them")
    void testPopAllReturnsSamplesInOrderAndRemovesThem() {
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
