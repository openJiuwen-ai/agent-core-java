/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mockConstruction;

import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;

import redis.clients.jedis.JedisCluster;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.LinkedHashMap;
import java.util.Map;

class RedisCheckpointerCompatibilityTest {
    @Test
    void helperStyleClusterModeInsideConnectionArgsSelectsClusterClient() {
        Map<String, Object> connectionArgs = new LinkedHashMap<>();
        connectionArgs.put("cluster_mode", true);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("url", "redis://cluster.example.invalid:7001");
        connection.put("connection_args", connectionArgs);

        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("connection", connection);

        try (MockedConstruction<JedisCluster> clusterClientConstruction = mockConstruction(JedisCluster.class);
                RedisCheckpointer checkpointer = assertInstanceOf(RedisCheckpointer.class,
                        CheckpointerFactory.create("redis", conf))) {
            assertThat(clusterClientConstruction.constructed()).hasSize(1);
            assertThat(checkpointer.getRedisStore().isCluster()).isTrue();
        }
    }
}
