/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentGroupStorage;
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentStorage;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test checkpointer agent storage functionality.
 *
 * <p>Mirrors Python's {@code test_agent_storage.py} in
 * {@code tests_unit_tests.extensions.checkpointer}.
 */
class TestAgentStorage {

    @Nested
    class TestStorageOperations {

        @Test
        void testStoreAgentSession() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession session = session("session-1", "agent-1");
            session.state().update(Map.of("key1", "value1", "key2", 42));

            storage.save(session).join();

            assertTrue(storage.exists(session).join());
        }

        @Test
        void testRetrieveAgentSession() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession saved = session("session-1", "agent-1");
            saved.state().update(Map.of("key1", "value1", "key2", 42));
            storage.save(saved).join();

            AgentSession restored = session("session-1", "agent-1");
            storage.recover(restored, null).join();

            assertEquals("value1", restored.state().get("key1"));
            assertEquals(42, restored.state().get("key2"));
        }

        @Test
        void testDeleteAgentSession() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession session = session("session-1", "agent-1");
            session.state().update(Map.of("key", "value"));
            storage.save(session).join();

            storage.clear("agent-1", "session-1").join();

            assertFalse(storage.exists(session).join());
        }

        @Test
        void testAgentStorageSaveAndRecover() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession saved = session("session-2", "agent-2");
            saved.state().update(Map.of("key1", "value1", "key2", 42, "list", List.of(1, 2, 3)));

            storage.save(saved).join();
            AgentSession recovered = session("session-2", "agent-2");
            storage.recover(recovered, null).join();

            assertEquals("value1", recovered.state().get("key1"));
            assertEquals(42, recovered.state().get("key2"));
            assertEquals(List.of(1, 2, 3), recovered.state().get("list"));
        }

        @Test
        void testAgentStorageSaveEmptyState() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession session = session("session-empty", "agent-empty");

            storage.save(session).join();

            assertTrue(storage.exists(session).join());
        }

        @Test
        void testAgentStorageRecoverNonexistentState() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession session = session("missing-session", "missing-agent");

            storage.recover(session, null).join();

            assertNull(session.state().get("key1"));
            assertEquals(Map.of(), session.state().get(null));
        }

        @Test
        void testAgentStorageExists() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession session = session("session-exists", "agent-exists");

            assertFalse(storage.exists(session).join());
            session.state().update(Map.of("key", "value"));
            storage.save(session).join();

            assertTrue(storage.exists(session).join());
        }

        @Test
        void testAgentStorageClear() {
            AgentStorage storage = new AgentStorage(redisStore(), null);
            AgentSession session = session("session-clear", "agent-clear");
            session.state().update(Map.of("key", "value"));
            storage.save(session).join();

            storage.clear("agent-clear", "session-clear").join();

            assertFalse(storage.exists(session).join());
        }

        @Test
        void testAgentStorageTtlRefreshOnRead() {
            FakeRedisClient fakeRedis = new FakeRedisClient();
            RedisStore redisStore = new RedisStore(fakeRedis);
            AgentStorage storage = new AgentStorage(redisStore, Map.of("default_ttl", 1, "refresh_on_read", true));
            AgentSession session = session("session-ttl", "agent-ttl");
            session.state().update(Map.of("key", "value"));
            storage.save(session).join();
            String dumpTypeKey = Checkpointer.buildKeyWithNamespace(
                    "session-ttl", Checkpointer.SESSION_NAMESPACE_AGENT, "agent-ttl", "agent_state_blobs_dump_type");

            sleep(20);
            storage.recover(session, null).join();

            assertTrue(fakeRedis.ttl(dumpTypeKey) >= 59);
        }

        @Test
        void testAgentGroupStorageSaveAndRecover() {
            AgentGroupStorage storage = new AgentGroupStorage(redisStore(), null);
            AgentTeamSession saved = new AgentTeamSession("group_session", "group_a", new Config(), null);
            saved.state().update(Map.of("agent_local", "ignored"));
            saved.state().updateGlobal(Map.of("shared_key", "shared_value", "counter", 3));

            storage.save(saved).join();
            AgentTeamSession recovered = new AgentTeamSession("group_session", "group_a", new Config(), null);
            recovered.state().update(Map.of("agent_local", "local"));
            storage.recover(recovered, null).join();

            assertEquals("shared_value", recovered.state().getGlobal("shared_key"));
            assertEquals(3, recovered.state().getGlobal("counter"));
            assertEquals("local", recovered.state().get("agent_local"));
        }

        @Test
        void testAgentGroupStorageClear() {
            AgentGroupStorage storage = new AgentGroupStorage(redisStore(), null);
            AgentTeamSession session = new AgentTeamSession("group_session", "group_a", new Config(), null);
            session.state().updateGlobal(Map.of("shared_key", "shared_value"));
            storage.save(session).join();

            storage.clear("group_a", "group_session").join();

            assertFalse(storage.exists(session).join());
        }

        @Test
        void testAgentGroupStorageTtlRefreshOnRead() {
            FakeRedisClient fakeRedis = new FakeRedisClient();
            RedisStore redisStore = new RedisStore(fakeRedis);
            AgentGroupStorage storage = new AgentGroupStorage(
                    redisStore, Map.of("default_ttl", 1, "refresh_on_read", true));
            AgentTeamSession session = new AgentTeamSession("group_session_ttl", "group_a", new Config(), null);
            session.state().updateGlobal(Map.of("shared_key", "shared_value"));
            storage.save(session).join();
            String dumpTypeKey = Checkpointer.buildKeyWithNamespace(
                    "group_session_ttl",
                    Checkpointer.SESSION_NAMESPACE_AGENT_TEAM,
                    "group_a",
                    "agent_group_state_blobs_dump_type");

            sleep(20);
            storage.recover(session, null).join();

            assertTrue(fakeRedis.ttl(dumpTypeKey) >= 59);
        }
    }

    private AgentSession session(String sessionId, String agentId) {
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike(agentId, "agent", "invoke"));
        return new AgentSession(sessionId, config, null);
    }

    private RedisStore redisStore() {
        return new RedisStore(new FakeRedisClient());
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        public boolean set(String key, Object value, boolean nx, Integer expiry) {
            cleanup(key);
            if (nx && values.containsKey(key)) {
                return false;
            }
            values.put(key, value);
            if (expiry != null && expiry > 0) {
                expiryAt.put(key, System.currentTimeMillis() + expiry * 1000L);
            } else {
                expiryAt.remove(key);
            }
            return true;
        }

        public Object get(String key) {
            cleanup(key);
            return values.get(key);
        }

        public long exists(String key) {
            cleanup(key);
            return values.containsKey(key) ? 1L : 0L;
        }

        public long delete(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                cleanup(key);
                if (values.remove(key) != null) {
                    expiryAt.remove(key);
                    deleted++;
                }
            }
            return deleted;
        }

        public List<Object> mget(String... keys) {
            List<Object> results = new ArrayList<>(keys.length);
            for (String key : keys) {
                results.add(get(key));
            }
            return results;
        }

        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> keys = new ArrayList<>();
            for (String key : new ArrayList<>(values.keySet())) {
                cleanup(key);
                if (values.containsKey(key) && key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            return keys;
        }

        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
            return true;
        }

        long ttl(String key) {
            cleanup(key);
            Long expiresAt = expiryAt.get(key);
            if (expiresAt == null) {
                return -1L;
            }
            return Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
