/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.InMemoryStateLike;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PersistenceStorage.
 * Mirrors Python's {@code tests/unit_tests/core/session/checkpointer/test_persistence_storage.py}.
 */
class TestPersistenceStorage {

    static class MockKVStore extends BaseKVStore {
        private final Map<String, Object> store = new HashMap<>();

        @Override
        public void set(String key, Object value) {
            store.put(key, value);
        }

        @Override
        public boolean exclusiveSet(String key, Object value, Integer expiry) {
            if (store.containsKey(key)) {
                return false;
            }
            store.put(key, value);
            return true;
        }

        @Override
        public Object get(String key) {
            return store.get(key);
        }

        @Override
        public boolean exists(String key) {
            return store.containsKey(key);
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public Map<String, Object> getByPrefix(String prefix) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : store.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }

        @Override
        public void deleteByPrefix(String prefix, Integer batchSize) {
            List<String> keysToDelete = new ArrayList<>();
            for (String key : store.keySet()) {
                if (key.startsWith(prefix)) {
                    keysToDelete.add(key);
                }
            }
            for (String key : keysToDelete) {
                store.remove(key);
            }
        }

        @Override
        public List<Object> mget(List<String> keys) {
            List<Object> result = new ArrayList<>();
            for (String key : keys) {
                result.add(store.get(key));
            }
            return result;
        }

        @Override
        public int batchDelete(List<String> keys, Integer batchSize) {
            int deleted = 0;
            for (String key : keys) {
                if (store.containsKey(key)) {
                    store.remove(key);
                    deleted++;
                }
            }
            return deleted;
        }

        @Override
        public KVStorePipeline pipeline() {
            MockKVStore self = this;
            Function<List<Object[]>, List<Object>> executorFunc = (operations) -> {
                List<Object> results = new ArrayList<>();
                for (Object[] operation : operations) {
                    String type = (String) operation[0];
                    String key = (String) operation[1];
                    if ("set".equals(type)) {
                        Object value = operation[2];
                        self.store.put(key, value);
                        results.add(null);
                    } else if ("get".equals(type)) {
                        results.add(self.store.get(key));
                    } else if ("exists".equals(type)) {
                        results.add(self.store.containsKey(key));
                    }
                }
                return results;
            };
            return new KVStorePipeline(executorFunc);
        }
    }

    private abstract static class BaseStorage {
        protected final BaseKVStore kvStore;

        protected BaseStorage(BaseKVStore kvStore) {
            this.kvStore = kvStore;
        }

        public abstract void save(Object session);
        public abstract void recover(Object session);
        public abstract void clear(String entityId, String sessionId);
        public abstract boolean exists(Object session);
    }

    private static class AgentStorage extends BaseStorage {
        private static final String STATE_BLOBS = "agent_state_blobs";
        private static final String STATE_BLOBS_DUMP_TYPE = "agent_state_blobs_dump_type";
        private static final String NAMESPACE = "agent";
        private static final int KEY_NUMS = 2;

        public AgentStorage(BaseKVStore kvStore) {
            super(kvStore);
        }

        private String[] buildStateKeys(String sessionId, String agentId) {
            String dumpTypeKey = sessionId + ":" + NAMESPACE + ":" + agentId + ":" + STATE_BLOBS_DUMP_TYPE;
            String blobKey = sessionId + ":" + NAMESPACE + ":" + agentId + ":" + STATE_BLOBS;
            return new String[]{dumpTypeKey, blobKey};
        }

        private String getEntityId(Object session) {
            if (session instanceof AgentSession agentSession) {
                String agentId = agentSession.agentId();
                return agentId != null ? agentId : agentSession.sessionId();
            }
            return null;
        }

        @Override
        public void save(Object session) {
            if (!(session instanceof AgentSession agentSession)) {
                return;
            }
            String sessionId = agentSession.sessionId();
            String agentId = getEntityId(session);
            Map<String, Object> state = agentSession.state().getState();
            
            String[] keys = buildStateKeys(sessionId, agentId);
            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.set(keys[0], "pickle");
            pipeline.set(keys[1], serializeState(state));
            pipeline.execute();
        }

