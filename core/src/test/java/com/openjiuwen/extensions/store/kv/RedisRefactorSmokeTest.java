/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.foundation.store.kv.ApplicationStorageScope;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.harness.tools.KvTodoStorage;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.spi.store.KVStoreFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EnabledIfSystemProperty(named = "redis.smoke.url", matches = ".+")
class RedisRefactorSmokeTest {
    @Test
    void realRedisSupportsSharedBinaryCheckpointTodoAndAtomicExpiry() throws Exception {
        String url = System.getProperty("redis.smoke.url");
        String key = "refactor:" + UUID.randomUUID();
        ApplicationStorageScope scope = new ApplicationStorageScope();
        RedisStore store = (RedisStore) scope.kvStores().create("default", "redis",
                Map.of("connection", Map.of("url", url)));
        RedisCheckpointer checkpointer = (RedisCheckpointer) CheckpointerFactory.create("redis",
                Map.of("storeRef", "default"), scope);
        boolean cluster = url.startsWith("redis+cluster:");
        URI endpoint = URI.create(url.replace("redis+cluster:", "redis:"));
        try (UnifiedJedis observer = cluster
                ? new JedisCluster(Set.of(new HostAndPort(endpoint.getHost(), endpoint.getPort())))
                : new JedisPooled(url)) {
            assertSame(store, checkpointer.getRedisStore());
            byte[] binary = new byte[] { (byte) 0xac, (byte) 0xed, 0, 5, 1 };
            store.set(key + ":bytes", binary, Duration.ofSeconds(30));
            assertArrayEquals(binary, (byte[]) store.get(key + ":bytes"));
            assertTrue(observer.ttl(key + ":bytes") > 0);
            assertTrue(store.exclusiveSet(key + ":lock", "a", 30));
            assertFalse(store.exclusiveSet(key + ":lock", "b", 30));
            assertTrue(observer.ttl(key + ":lock") > 0);
            KvTodoStorage todo = new KvTodoStorage(store, Duration.ofSeconds(2), true);
            todo.save(key, List.of(TodoItem.create("redis task")));
            assertEquals(1, todo.load(key).size());
            assertTrue(observer.ttl(key + ":todo") > 0);
            long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (observer.exists(key + ":todo") && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertFalse(observer.exists(key + ":todo"), "Todo must expire naturally on the server");
            assertTrue(todo.load(key).isEmpty());
            assertTrue(store.getByPrefix(key).containsKey(key + ":bytes"));
            store.pipeline().set(key + ":pipeline", binary, 30).get(key + ":pipeline").execute();
            assertArrayEquals(binary, (byte[]) store.get(key + ":pipeline"));
            assertTrue(observer.ttl(key + ":pipeline") > 0);
        } finally {
            store.deleteByPrefix(key, 100);
            store.close();
        }
    }

    @Test
    void businessTtlPoliciesRemainIndependentOnTheSharedServerStore() throws Exception {
        String url = System.getProperty("redis.smoke.url");
        String prefix = "policy:" + UUID.randomUUID();
        var store = (RedisStore) KVStoreFactory.create("redis", Map.of("connection", Map.of("url", url)));
        var checkpoint = new RedisCheckpointer(store, Map.of("default_ttl", 1, "refresh_on_read", true));
        var fast = new KvTodoStorage(store, Duration.ofSeconds(2), true);
        var slow = new KvTodoStorage(store, Duration.ofSeconds(30), false);
        var connection = new RedisConnectionConfig(url);
        try (UnifiedJedis observer = (UnifiedJedis) JedisRedisClientFactory.create(connection)) {
            var config = new Config();
            config.setAgentConfig(new Config.MetadataLike("agent", "agent", "invoke"));
            var session = new AgentSession(prefix, config, checkpoint);
            session.state().updateGlobal(Map.of("persisted", "checkpoint"));
            checkpoint.interruptAgentExecute(session);
            String checkpointKey = prefix + ":agent:agent:agent_state_blobs";
            fast.save(prefix + ":fast", List.of(TodoItem.create("fast")));
            slow.save(prefix + ":slow", List.of(TodoItem.create("slow")));
            assertTrue(observer.ttl(checkpointKey) > 50);
            assertTrue(observer.ttl(prefix + ":slow:todo") > 20);
            assertTrue(observer.ttl(prefix + ":fast:todo") <= 2);
            // Lower only fast TTL to observe its refresh without waiting close to the
            // expiry boundary.
            observer.pexpire(prefix + ":fast:todo", 1000);
            assertEquals(1, fast.load(prefix + ":fast").size());
            assertTrue(observer.pttl(prefix + ":fast:todo") > 1000);
            assertTrue(observer.ttl(prefix + ":slow:todo") > 20);
            assertTrue(observer.ttl(checkpointKey) > 50);
            long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (observer.exists(prefix + ":fast:todo") && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(fast.load(prefix + ":fast").isEmpty());
            assertEquals(1, slow.load(prefix + ":slow").size());
            var restored = new AgentSession(prefix, config, checkpoint);
            checkpoint.preAgentExecute(restored, null);
            assertEquals("checkpoint", restored.state().getGlobal("persisted"));
        } finally {
            store.deleteByPrefix(prefix, 100);
            store.close();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "redis.smoke.bad.url", matches = ".+")
    void invalidAuthenticationOrUntrustedTlsCannotSucceed() {
        String invalidUrl = System.getProperty("redis.smoke.bad.url");
        var store = (RedisStore) KVStoreFactory.create("redis", Map.of("connection", Map.of(
                "url", invalidUrl, "connection_args", Map.of("socket_connect_timeout", 2, "socket_timeout", 2))));
        try {
            assertThrows(RuntimeException.class, () -> store.get("connection-validation"));
        } finally {
            store.close();
        }
    }

}
