/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.checkpointer.redis.storage.GraphStore;
import com.openjiuwen.extensions.store.kv.RedisStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RedisCheckpointer core functionality.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/checkpointer/test_redis_checkpointer.py}.
 * <p>
 * Note: These tests require Redis local environment to run.
 * Tests are skipped when Redis is not available.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_REDIS_TESTS", matches = "true")
public class TestRedisCheckpointer {

    private RedisStore redisStore;
    private InMemoryRedisClient redisClient;
    private RedisCheckpointer checkpointer;
    private AgentSession mockAgentSession;
    private String sessionId;
    private String agentId;

    @BeforeEach
    void setUp() {
        redisClient = new InMemoryRedisClient();
        redisStore = new RedisStore(redisClient);
        checkpointer = new RedisCheckpointer(redisStore, null);
        
        sessionId = "test_session_" + UUID.randomUUID().toString().substring(0, 8);
        agentId = "test_agent_" + UUID.randomUUID().toString().substring(0, 8);
        
        mockAgentSession = createAgentSession(sessionId, agentId);
    }

    @AfterEach
    void tearDown() {
        // Cleanup resources
    }

    // ---------------------------------------------------------------------------
    // Test fixtures helpers
    // ---------------------------------------------------------------------------

    private AgentSession createAgentSession(String newSessionId, String newAgentId) {
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike(newAgentId, newAgentId, "agent"));
        return new AgentSession(newSessionId, config, null);
    }

    private AgentSession createMockAgentSession() {
        String newSessionId = "test_session_" + UUID.randomUUID().toString().substring(0, 8);
        String newAgentId = "test_agent_" + UUID.randomUUID().toString().substring(0, 8);
        return createAgentSession(newSessionId, newAgentId);
    }

    private WorkflowSession createMockWorkflowSession(AgentSession parent) {
        String workflowId = "test_workflow_" + UUID.randomUUID().toString().substring(0, 8);
        return createWorkflowSession(parent, workflowId, parent.sessionId());
    }

    private WorkflowSession createWorkflowSession(AgentSession parent, String workflowId, String workflowSessionId) {
        return new WorkflowSession(workflowId, parent, workflowSessionId, null, null);
    }

    // ---------------------------------------------------------------------------
    // pre_agent_execute tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test pre_agent_execute saves and recovers agent state")
    @Tag("level0")
    void testPreAgentExecuteSavesAndRecoversAgentState() {
        mockAgentSession.state().update(Map.of("key1", "value1", "key2", 42));

        // Save state
        checkpointer.preAgentExecute(mockAgentSession, null);
        checkpointer.interruptAgentExecute(mockAgentSession);

        // Create new session and recover
        AgentSession newSession = createAgentSession(sessionId, agentId);

        // Recover
        checkpointer.preAgentExecute(newSession, null);

        // Verify state was recovered
        assertThat(newSession.state().get("key1")).isEqualTo("value1");
        assertThat(newSession.state().get("key2")).isEqualTo(42);
    }

    @Test
    @DisplayName("Test pre_agent_execute with inputs")
    @Tag("level0")
    void testPreAgentExecuteWithInputs() {
        // Execute with inputs
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("test_input", "test_value");
        
        checkpointer.preAgentExecute(mockAgentSession, inputs);

        // Verify inputs were set - INTERACTIVE_INPUT is set in agent_state
        Map<String, Object> stateData = mockAgentSession.state().getState();
        @SuppressWarnings("unchecked")
        Map<String, Object> agentState = (Map<String, Object>) stateData.get("agent_state");
        assertThat(agentState).containsKey(Constant.INTERACTIVE_INPUT);
        assertThat(agentState.get(Constant.INTERACTIVE_INPUT)).isEqualTo(List.of(inputs));
    }

    // ---------------------------------------------------------------------------
    // interrupt_agent_execute tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test interrupt_agent_execute saves state")
    @Tag("level0")
    void testInterruptAgentExecuteSavesState() {
        mockAgentSession.state().update(Map.of("key", "value"));

        // Execute interrupt
        checkpointer.interruptAgentExecute(mockAgentSession);

        // Verify state was saved by checking exists
        assertThat(checkpointer.getAgentStorage().exists(mockAgentSession).join()).isTrue();
    }

    // ---------------------------------------------------------------------------
    // post_agent_execute tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test post_agent_execute saves state")
    @Tag("level0")
    void testPostAgentExecuteSavesState() {
        mockAgentSession.state().update(Map.of("key", "value"));

        // Execute post
        checkpointer.postAgentExecute(mockAgentSession);

        // Verify state was saved
        assertThat(checkpointer.getAgentStorage().exists(mockAgentSession).join()).isTrue();
    }

    // ---------------------------------------------------------------------------
    // pre_workflow_execute tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test pre_workflow_execute recovers workflow state")
    @Tag("level0")
    void testPreWorkflowExecuteRecoversWorkflowState() {
        // Create workflow session
        WorkflowSession workflowSession = createMockWorkflowSession(mockAgentSession);
        String workflowId = workflowSession.workflowId();
        
        // Set some state
        ((WorkflowCommitState) workflowSession.state())
                .updateAndCommitWorkflowState(Map.of("workflow_key", "workflow_value"));

        // Save state via workflow storage
        checkpointer.getWorkflowStorage().save(workflowSession).join();
        
        // Create new session and recover
        WorkflowSession newSession = createWorkflowSession(mockAgentSession, workflowId, sessionId);

        // Recover with inputs
        InteractiveInput inputs = new InteractiveInput();
        checkpointer.preWorkflowExecute(newSession, inputs);

        // Verify state was recovered
        assertThat(((WorkflowCommitState) newSession.state()).getWorkflow("workflow_key"))
                .isEqualTo("workflow_value");
    }

    // ---------------------------------------------------------------------------
    // post_workflow_execute tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test post_workflow_execute clears state on success")
    @Tag("level0")
    void testPostWorkflowExecuteClearsStateOnSuccess() {
        // Create workflow session
        WorkflowSession workflowSession = createMockWorkflowSession(mockAgentSession);
        
        ((WorkflowCommitState) workflowSession.state()).updateAndCommitWorkflowState(Map.of("key", "value"));
        checkpointer.getWorkflowStorage().save(workflowSession).join();
        assertThat(checkpointer.getWorkflowStorage().exists(workflowSession).join()).isTrue();

        // Execute with success result
        Map<String, Object> result = new HashMap<>();
        checkpointer.postWorkflowExecute(workflowSession, result, null);

        // Verify state was cleared
        assertThat(checkpointer.getWorkflowStorage().exists(workflowSession).join()).isFalse();
    }

    @Test
    @DisplayName("Test post_workflow_execute saves state and raises exception")
    @Tag("level0")
    void testPostWorkflowExecuteSavesStateAndRaisesException() {
        // Create workflow session
        WorkflowSession workflowSession = createMockWorkflowSession(mockAgentSession);
        
        ((WorkflowCommitState) workflowSession.state()).updateAndCommitWorkflowState(Map.of("key", "value"));

        // Execute with exception
        Exception exception = new RuntimeException("Test error");

        assertThatThrownBy(() -> 
            checkpointer.postWorkflowExecute(workflowSession, new HashMap<>(), exception)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Test error");

        // Verify state was saved
        assertThat(checkpointer.getWorkflowStorage().exists(workflowSession).join()).isTrue();
    }

    @Test
    @DisplayName("Test post_workflow_execute saves state on interrupt")
    @Tag("level0")
    void testPostWorkflowExecuteSavesStateOnInterrupt() {
        // Create workflow session
        WorkflowSession workflowSession = createMockWorkflowSession(mockAgentSession);
        
        ((WorkflowCommitState) workflowSession.state()).updateAndCommitWorkflowState(Map.of("key", "value"));

        // Execute with interrupt result
        Map<String, Object> result = new HashMap<>();
        result.put(PregelConstants.TASK_STATUS_INTERRUPT, true);
        
        checkpointer.postWorkflowExecute(workflowSession, result, null);

        // Verify state was saved
        assertThat(checkpointer.getWorkflowStorage().exists(workflowSession).join()).isTrue();
    }

    // ---------------------------------------------------------------------------
    // release tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test release with specific agent_id")
    @Tag("level0")
    void testReleaseWithAgentId() {
        mockAgentSession.state().update(Map.of("key", "value"));

        // Save state
        checkpointer.interruptAgentExecute(mockAgentSession);
        assertThat(checkpointer.getAgentStorage().exists(mockAgentSession).join()).isTrue();

        // Release agent
        checkpointer.release(sessionId, agentId);

        // Verify state was cleared
        assertThat(checkpointer.getAgentStorage().exists(mockAgentSession).join()).isFalse();
    }

    @Test
    @DisplayName("Test release without agent_id deletes all session keys")
    @Tag("level0")
    void testReleaseWithoutAgentIdDeletesAllSessionKeys() {
        String testSessionId = "test_session_" + UUID.randomUUID().toString().substring(0, 8);

        redisStore.set(testSessionId + ":key1", "value1");
        redisStore.set(testSessionId + ":key2", "value2");
        redisStore.set("other_session:key3", "value3");

        // Release session
        checkpointer.release(testSessionId);

        // Verify session keys were deleted and other session keys remain
        assertThat(redisStore.exists(testSessionId + ":key1")).isFalse();
        assertThat(redisStore.exists(testSessionId + ":key2")).isFalse();
        assertThat(redisStore.exists("other_session:key3")).isTrue();
    }

    @Test
    @DisplayName("Test release when redis_store is null")
    @Tag("level0")
    void testReleaseWithNullRedisStore() {
        // Create a RedisCheckpointer with null RedisStore
        RedisCheckpointer nullCheckpointer = new RedisCheckpointer(null, null);
        
        // Should not raise error
        nullCheckpointer.release("session_id", "agent_id");
        
        // No exception thrown - test passes
        assertThat(true).isTrue();
    }

    // ---------------------------------------------------------------------------
    // graph_store tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test graph_store returns GraphStore instance")
    @Tag("level0")
    void testGraphStoreReturnsGraphStoreInstance() {
        GraphStore graphStore = checkpointer.getGraphStore();
        
        assertThat(graphStore).isNotNull();
        assertThat(graphStore).isInstanceOf(GraphStore.class);
    }

    // ---------------------------------------------------------------------------
    // Redis flush tests (skipped by default)
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test deleting all key-value data in current Redis database")
    @Tag("level0")
    @Disabled("Skip by default - this test flushes all Redis data")
    void testFlushAllRedisKeys() {
        // This test would require real Redis connection
        // Create some test keys with different prefixes
        List<String> testKeys = Arrays.asList(
            "test_session_1:agent_state",
            "test_session_2:workflow_state",
            "test_session_3:graph_state",
            "other_key_1",
            "other_key_2"
        );

        // In real test:
        // 1. Set test data for each key
        // 2. Verify all keys exist
        // 3. Delete all keys in current database
        // 4. Verify all keys are deleted
        
        assertThat(true).isTrue(); // Placeholder
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private static final class InMemoryRedisClient {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public synchronized void set(String key, Object value) {
            values.put(key, value);
        }

        public synchronized boolean set(String key, Object value, Boolean nx, Integer expiry) {
            if (Boolean.TRUE.equals(nx) && values.containsKey(key)) {
                return false;
            }
            values.put(key, value);
            return true;
        }

        public synchronized Object get(String key) {
            return values.get(key);
        }

        public synchronized long exists(String key) {
            return values.containsKey(key) ? 1L : 0L;
        }

        public synchronized long delete(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                if (values.remove(key) != null) {
                    deleted++;
                }
            }
            return deleted;
        }

        public synchronized List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> keys = new ArrayList<>();
            for (String key : values.keySet()) {
                if (key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            return keys;
        }

        public synchronized boolean expire(String key, int ttlSeconds) {
            return values.containsKey(key);
        }
    }
}
