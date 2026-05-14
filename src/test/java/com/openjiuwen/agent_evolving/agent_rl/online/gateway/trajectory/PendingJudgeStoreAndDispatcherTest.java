/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingJudgeStoreAndDispatcherTest {

    @Test
    void pendingJudgeStorePersistsAndPopsInOrder() {
        FakePendingRedis redis = new FakePendingRedis();
        PendingJudgeStore store = new PendingJudgeStore(redis, 60);

        store.put(sample("s1", "t1", 2, 2.0));
        store.put(sample("s1", "t1", 1, 1.0));

        List<Map<String, Object>> bySession = store.getBySession("s1");
        assertEquals(2, bySession.size());
        assertEquals(1, ((Number) bySession.getFirst().get("step_index")).intValue());

        Map<String, Object> earliest = store.popEarliest("s1");
        assertEquals(1, ((Number) earliest.get("step_index")).intValue());

        List<Map<String, Object>> remaining = store.popAll("s1");
        assertEquals(1, remaining.size());
        assertEquals(2, ((Number) remaining.getFirst().get("step_index")).intValue());
    }

    @Test
    void judgeDispatcherScoresSessionDoneSampleWithoutFollowupFeedback() {
        FakeRecorder recorder = new FakeRecorder();
        FakeJudgeScorer scorer = new FakeJudgeScorer(Map.of("score", 0.25, "votes", List.of(6.25), "details", Map.of("overall", 6.25)));
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", "sample-1");
        sample.put("user_id", "user-1");
        sample.put("session_id", "s1");
        sample.put("turn_num", 1);
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("trajectory", Map.of("response_text", "pong"));
        PendingJudgeStore store = new StubPendingJudgeStore(List.of(sample));
        JudgeDispatcher dispatcher = new JudgeDispatcher(store, recorder, scorer);

        int count = dispatcher.onSessionDone("s1");

        assertEquals(1, count);
        assertEquals(1, scorer.calls.size());
        assertEquals("hello", scorer.calls.getFirst().instructionText);
        assertEquals("", scorer.calls.getFirst().followupUserFeedback);
        assertEquals(0.25, ((Number) recorder.samples.getFirst().get("judge", Map.class).get("score")).doubleValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> judgeFeedback = (Map<String, Object>) recorder.samples.getFirst().get("judge_feedback");
        assertEquals("session_done", judgeFeedback.get("tag"));
    }

    private static Map<String, Object> sample(String sessionId, String trajectoryId, int stepIndex, double createdAt) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("session_id", sessionId);
        sample.put("trajectory_id", trajectoryId);
        sample.put("step_index", stepIndex);
        sample.put("_pending_created_at", createdAt);
        return sample;
    }

    static final class FakePendingRedis implements RedisTrajectoryStoreBackend, PendingJudgeStore.TestablePendingJudgeBackend {
        final Map<String, String> kv = new LinkedHashMap<>();
        final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        final Map<String, Set<Object>> sets = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSec) {
            kv.put(key, value);
        }

        @Override
        public void expire(String key, int ttlSec) {
            // ignored in fake backend
        }

        @Override
        public List<String> zrange(String key, int start, int end) {
            List<Map.Entry<Object, Double>> ordered = new ArrayList<>(zsets.getOrDefault(key, Map.of()).entrySet());
            ordered.sort(Map.Entry.comparingByValue());
            List<String> members = ordered.stream().map(entry -> String.valueOf(entry.getKey())).toList();
            if (end == -1) {
                end = members.size() - 1;
            }
            return members.subList(Math.min(start, members.size()), Math.min(end + 1, members.size()));
        }

        @Override
        public Object get(String key) {
            return kv.get(key);
        }

        @Override public RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) { return (keys, args) -> List.of(); }
        @Override public List<Object> hmget(String key, List<String> fields) { return List.of(); }
        @Override public Object hget(String key, String field) { return null; }
        @Override public long hset(String key, Map<String, Object> mapping) { hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping); return 1; }
        @Override public long zadd(String key, Map<String, Double> mapping) { zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping); return mapping.size(); }
        @Override public long zcard(String key) { return zsets.getOrDefault(key, Map.of()).size(); }
        @Override public long zrem(String key, Object... members) { for (Object member : members) { zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).remove(member); } return 0; }
        @Override public long sadd(String key, Object... members) { sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(List.of(members)); return members.length; }
        @Override public long srem(String key, Object... members) { for (Object member : members) { sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).remove(member); } return 0; }
        @Override public Set<Object> smembers(String key) { return new LinkedHashSet<>(sets.getOrDefault(key, Set.of())); }
        @Override public RedisTrajectoryStorePipeline pipeline() { return new FakePipeline(this); }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline, PendingJudgeStore.TestablePendingJudgePipeline {
        private final FakePendingRedis redis;
        private final List<Runnable> actions = new ArrayList<>();

        FakePipeline(FakePendingRedis redis) {
            this.redis = redis;
        }

        @Override public void delete(String key) { actions.add(() -> redis.kv.remove(key)); }
        @Override public void zremSingle(String key, String member) { actions.add(() -> redis.zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).remove(member)); }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) { actions.add(() -> { for (Object member : members) { redis.zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).remove(member); } }); return this; }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) { actions.add(() -> redis.hset(key, mapping)); return this; }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) { actions.add(() -> redis.zadd(key, mapping)); return this; }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline sadd(String key, Object... members) { actions.add(() -> redis.sadd(key, members)); return this; }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zcard(String key) { return this; }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hget(String key, String field) { return this; }
        @Override public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hmget(String key, List<String> fields) { return this; }
        @Override public List<Object> execute() { actions.forEach(Runnable::run); actions.clear(); return List.of(); }
    }

    static final class FakeRecorder implements SampleRecordingSink {
        final List<Map<String, Object>> samples = new ArrayList<>();
        @Override public void recordSample(Map<String, Object> sample) { samples.add(sample); }
    }

    static final class StubPendingJudgeStore extends PendingJudgeStore {
        private final List<Map<String, Object>> samples;

        StubPendingJudgeStore(List<Map<String, Object>> samples) {
            super(new FakePendingRedis());
            this.samples = new ArrayList<>(samples);
        }

        @Override
        public List<Map<String, Object>> popAll(String sessionId) {
            List<Map<String, Object>> out = new ArrayList<>(samples);
            samples.clear();
            return out;
        }
    }

    static final class FakeJudgeScorer implements JudgeScorer {
        final Map<String, Object> result;
        final List<Call> calls = new ArrayList<>();

        FakeJudgeScorer(Map<String, Object> result) {
            this.result = result;
        }

        @Override
        public Map<String, Object> score(String responseText, String instructionText, String followupUserFeedback, String sessionId, int turnNum) {
            calls.add(new Call(responseText, instructionText, followupUserFeedback, sessionId, turnNum));
            return new LinkedHashMap<>(result);
        }
    }

    record Call(String responseText, String instructionText, String followupUserFeedback, String sessionId, int turnNum) {
    }
}
