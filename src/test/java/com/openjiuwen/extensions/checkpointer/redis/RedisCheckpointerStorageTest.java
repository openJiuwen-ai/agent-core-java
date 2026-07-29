
package com.openjiuwen.extensions.checkpointer.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import org.junit.jupiter.api.Test;

import java.io.NotSerializableException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

class RedisCheckpointerStorageTest {
    @Test
    void preAgentExecuteRestoresStateQueuesInputsAndRefreshesTtl() throws Exception {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                    Map.of("default_ttl", 1, "refresh_on_read", true));

        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike("agent-1", "agent", "invoke"));

        AgentSession session = new AgentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(session, null);
        session.state().updateGlobal(Map.of("persisted", "value"));
        checkpointer.interruptAgentExecute(session);

        String ttlKey = "session-1:agent:agent-1:agent_state_blobs_dump_type";
        Thread.sleep(20L);

        AgentSession restored = new AgentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, "hello");

        assertEquals("value", restored.state().getGlobal("persisted"));
        assertEquals(List.of("hello"), restored.state().get(Constant.INTERACTIVE_INPUT));
        assertTrue(redisClient.ttl(ttlKey) >= 59L, "recover should refresh TTL on read");
    }

    @Test
    void agentCheckpointPersistsInteractiveInputAcrossCheckpointerInstances() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer writer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient), null);
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike("agent-1", "agent", "invoke"));
        InteractiveInput input = new InteractiveInput();
        input.update("approval", Map.of("answer", "yes"));

        AgentSession session = new AgentSession("interactive-session", config, writer);
        writer.preAgentExecute(session, input);
        writer.interruptAgentExecute(session);

        assertEquals(1L, redisClient.exists("interactive-session:agent:agent-1:agent_state_blobs"));
        assertEquals(1L, redisClient.exists("interactive-session:agent:agent-1:agent_state_blobs_dump_type"));

        RedisCheckpointer reader =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient), null);
        AgentSession restored = new AgentSession("interactive-session", config, reader);
        reader.preAgentExecute(restored, null);

        List<?> restoredInputs = (List<?>) restored.state().get(Constant.INTERACTIVE_INPUT);
        InteractiveInput restoredInput = (InteractiveInput) restoredInputs.get(0);
        assertEquals(Map.of("answer", "yes"), restoredInput.getUserInputs().get("approval"));
    }

    @Test
    void agentCheckpointPropagatesSerializationFailureWithoutWritingState() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient), null);
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike("agent-1", "agent", "invoke"));
        AgentSession session = new AgentSession("invalid-session", config, checkpointer);
        session.state().update(Map.of("invalid", new Object()));

        CompletionException error = assertThrows(CompletionException.class,
                () -> checkpointer.getAgentStorage().save(session).join());

        assertTrue(hasCause(error, NotSerializableException.class));
        assertFalse(checkpointer.sessionExists("invalid-session"));
    }

    @Test
    void workflowLifecycleRestoresStateUpdatesAndClearsOnCompletion() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                    Map.of("default_ttl", 1, "refresh_on_read", true));

        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        checkpointer.preWorkflowExecute(session, null);
        WorkflowCommitState state = (WorkflowCommitState) session.state();
        state.updateGlobal(Map.of("persisted", "value"));
        state.updateWorkflow(Map.of("step", 1));
        state.update(Map.of("ask", "pending"));
        state.commit();
        state.update(Map.of("afterCommit", "still-pending"));
        checkpointer.graphStore().save("session-1", "workflow-1:sub:1",
                GraphStoreState.create("workflow-1:sub:1", 1, Map.of("k", "v"), List.of(), Map.of(), Map.of()));

        checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);
        assertTrue(checkpointer.sessionExists("session-1"));

        WorkflowSession restored = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        InteractiveInput inputs = new InteractiveInput();
        inputs.update("ask", Map.of("answer", "done"));
        checkpointer.preWorkflowExecute(restored, inputs);

        WorkflowCommitState restoredState = (WorkflowCommitState) restored.state();
        assertEquals("value", restored.state().getGlobal("persisted"));
        assertEquals(1, restoredState.getWorkflow("step"));
        restoredState.commit();
        assertEquals("still-pending", restoredState.get("afterCommit"));

        NodeSession nodeSession = new NodeSession(restored, "ask");
        assertEquals(List.of(Map.of("answer", "done")), nodeSession.state().get(Constant.INTERACTIVE_INPUT));

        checkpointer.postWorkflowExecute(restored, Map.of("ok", true), null);
        assertFalse(checkpointer.sessionExists("session-1"));
        assertTrue(checkpointer.graphStore().get("session-1", "workflow-1:sub:1").isEmpty());
    }

    @Test
    void forceDeleteWorkflowStateClearsGraphAndWorkflowCheckpoint() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient), null);

        WorkflowSession initial = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        checkpointer.preWorkflowExecute(initial, null);
        WorkflowCommitState state = (WorkflowCommitState) initial.state();
        state.updateGlobal(Map.of("persisted", "value"));
        state.commit();
        checkpointer.postWorkflowExecute(initial, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);
        checkpointer.graphStore().save("session-1", "workflow-1",
                GraphStoreState.create("workflow-1", 1, Map.of(), List.of(), Map.of(), Map.of()));

        WorkflowSession fresh = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        fresh.config().setEnvs(Map.of(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, true));

        checkpointer.preWorkflowExecute(fresh, null);

        assertFalse(checkpointer.sessionExists("session-1"));
        assertTrue(checkpointer.graphStore().get("session-1", "workflow-1").isEmpty());
    }

    @Test
    void preWorkflowExecuteWithoutInteractiveInputRejectsExistingStateWhenCleanupDisabled() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient), null);

        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        checkpointer.preWorkflowExecute(session, null);
        WorkflowCommitState state = (WorkflowCommitState) session.state();
        state.updateGlobal(Map.of("persisted", "value"));
        state.commit();
        checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

        WorkflowSession resumed = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        RuntimeException error =
            assertThrows(RuntimeException.class, () -> checkpointer.preWorkflowExecute(resumed, null));
        assertTrue(error.getMessage().contains("workflow state exists"));
    }

    @Test
    void graphStoreDeletesNamespacePrefixesAndRefreshesTtl() throws Exception {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer =
            new RedisCheckpointer(new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                    Map.of("default_ttl", 1, "refresh_on_read", true));

        GraphStoreState parent = GraphStoreState.create("workflow-1", 1, Map.of("a", 1), List.of(), Map.of(), Map.of());
        GraphStoreState child =
            GraphStoreState.create("workflow-1:sub:1", 2, Map.of("b", 2), List.of(), Map.of(), Map.of());
        GraphStoreState other = GraphStoreState.create("workflow-2", 3, Map.of("c", 3), List.of(), Map.of(), Map.of());

        checkpointer.graphStore().save("session-1", "workflow-1", parent);
        checkpointer.graphStore().save("session-1", "workflow-1:sub:1", child);
        checkpointer.graphStore().save("session-1", "workflow-2", other);

        String ttlKey = "session-1:workflow-graph:workflow-1:checkpoint_data_type";
        Thread.sleep(20L);

        GraphStoreState loaded = checkpointer.graphStore().get("session-1", "workflow-1").orElse(null);
        assertNotNull(loaded);
        assertEquals(1, loaded.getStep());
        assertTrue(redisClient.ttl(ttlKey) >= 59L, "graph read should refresh TTL");

        checkpointer.graphStore().delete("session-1", "workflow-1");

        assertTrue(checkpointer.graphStore().get("session-1", "workflow-1").isEmpty());
        assertTrue(checkpointer.graphStore().get("session-1", "workflow-1:sub:1").isEmpty());
        assertEquals(3, checkpointer.graphStore().get("session-1", "workflow-2").orElseThrow().getStep());
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        public void set(byte[] key, byte[] value) {
            set(new String(key, StandardCharsets.UTF_8), value);
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

        public byte[] get(byte[] key) {
            Object value = get(new String(key, StandardCharsets.UTF_8));
            return value instanceof byte[] bytes ? bytes : null;
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

        public long ttl(String key) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return -2L;
            }
            Long expiresAt = expiryAt.get(key);
            if (expiresAt == null) {
                return -1L;
            }
            long remaining = (expiresAt - System.currentTimeMillis()) / 1000L;
            return Math.max(remaining, 0L);
        }

        public FakeRedisPipeline pipeline() {
            return new FakeRedisPipeline(this);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    static class FakeRedisPipeline {
        private final FakeRedisClient client;
        private final List<Runnable> operations = new ArrayList<>();

        FakeRedisPipeline(FakeRedisClient client) {
            this.client = client;
        }

        public FakeRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }
}
