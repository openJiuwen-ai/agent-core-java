/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStore;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Own scored-sample persistence and rail-v1 ingestion wiring.
 * <p>
 * Mirrors Python's {@code GatewayTrajectoryRuntime} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/persistence.py}.
 */
public final class GatewayTrajectoryRuntime implements SampleRecordingSink {

    private static final String SINGLE_USER_DEFAULT_ID = "jiuwenclaw-web";

    private final String defaultUserId;
    private final RedisTrajectoryStore trajectoryStore;
    private final SampleRecorder sampleRecorder;
    private final PendingJudgeStore pendingJudgeStore;

    private RailBatchIngestor railIngestor;

    public GatewayTrajectoryRuntime(GatewayConfig config, Object redis) {
        Objects.requireNonNull(config, "GatewayTrajectoryRuntime requires config");
        if (redis == null) {
            throw new IllegalArgumentException("GatewayTrajectoryRuntime requires redis client");
        }
        ensureRecordDirectory(config.getRecordDir());
        this.defaultUserId = config.isSingleUserDefault() ? SINGLE_USER_DEFAULT_ID : "";
        this.trajectoryStore = new RedisTrajectoryStore(new ReflectiveRedisTrajectoryBackend(redis));
        this.sampleRecorder = new SampleRecorder(
                Path.of(config.getRecordDir(), "samples.jsonl"),
                config.isDumpTokenIds()
        );
        this.pendingJudgeStore = new PendingJudgeStore(new ReflectivePendingJudgeBackend(redis));
        setJudgeScorer(null);
    }

    public String getStoreBackend() {
        return trajectoryStore.getClass().getSimpleName();
    }

    public RailBatchIngestor getRailIngestor() {
        if (railIngestor == null) {
            throw new IllegalStateException("railIngestor is not initialized");
        }
        return railIngestor;
    }

    public void setJudgeScorer(JudgeScorer judgeScorer) {
        JudgeDispatcher judgeDispatcher = new JudgeDispatcher(
                pendingJudgeStore,
                this,
                judgeScorer
        );
        this.railIngestor = new RailBatchIngestor(
                pendingJudgeStore,
                judgeDispatcher,
                defaultUserId
        );
    }

    @Override
    public void recordSample(Map<String, Object> sample) {
        Map<String, Object> normalized = new LinkedHashMap<>(sample);
        String normalizedUserId = String.valueOf(
                normalized.getOrDefault("user_id", defaultUserId)
        ).trim();
        if (normalizedUserId.isEmpty()) {
            throw new IllegalArgumentException("missing user_id; online training requires a stable user id");
        }
        normalized.put("user_id", normalizedUserId);
        trajectoryStore.saveSample(normalized, normalizedUserId);
        sampleRecorder.recordSample(normalized);
    }

    public Map<String, Object> snapshotStats() {
        Map<String, Integer> sampleStats = sampleRecorder.snapshotStats();
        Map<String, Integer> trainStats = trajectoryStore.stats();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_samples", sampleStats.get("total_samples"));
        stats.put("trajectory_store_backend", getStoreBackend());
        stats.put("trajectory_store_total", trainStats.get("total_samples"));
        stats.put("trajectory_store_pending", trainStats.get("pending_samples"));
        stats.put("trajectory_store_training", trainStats.get("training_samples"));
        stats.put("trajectory_store_trained", trainStats.get("trained_samples"));
        stats.put("trajectory_store_failed", trainStats.get("failed_samples"));
        return stats;
    }

    private static void ensureRecordDirectory(String recordDir) {
        try {
            Files.createDirectories(Path.of(recordDir));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create gateway record directory", exception);
        }
    }

    private static final class ReflectiveRedisTrajectoryBackend implements RedisTrajectoryStoreBackend {
        private final Object redis;

        private ReflectiveRedisTrajectoryBackend(Object redis) {
            this.redis = redis;
        }

        @Override
        public RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
            Object script = invoke(redis, "registerFetchAndMarkScript", luaSource);
            return (keys, args) -> castList(invoke(script, "execute", keys, args));
        }

