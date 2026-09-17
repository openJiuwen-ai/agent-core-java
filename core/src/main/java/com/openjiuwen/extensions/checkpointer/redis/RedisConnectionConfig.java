/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.checkpointer.redis;

import java.util.Map;

/** Compatibility entrypoint for the shared Redis connection model. */
public class RedisConnectionConfig extends com.openjiuwen.extensions.store.kv.RedisConnectionConfig {
    public RedisConnectionConfig() {
        super();
    }

    public RedisConnectionConfig(String url) {
        super(url);
    }

    public RedisConnectionConfig(Object client) {
        super(client);
    }

    public static RedisConnectionConfig fromMap(Map<String, Object> input) {
        var parsed = com.openjiuwen.extensions.store.kv.RedisConnectionConfig.fromMap(input);
        RedisConnectionConfig result = new RedisConnectionConfig();
        result.setRedisClient(parsed.getRedisClient());
        result.setUrl(parsed.getUrl());
        result.setClusterMode(parsed.getClusterMode());
        result.setConnectionArgs(parsed.getConnectionArgs());
        return result;
    }
}
