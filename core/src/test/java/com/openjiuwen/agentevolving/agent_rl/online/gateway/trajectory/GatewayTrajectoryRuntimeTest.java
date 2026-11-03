package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agentevolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

class GatewayTrajectoryRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void recordSampleFillsSingleUserDefaultAndWritesJsonl() throws Exception {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());

        FakeGatewayRedis redis = new FakeGatewayRedis();
        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, redis);

        runtime.recordSample(Map.of("sample_id", "s1"));

        assertEquals("RedisTrajectoryStore", runtime.getStoreBackend());
        assertEquals("jiuwenclaw-web", redis.hashes.get("rl:traj:s1").get("user_id"));
        String line = Files.readString(tempDir.resolve("samples.jsonl"), StandardCharsets.UTF_8).trim();
        assertTrue(line.contains("\"user_id\":\"jiuwenclaw-web\""));
    }

    @Test
    void recordSampleRejectsMissingUserIdWhenSingleUserDefaultDisabled() {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());
        config.setSingleUserDefault(false);

        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, new FakeGatewayRedis());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> runtime.recordSample(Map.of("sample_id", "s1"))
        );
        assertEquals("missing user_id; online training requires a stable user id", error.getMessage());
    }

    @Test
    void snapshotStatsMergesRecorderAndTrajectoryStoreStats() {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());

        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, new FakeGatewayRedis());
        runtime.recordSample(Map.of("sample_id", "s1", "user_id", "tenant-a"));
        runtime.recordSample(Map.of("sample_id", "s2", "user_id", "tenant-a"));

        Map<String, Object> stats = runtime.snapshotStats();

        assertEquals(2, stats.get("total_samples"));
        assertEquals("RedisTrajectoryStore", stats.get("trajectory_store_backend"));
        assertEquals(2, stats.get("trajectory_store_total"));
        assertEquals(2, stats.get("trajectory_store_pending"));
        assertEquals(0, stats.get("trajectory_store_training"));
        assertEquals(0, stats.get("trajectory_store_trained"));
        assertEquals(0, stats.get("trajectory_store_failed"));
    }

    @Test
    void setJudgeScorerRebuildsRailIngestorAroundPendingStore() {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());

        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, new FakeGatewayRedis());
        RailBatchIngestor initial = runtime.getRailIngestor();

        runtime.setJudgeScorer((responseText, instructionText, followupUserFeedback, sessionId, turnNum) ->
                Map.of("score", 0.8d, "votes", List.of("pass"), "details", Map.of())
        );

        RailBatchIngestor rebuilt = runtime.getRailIngestor();
        assertNotNull(initial);
        assertNotNull(rebuilt);
        assertTrue(initial != rebuilt);
    }

    @Test
    void constructorRequiresRedisClient() {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new GatewayTrajectoryRuntime(config, null)
        );
        assertEquals("GatewayTrajectoryRuntime requires redis client", error.getMessage());
    }

    static final class FakeGatewayRedis {
        final Map<String, String> kv = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        final Map<String, Set<Object>> sets = new LinkedHashMap<>();
        final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();

        public RedisTrajectoryStoreBackend.RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
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

        public List<Object> hmget(String key, List<String> fields) {
            Map<String, Object> hash = hashes.getOrDefault(key, Map.of());
            List<Object> values = new ArrayList<>();
            for (String field : fields) {
                values.add(hash.get(field));
            }
            return values;
        }

        public Object hget(String key, String field) {
            return hashes.getOrDefault(key, Map.of()).get(field);
        }

        public long hset(String key, Map<String, Object> mapping) {
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return 1;
        }

        public long zadd(String key, Map<String, Double> mapping) {
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        public long zcard(String key) {
            return zsets.getOrDefault(key, Map.of()).size();
        }

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

        public long sadd(String key, Object... members) {
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (Object member : members) {
                set.add(member);
            }
            return members.length;
        }

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

        public Set<Object> smembers(String key) {
            return new LinkedHashSet<>(sets.getOrDefault(key, Set.of()));
        }

        public void set(String key, String value, int ttlSeconds) {
            kv.put(key, value);
        }

        public long expire(String key, int ttlSeconds) {
            return 1;
        }

        public List<Object> zrange(String key, int start, int end) {
            List<Map.Entry<Object, Double>> ordered = new ArrayList<>(zsets.getOrDefault(key, Map.of()).entrySet());
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

        public List<Object> mget(List<String> keys) {
            List<Object> rows = new ArrayList<>();
            for (String key : keys) {
                rows.add(kv.get(key));
            }
            return rows;
        }

        public Object get(String key) {
            return kv.get(key);
        }

        public FakePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline,
            PendingJudgeStoreBackend.PendingJudgeStorePipeline {
        private final FakeGatewayRedis redis;
        private final List<Operation> operations = new ArrayList<>();

        FakePipeline(FakeGatewayRedis redis) {
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

        @Override
        public PendingJudgeStoreBackend.PendingJudgeStorePipeline delete(String key) {
            operations.add(() -> {
                redis.kv.remove(key);
                redis.hashes.remove(key);
                return null;
            });
            return this;
        }

        @Override
        public PendingJudgeStoreBackend.PendingJudgeStorePipeline zrem(String key, Object member) {
            operations.add(() -> redis.zrem(key, member));
            return this;
        }
    }

    interface Operation {
        Object run();
    }
}
