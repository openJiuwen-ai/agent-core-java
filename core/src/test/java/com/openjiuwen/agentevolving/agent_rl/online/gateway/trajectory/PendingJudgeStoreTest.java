package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingJudgeStoreTest {

    @Test
    void putGetAndPopOneRoundTripDoesNotMutateCallerPayload() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);
        Map<String, Object> sample = sample("session-1", "traj-1", 3);

        store.put(sample);

        assertFalse(sample.containsKey("_pending_key"));
        List<Map<String, Object>> rows = store.getBySession("session-1");
        assertEquals(1, rows.size());
        assertEquals("traj-1", rows.get(0).get("trajectory_id"));
        assertTrue(String.valueOf(rows.get(0).get("_pending_key")).contains("pending_judge:session-1:traj-1:3"));

        Map<String, Object> popped = store.popOne("session-1", "traj-1", 3);
        assertNotNull(popped);
        assertEquals("traj-1", popped.get("trajectory_id"));
        assertEquals(List.of(), store.getBySession("session-1"));
    }

    @Test
    void popEarliestAndPopAllRespectSortedSessionOrder() {
        FakePendingJudgeBackend backend = new FakePendingJudgeBackend();
        PendingJudgeStore store = new PendingJudgeStore(backend);

        Map<String, Object> earlier = sample("session-2", "traj-a", 2);
        earlier.put("_pending_created_at", 1.5d);
        Map<String, Object> later = sample("session-2", "traj-b", 1);
        later.put("_pending_created_at", 2.5d);
        Map<String, Object> last = sample("session-2", "traj-c", 5);
        last.put("_pending_created_at", 3.5d);

        store.put(later);
        store.put(earlier);
        store.put(last);

        assertEquals(List.of(1.5d, 2), PendingJudgeStore.sortKey(earlier));
        assertEquals("traj-a", store.popEarliest("session-2").get("trajectory_id"));

        List<Map<String, Object>> remaining = store.popAll("session-2");
        assertEquals(List.of("traj-b", "traj-c"), remaining.stream().map(item -> String.valueOf(item.get("trajectory_id"))).toList());
        assertNull(store.popEarliest("session-2"));
    }

    private static Map<String, Object> sample(String sessionId, String trajectoryId, int stepIndex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", sessionId);
        payload.put("trajectory_id", trajectoryId);
        payload.put("step_index", stepIndex);
        payload.put("message", "payload");
        return payload;
    }

    static final class FakePendingJudgeBackend implements PendingJudgeStoreBackend {
        private final Map<String, String> kv = new LinkedHashMap<>();
        private final Map<String, Map<String, Double>> zsets = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSeconds) {
            kv.put(key, value);
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        @Override
        public long expire(String key, int ttlSeconds) {
            return 1;
        }

        @Override
        public List<Object> zrange(String key, int start, int end) {
            List<Map.Entry<String, Double>> ordered = new ArrayList<>(zsets.getOrDefault(key, Map.of()).entrySet());
            ordered.sort(Map.Entry.comparingByValue());
            int effectiveEnd = end == -1 ? ordered.size() - 1 : Math.min(end, ordered.size() - 1);
            if (ordered.isEmpty() || start > effectiveEnd) {
                return List.of();
            }
            List<Object> keys = new ArrayList<>();
            for (int index = start; index <= effectiveEnd; index++) {
                keys.add(ordered.get(index).getKey());
            }
            return keys;
        }

        @Override
        public List<Object> mget(List<String> keys) {
            List<Object> rows = new ArrayList<>();
            for (String key : keys) {
                rows.add(kv.get(key));
            }
            return rows;
        }

        @Override
        public Object get(String key) {
            return kv.get(key);
        }

        @Override
        public PendingJudgeStorePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    static final class FakePipeline implements PendingJudgeStoreBackend.PendingJudgeStorePipeline {
        private final FakePendingJudgeBackend backend;
        private final List<Runnable> operations = new ArrayList<>();

        FakePipeline(FakePendingJudgeBackend backend) {
            this.backend = backend;
        }

        @Override
        public PendingJudgeStoreBackend.PendingJudgeStorePipeline delete(String key) {
            operations.add(() -> backend.kv.remove(key));
            return this;
        }

        @Override
        public PendingJudgeStoreBackend.PendingJudgeStorePipeline zrem(String key, Object member) {
            operations.add(() -> backend.zsets.getOrDefault(key, new LinkedHashMap<>()).remove(member));
            return this;
        }

        @Override
        public List<Object> execute() {
            for (Runnable operation : operations) {
                operation.run();
            }
            operations.clear();
            return List.of();
        }
    }
}