        @Override
        public List<Object> hmget(String key, List<String> fields) {
            return castList(invoke(redis, "hmget", key, fields));
        }

        @Override
        public Object hget(String key, String field) {
            return invoke(redis, "hget", key, field);
        }

        @Override
        public long hset(String key, Map<String, Object> mapping) {
            return toLong(invoke(redis, "hset", key, mapping));
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            return toLong(invoke(redis, "zadd", key, mapping));
        }

        @Override
        public long zcard(String key) {
            return toLong(invoke(redis, "zcard", key));
        }

        @Override
        public long zrem(String key, Object... members) {
            return toLong(invoke(redis, "zrem", key, members));
        }

        @Override
        public long sadd(String key, Object... members) {
            return toLong(invoke(redis, "sadd", key, members));
        }

        @Override
        public long srem(String key, Object... members) {
            return toLong(invoke(redis, "srem", key, members));
        }

        @Override
        public java.util.Set<Object> smembers(String key) {
            @SuppressWarnings("unchecked")
            java.util.Set<Object> value = (java.util.Set<Object>) invoke(redis, "smembers", key);
            return value;
        }

        @Override
        public RedisTrajectoryStorePipeline pipeline() {
            Object pipeline = invoke(redis, "pipeline");
            return new ReflectiveRedisTrajectoryPipeline(pipeline);
        }
    }

    private static final class ReflectiveRedisTrajectoryPipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline {
        private final Object pipeline;

        private ReflectiveRedisTrajectoryPipeline(Object pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) {
            invoke(pipeline, "zrem", key, members);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) {
            invoke(pipeline, "hset", key, mapping);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) {
            invoke(pipeline, "zadd", key, mapping);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline sadd(String key, Object... members) {
            invoke(pipeline, "sadd", key, members);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zcard(String key) {
            invoke(pipeline, "zcard", key);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hget(String key, String field) {
            invoke(pipeline, "hget", key, field);
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hmget(String key, List<String> fields) {
            invoke(pipeline, "hmget", key, fields);
            return this;
        }

        @Override
        public List<Object> execute() {
            return castList(invoke(pipeline, "execute"));
        }
    }

    private static final class ReflectivePendingJudgeBackend implements PendingJudgeStoreBackend {
        private final Object redis;

        private ReflectivePendingJudgeBackend(Object redis) {
            this.redis = redis;
        }

        @Override
        public void set(String key, String value, int ttlSeconds) {
            invoke(redis, "set", key, value, ttlSeconds);
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            return toLong(invoke(redis, "zadd", key, mapping));
        }

        @Override
        public long expire(String key, int ttlSeconds) {
            return toLong(invoke(redis, "expire", key, ttlSeconds));
        }

        @Override
        public List<Object> zrange(String key, int start, int end) {
            return castList(invoke(redis, "zrange", key, start, end));
        }

        @Override
        public List<Object> mget(List<String> keys) {
            return castList(invoke(redis, "mget", keys));
        }

        @Override
        public Object get(String key) {
            return invoke(redis, "get", key);
        }

        @Override
        public PendingJudgeStorePipeline pipeline() {
            Object pipeline = invoke(redis, "pipeline");
            return new ReflectivePendingJudgePipeline(pipeline);
        }
    }

    private static final class ReflectivePendingJudgePipeline implements PendingJudgeStoreBackend.PendingJudgeStorePipeline {
        private final Object pipeline;

        private ReflectivePendingJudgePipeline(Object pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public PendingJudgeStoreBackend.PendingJudgeStorePipeline delete(String key) {
            invoke(pipeline, "delete", key);
            return this;
        }

        @Override
        public PendingJudgeStoreBackend.PendingJudgeStorePipeline zrem(String key, Object member) {
            invoke(pipeline, "zrem", key, member);
            return this;
        }

        @Override
        public List<Object> execute() {
            return castList(invoke(pipeline, "execute"));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        Method method = findMethod(target.getClass(), methodName, args.length);
        if (method == null) {
            throw new IllegalStateException("Missing redis method: " + methodName + "/" + args.length);
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke redis method: " + methodName, exception);
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }
}
