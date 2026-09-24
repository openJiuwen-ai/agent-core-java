/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerProvider;

import java.util.List;
import java.util.Map;

/**
 * ServiceLoader entry point for the optional Redis checkpointer integration.
 *
 * <p>The provider itself has no static reference to a Jedis type. This allows
 * applications that exclude Jedis to initialize the other checkpointer
 * providers and fail only when Redis is explicitly selected.</p>
 */
public final class OptionalRedisCheckpointerProvider implements CheckpointerProvider {
    private static final String TYPE_NAME = "redis";
    private static final String LEGACY_TYPE_NAME = "redis_checkpointer_cluster";
    private static final String JEDIS_CLIENT_CONFIG = "redis.clients.jedis.JedisClientConfig";

    @Override
    public String typeName() {
        return TYPE_NAME;
    }

    @Override
    public List<String> aliases() {
        return List.of(LEGACY_TYPE_NAME);
    }

    @Override
    public Checkpointer create(Map<String, Object> conf) {
        requireJedis();
        return new RedisCheckpointer.Provider().create(conf);
    }

    private static void requireJedis() {
        try {
            Class.forName(JEDIS_CLIENT_CONFIG, false, OptionalRedisCheckpointerProvider.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Redis checkpointer requires the optional dependency redis.clients:jedis; "
                            + "add Jedis to the application runtime classpath",
                    exception);
        }
    }
}
