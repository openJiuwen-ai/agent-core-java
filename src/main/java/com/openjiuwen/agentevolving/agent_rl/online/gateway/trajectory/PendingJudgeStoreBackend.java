/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import java.util.List;
import java.util.Map;

/**
 * Minimal Redis-like backend contract for pending judge samples.
 * <p>
 * Mirrors Python's Redis client surface consumed by
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/pending_judge_store.py}.
 */
public interface PendingJudgeStoreBackend {

    void set(String key, String value, int ttlSeconds);

    long zadd(String key, Map<String, Double> mapping);

    long expire(String key, int ttlSeconds);

    List<Object> zrange(String key, int start, int end);

    List<Object> mget(List<String> keys);

    Object get(String key);

    PendingJudgeStorePipeline pipeline();

    interface PendingJudgeStorePipeline {
        PendingJudgeStorePipeline delete(String key);

        PendingJudgeStorePipeline zrem(String key, Object member);

        List<Object> execute();
    }
}
