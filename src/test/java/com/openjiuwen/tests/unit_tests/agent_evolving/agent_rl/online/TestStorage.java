/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.online;

import com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStore;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Storage.
 * <p>
 * Mirrors Python's {@code test_storage.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/}.
 */
@DisplayName("Storage Tests")
class TestStorage {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("in-memory trajectory store status flow")
    void testInmemoryTrajectoryStoreStatusFlow() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.saveSample(sample("s1"), "online");
        store.saveSample(sample("s2"), "online");

        assertThat(store.getPendingCount("online")).isEqualTo(2);
        assertThat(store.getUsersAboveThreshold(2)).containsExactly("online");

        List<Map<String, Object>> samples = store.fetchAndMarkTraining("online", 2);
        assertThat(samples.stream().map(item -> String.valueOf(item.get("sample_id"))).toList())
                .containsExactly("s1", "s2");

        store.markTrained(List.of("s1"));
        store.markFailed(List.of("s2"));
        assertThat(store.stats()).containsEntry("pending_samples", 0)
                .containsEntry("trained_samples", 1)
                .containsEntry("failed_samples", 1);
    }

    @Test
    @DisplayName("redis trajectory store status flow")
    void testRedisTrajectoryStoreStatusFlow() {
        RedisTrajectoryStore store = new RedisTrajectoryStore(new FakeRedis());
        store.saveSample(sample("s1"), "online");
        store.saveSample(sample("s2"), "online");

        assertThat(store.getPendingCount("online")).isEqualTo(2);
        assertThat(store.getUsersAboveThreshold(2)).containsExactly("online");

        List<Map<String, Object>> samples = store.fetchAndMarkTraining("online", 2);
        assertThat(samples.stream().map(item -> String.valueOf(item.get("sample_id"))).toList())
                .containsExactly("s1", "s2");

        store.markTrained(List.of("s1"));
        store.resetToPending(List.of("s2"));
        assertThat(store.stats()).containsEntry("pending_samples", 1)
                .containsEntry("trained_samples", 1)
                .containsEntry("failed_samples", 0);
    }

    @Test
    @DisplayName("redis trajectory store save sample replaces old status index")
    void testRedisTrajectoryStoreSaveSampleReplacesOldStatusIndex() {
        RedisTrajectoryStore store = new RedisTrajectoryStore(new FakeRedis());
        store.saveSample(sample("s1"), "online");
        store.fetchAndMarkTraining("online", 1);

        store.saveSample(sample("s1"), "online");

        assertThat(store.stats()).containsEntry("pending_samples", 1)
                .containsEntry("training_samples", 0);
    }

    @Test
    @DisplayName("redis trajectory store update status tolerates missing payload")
    void testRedisTrajectoryStoreUpdateStatusToleratesMissingPayload() {
        FakeRedis redis = new FakeRedis();
        RedisTrajectoryStore store = new RedisTrajectoryStore(redis);
        store.saveSample(sample("s1"), "online");
        store.fetchAndMarkTraining("online", 1);

        redis.hashes.get("rl:traj:s1").put("sample_json", null);
        store.markTrained(List.of("s1"));

        assertThat(store.stats()).containsEntry("pending_samples", 0)
                .containsEntry("training_samples", 1)
                .containsEntry("trained_samples", 0);
    }

    @Test
    @DisplayName("publish and get latest")
    void testPublishAndGetLatest() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        Path loraDir = makeLoraDir("adapter");

        LoRARepository.LoRAVersion version = repo.publish(
                "user1",
                loraDir.toString(),
                Map.of("trajectory_count", 10, "reward_avg", 0.6),
                ""
        );

        assertThat(version.version()).isEqualTo("v1");
        assertThat(version.trajectoryCount()).isEqualTo(10);
        assertThat(repo.getLatest("user1")).isPresent();
        assertThat(repo.getLatest("user1").orElseThrow().version()).isEqualTo("v1");
    }

    @Test
    @DisplayName("latest points to newest")
    void testLatestPointsToNewest() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        for (int i = 0; i < 3; i++) {
            repo.publish("user1", makeLoraDir("adapter-" + i).toString(),
                    Map.of("trajectory_count", i, "reward_avg", 0.0), "");
        }

        assertThat(repo.getLatest("user1").orElseThrow().version()).isEqualTo("v3");
    }

    @Test
    @DisplayName("get latest returns none for new user")
    void testGetLatestReturnsNoneForNewUser() {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());

        assertThat(repo.getLatest("no_such_user")).isEmpty();
    }

    @Test
    @DisplayName("publish accepts scheduler metadata keys")
    void testPublishAcceptsSchedulerMetadataKeys() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());

        LoRARepository.LoRAVersion version = repo.publish(
                "user1",
                makeLoraDir("adapter").toString(),
                Map.of("sample_count", 12, "avg_score", 0.75),
                ""
        );

        assertThat(version.trajectoryCount()).isEqualTo(12);
        assertThat(version.rewardAvg()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("publish ignores non-numeric version dirs")
    void testPublishIgnoresNonNumericVersionDirs() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        Files.createDirectories(repo.getRepoPath().resolve("user1").resolve("v_test"));

        LoRARepository.LoRAVersion version = repo.publish("user1", makeLoraDir("adapter").toString());

        assertThat(version.version()).isEqualTo("v1");
    }

    private Path makeLoraDir(String name) throws Exception {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("adapter_model.safetensors"), "dummy");
        return dir;
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
                List<Map.Entry<Object, Double>> ordered =
                        new ArrayList<>(zsets.getOrDefault(pendingKey, Map.of()).entrySet());
                ordered.sort(Map.Entry.comparingByValue());
                List<Object> ids = ordered.stream().limit(limit).map(Map.Entry::getKey).toList();
                for (Object sampleId : ids) {
                    zsets.getOrDefault(pendingKey, new LinkedHashMap<>()).remove(sampleId);
                    zsets.computeIfAbsent(trainingKey, ignored -> new LinkedHashMap<>()).put(sampleId, nowScore);
                    hashes.computeIfAbsent(trajPrefix + sampleId, ignored -> new LinkedHashMap<>())
                            .put("status", newStatus);
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
