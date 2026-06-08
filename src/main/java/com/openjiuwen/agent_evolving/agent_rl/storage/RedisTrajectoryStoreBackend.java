/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal Redis-like backend contract for {@link RedisTrajectoryStore}.
 * <p>
 * Mirrors Python's Redis client surface consumed by
 * {@code openjiuwen/agent_evolving/agent_rl/storage/redis_trajectory_store.py}.
 */
public interface RedisTrajectoryStoreBackend {

    RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource);

    List<Object> hmget(String key, List<String> fields);

    Object hget(String key, String field);

    long hset(String key, Map<String, Object> mapping);

    long zadd(String key, Map<String, Double> mapping);

    long zcard(String key);

    long zrem(String key, Object... members);

    long sadd(String key, Object... members);

    long srem(String key, Object... members);

    Set<Object> smembers(String key);

    RedisTrajectoryStorePipeline pipeline();

    interface RedisTrajectoryStoreFetchScript {
        List<Object> execute(List<String> keys, List<Object> args);
    }

    interface RedisTrajectoryStorePipeline {
        RedisTrajectoryStorePipeline zrem(String key, Object... members);

        RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping);

        RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping);

        RedisTrajectoryStorePipeline sadd(String key, Object... members);

        RedisTrajectoryStorePipeline zcard(String key);

        RedisTrajectoryStorePipeline hget(String key, String field);

        RedisTrajectoryStorePipeline hmget(String key, List<String> fields);

        List<Object> execute();
    }
}