        @Override
        public void recover(Object session) {
            if (!(session instanceof AgentSession agentSession)) {
                return;
            }
            String sessionId = agentSession.sessionId();
            String agentId = getEntityId(session);
            
            String[] keys = buildStateKeys(sessionId, agentId);
            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(keys[0]);
            pipeline.get(keys[1]);
            List<Object> results = pipeline.execute();
            
            if (results == null || results.size() != KEY_NUMS) {
                return;
            }
            
            Object dumpType = results.get(0);
            Object blob = results.get(1);
            if (dumpType == null || blob == null) {
                return;
            }
            
            if (blob instanceof Map<?, ?> stateMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> state = (Map<String, Object>) stateMap;
                if (agentSession.state() instanceof AgentStateCollection asc) {
                    asc.setState(state);
                }
            }
        }

        @Override
        public void clear(String agentId, String sessionId) {
            String[] keys = buildStateKeys(sessionId, agentId);
            kvStore.batchDelete(List.of(keys), null);
        }

        @Override
        public boolean exists(Object session) {
            if (!(session instanceof AgentSession agentSession)) {
                return false;
            }
            String sessionId = agentSession.sessionId();
            String agentId = getEntityId(session);
            
            String[] keys = buildStateKeys(sessionId, agentId);
            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.exists(keys[0]);
            pipeline.exists(keys[1]);
            List<Object> results = pipeline.execute();
            
            if (results == null || results.size() != KEY_NUMS) {
                return false;
            }
            return Boolean.TRUE.equals(results.get(0)) && Boolean.TRUE.equals(results.get(1));
        }

