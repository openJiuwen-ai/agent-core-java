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
import java.util.List;
import java.util.Map;

/**
 * Redis checkpointer cluster-mode selection contracts (ported from branch 730, adapted to develop).
 */
class RedisCheckpointerCompatibilityTest {

    @Test
    void urlWithClusterModeInsideConnectionArgsSelectsClusterStore() {
        Map<String, Object> connectionArgs = new LinkedHashMap<>();
        connectionArgs.put("cluster_mode", true);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("url", "redis://cluster.example.invalid:7001");
        connection.put("connection_args", connectionArgs);

        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("connection", connection);

        try (RedisCheckpointer checkpointer = assertInstanceOf(RedisCheckpointer.class,
                CheckpointerFactory.create("redis", conf))) {
            // develop uses UrlBackedRedisClusterClient for url + cluster_mode (not JedisCluster ctor)
            assertThat(checkpointer.getRedisStore().isCluster()).isTrue();
        }
    }

    @Test
    void nodesConfigConstructsJedisClusterClient() {
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("nodes", List.of("127.0.0.1:7001", "127.0.0.1:7002"));
        connection.put("cluster_mode", true);

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
