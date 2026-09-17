/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.store.kv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;

import java.nio.charset.StandardCharsets;
import java.util.List;

class JedisPipelineCompatibilityTest {
    @Test
    void mixedRepliesStayAlignedAndExpiryUsesOneCommand() {
        JedisPooled client = mock(JedisPooled.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(client.pipelined()).thenReturn(pipeline);
        byte[] binary = { (byte) 0xac, (byte) 0xed, 0, 5 };
        when(pipeline.syncAndReturnAll()).thenReturn(List.of("OK", binary, true));
        var replies = new RedisStore(client).pipeline().set("k", binary, 30).get("k").isExists("k").execute();
        assertNull(replies.get(0));
        assertArrayEquals(binary, (byte[]) replies.get(1));
        assertEquals(true, replies.get(2));
        verify(pipeline).setex("k".getBytes(StandardCharsets.UTF_8), 30, binary);
        verify(pipeline).syncAndReturnAll();
        verify(pipeline).close();
    }

    @Test
    void serverCommandErrorAndMismatchedRepliesCannotBecomeSuccessfulWrites() {
        JedisPooled client = mock(JedisPooled.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(client.pipelined()).thenReturn(pipeline);
        when(pipeline.syncAndReturnAll()).thenReturn(List.of(new IllegalStateException("write denied")));
        assertThrows(IllegalStateException.class,
                () -> new RedisStore(client).pipeline().set("k", "v").execute());
        when(pipeline.syncAndReturnAll()).thenReturn(List.of());
        assertThrows(IllegalStateException.class,
                () -> new RedisStore(client).pipeline().get("k").execute());
        verify(client, never()).set(anyString(), anyString(), any(redis.clients.jedis.params.SetParams.class));
    }

    @Test
    void uncertainPipelineFailureDoesNotReplayWrites() {
        JedisPooled client = mock(JedisPooled.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(client.pipelined()).thenReturn(pipeline);
        when(pipeline.syncAndReturnAll()).thenThrow(new IllegalStateException("connection lost"));
        assertThrows(IllegalStateException.class, () -> new RedisStore(client).pipeline().set("k", "v", 30).execute());
        verify(client, never()).set(anyString(), anyString(), any(redis.clients.jedis.params.SetParams.class));
        verify(pipeline).close();
    }
}