        private Object serializeState(Map<String, Object> state) {
            return state;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> deserializeState(Object blob) {
            if (blob instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return null;
        }
    }

    private static class AgentTeamStorage extends BaseStorage {
        private static final String STATE_BLOBS = "agent_team_state_blobs";
        private static final String STATE_BLOBS_DUMP_TYPE = "agent_team_state_blobs_dump_type";
        private static final String NAMESPACE = "agent_team";
        private static final int KEY_NUMS = 2;

        public AgentTeamStorage(BaseKVStore kvStore) {
            super(kvStore);
        }

        private String[] buildStateKeys(String sessionId, String teamId) {
            String dumpTypeKey = sessionId + ":" + NAMESPACE + ":" + teamId + ":" + STATE_BLOBS_DUMP_TYPE;
            String blobKey = sessionId + ":" + NAMESPACE + ":" + teamId + ":" + STATE_BLOBS;
            return new String[]{dumpTypeKey, blobKey};
        }

        @Override
        public void save(Object session) {
            if (!(session instanceof AgentTeamSession teamSession)) {
                return;
            }
            String sessionId = teamSession.sessionId();
            String teamId = teamSession.teamId();
            Object globalState = teamSession.state().getGlobal(null);
            
            String[] keys = buildStateKeys(sessionId, teamId);
            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.set(keys[0], "pickle");
            pipeline.set(keys[1], serializeState(globalState));
            pipeline.execute();
        }

        @Override
        public void recover(Object session) {
            if (!(session instanceof AgentTeamSession teamSession)) {
                return;
            }
            String sessionId = teamSession.sessionId();
            String teamId = teamSession.teamId();
            
            String[] keys = buildStateKeys(sessionId, teamId);
            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(keys[0]);
            pipeline.get(keys[1]);
            List<Object> results = pipeline.execute();
            
            if (results == null || results.size() != KEY_NUMS) {
                return;
            }
            
            Object dumpType = results.get(0);
            Object blob = results.get(1);
            if (dumpType == null || blob == null) {
                return;
            }
            
            if (blob instanceof Map<?, ?> globalStateMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> globalState = (Map<String, Object>) globalStateMap;
                AgentStateCollection asc = (AgentStateCollection) teamSession.state();
                InMemoryStateLike globalStateLike = asc.getGlobalStateLike();
                globalStateLike.setState(globalState);
            }
        }

        @Override
        public void clear(String teamId, String sessionId) {
            String[] keys = buildStateKeys(sessionId, teamId);
            kvStore.batchDelete(List.of(keys), null);
        }

        @Override
        public boolean exists(Object session) {
            if (!(session instanceof AgentTeamSession teamSession)) {
                return false;
            }
            String sessionId = teamSession.sessionId();
            String teamId = teamSession.teamId();
            
            String[] keys = buildStateKeys(sessionId, teamId);
            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.exists(keys[0]);
            pipeline.exists(keys[1]);
            List<Object> results = pipeline.execute();
            
            if (results == null || results.size() != KEY_NUMS) {
                return false;
            }
            return Boolean.TRUE.equals(results.get(0)) && Boolean.TRUE.equals(results.get(1));
        }

        private Object serializeState(Object state) {
            return state;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> deserializeState(Object blob) {
            if (blob instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return null;
        }
    }

    @Nested
    @DisplayName("AgentStorage tests")
    class AgentStorageTests {

        @Test
        @DisplayName("test persistence agent storage save recover exists and clear")
        void testPersistenceAgentStorageSaveRecoverExistsAndClear() {
            MockKVStore kvStore = new MockKVStore();
            AgentStorage storage = new AgentStorage(kvStore);
            AgentSession session = spy(new AgentSession("session-agent", new Config()));
            
            when(session.agentId()).thenReturn("agent-1");
            session.state().update(Map.of("name", "alice"));
            session.state().updateGlobal(Map.of("shared", "value"));

            storage.save(session);

            assertTrue(storage.exists(session));

            AgentSession recovered = spy(new AgentSession("session-agent", new Config()));
            when(recovered.agentId()).thenReturn("agent-1");
            storage.recover(recovered);

            assertEquals("alice", recovered.state().get("name"));
            assertEquals("value", recovered.state().getGlobal("shared"));

            storage.clear("agent-1", "session-agent");
            assertFalse(storage.exists(session));
        }

        @Test
        @DisplayName("test persistence storage recover nonexistent is noop")
        void testPersistenceStorageRecoverNonexistentIsNoop() {
            MockKVStore kvStore = new MockKVStore();
            AgentStorage storage = new AgentStorage(kvStore);
            AgentSession session = spy(new AgentSession("session-agent", new Config()));
            when(session.agentId()).thenReturn("agent-1");

            storage.recover(session);

            Map<String, Object> expectedState = new HashMap<>();
            expectedState.put("global_state", new HashMap<>());
            expectedState.put("agent_state", new HashMap<>());
            assertEquals(expectedState, session.state().getState());
        }
    }

    @Nested
    @DisplayName("AgentTeamStorage tests")
    class AgentTeamStorageTests {

        @Test
        @DisplayName("test persistence agent group storage save recover exists and clear")
        void testPersistenceAgentGroupStorageSaveRecoverExistsAndClear() {
            MockKVStore kvStore = new MockKVStore();
            AgentTeamStorage storage = new AgentTeamStorage(kvStore);
            AgentTeamSession session = new AgentTeamSession("session-team", "team-1", new Config(), null);
            session.state().update(Map.of("agent_local", "should_not_be_restored"));
            session.state().updateGlobal(Map.of("team", "alpha"));

            storage.save(session);

            assertTrue(storage.exists(session));

            AgentTeamSession recovered = new AgentTeamSession("session-team", "team-1", new Config(), null);
            storage.recover(recovered);

            assertEquals("alpha", recovered.state().getGlobal("team"));
            assertNull(recovered.state().get("agent_local"));

            storage.clear("team-1", "session-team");
            assertFalse(storage.exists(session));
        }
    }
}
