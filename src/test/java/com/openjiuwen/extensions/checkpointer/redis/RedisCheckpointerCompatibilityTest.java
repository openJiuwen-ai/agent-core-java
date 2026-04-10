/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.extensions.store.kv.RedisStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCheckpointerCompatibilityTest {

    @Test
    void helperStyleClusterModeInsideConnectionArgsSelectsClusterClient() throws Exception {
        Map<String, Object> connectionArgs = new LinkedHashMap<>();
        connectionArgs.put("cluster_mode", true);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("url", "redis://127.0.0.1:7001");
        connection.put("connection_args", connectionArgs);

        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("connection", connection);

        RedisCheckpointer checkpointer = (RedisCheckpointer) CheckpointerFactory.create("redis", conf);
        Object redisClient = readRedisClient(checkpointer.getRedisStore());

        assertThat(redisClient.getClass().getSimpleName()).contains("Cluster");
    }

    private static Object readRedisClient(RedisStore redisStore) throws Exception {
        Field redisClientField = RedisStore.class.getDeclaredField("redisClient");
        redisClientField.setAccessible(true);
        return redisClientField.get(redisStore);
    }
}