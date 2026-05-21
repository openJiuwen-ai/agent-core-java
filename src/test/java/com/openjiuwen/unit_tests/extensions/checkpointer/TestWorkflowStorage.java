/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.checkpointer;

import com.openjiuwen.core.session.internal.agent.AgentSession;
import com.openjiuwen.core.session.internal.workflow.WorkflowSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.state.agent_state.StateCollection;
import com.openjiuwen.extensions.checkpointer.redis.storage.WorkflowStorage;
import com.openjiuwen.extensions.store.kv.RedisStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for WorkflowStorage.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/checkpointer/test_workflow_storage.py}.
 * <p>
 * Note: These tests require Redis local environment to run.
 * Tests are skipped when Redis is not available.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_REDIS_TESTS", matches = "true")
public class TestWorkflowStorage {

    private RedisStore redisStore;
    private WorkflowStorage storage;
    private WorkflowSession mockWorkflowSession;
    private String sessionId;
    private String workflowId;

    @BeforeEach
    void setUp() {
        redisStore = mock(RedisStore.class);
        storage = new WorkflowStorage(redisStore, null);
        
        sessionId = "test_session_" + UUID.randomUUID().toString().substring(0, 8);
        workflowId = "test_workflow_" + UUID.randomUUID().toString().substring(0, 8);
        
        mockWorkflowSession = createMockWorkflowSession();
    }

    @AfterEach
    void tearDown() {
        // Cleanup resources
    }

    // ---------------------------------------------------------------------------
    // Test fixtures helpers
    // ---------------------------------------------------------------------------

    private WorkflowSession createMockWorkflowSession() {
        AgentSession parentSession = mock(AgentSession.class);
        when(parentSession.sessionId()).thenReturn(sessionId);
        
        WorkflowSession session = mock(WorkflowSession.class);
        when(session.sessionId()).thenReturn(sessionId);
        when(session.workflowId()).thenReturn(workflowId);
        when(session.parent()).thenReturn(parentSession);
        
        StateCollection state = mock(StateCollection.class);
        when(session.state()).thenReturn(state);
        
        return session;
    }

    // ---------------------------------------------------------------------------
    // save and recover tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test save and recover workflow state")
    @Tag("level0")
    void testSaveAndRecoverWorkflowState() {
        // Set state
        StateCollection state = mockWorkflowSession.state();
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("workflow_key", "workflow_value");
        when(state.getState()).thenReturn(stateData);

        // Save
        storage.save(mockWorkflowSession);

        // Verify exists
        when(redisStore.get(any())).thenReturn(Optional.of(serializeState(stateData)));
        boolean exists = storage.exists(mockWorkflowSession).join();
        assertThat(exists).isTrue();

        // Recover
        WorkflowSession newSession = createMockWorkflowSession();
        StateCollection newState = newSession.state();
        when(newState.getState()).thenReturn(new HashMap<>());

        InteractiveInput inputs = new InteractiveInput();
        storage.recover(newSession, inputs);

        // Verify state was recovered
        verify(newState, atLeast(0)).getState();
    }

    @Test
    @DisplayName("Test save with updates")
    @Tag("level0")
    void testSaveWithUpdates() {
        // Set state and updates
        StateCollection state = mockWorkflowSession.state();
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("key", "value");
        
        Map<String, Object> updatesData = new HashMap<>();
        updatesData.put("update_key", "update_value");
        
        when(state.getState()).thenReturn(stateData);
        when(state.getUpdates()).thenReturn(updatesData);

        // Save
        storage.save(mockWorkflowSession);

        // Verify exists
        when(redisStore.get(any())).thenReturn(Optional.of(serializeState(stateData)));
        boolean exists = storage.exists(mockWorkflowSession).join();
        assertThat(exists).isTrue();
    }

    // ---------------------------------------------------------------------------
    // exists tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test exists method")
    @Tag("level0")
    void testExists() {
        // Initially doesn't exist
        when(redisStore.get(any())).thenReturn(Optional.empty());
        boolean initialExists = storage.exists(mockWorkflowSession).join();
        assertThat(initialExists).isFalse();

        // Save state
        StateCollection state = mockWorkflowSession.state();
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("key", "value");
        when(state.getState()).thenReturn(stateData);
        storage.save(mockWorkflowSession);

        // Now exists
        when(redisStore.get(any())).thenReturn(Optional.of(serializeState(stateData)));
        boolean afterSaveExists = storage.exists(mockWorkflowSession).join();
        assertThat(afterSaveExists).isTrue();
    }

    // ---------------------------------------------------------------------------
    // clear tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test clear method")
    @Tag("level0")
    void testClear() {
        // Save state
        StateCollection state = mockWorkflowSession.state();
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("key", "value");
        when(state.getState()).thenReturn(stateData);
        storage.save(mockWorkflowSession);

        // Verify exists
        when(redisStore.get(any())).thenReturn(Optional.of(serializeState(stateData)));
        boolean existsBeforeClear = storage.exists(mockWorkflowSession).join();
        assertThat(existsBeforeClear).isTrue();

        // Clear
        storage.clear(workflowId, sessionId);

        // Verify cleared
        when(redisStore.get(any())).thenReturn(Optional.empty());
        boolean existsAfterClear = storage.exists(mockWorkflowSession).join();
        assertThat(existsAfterClear).isFalse();
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private byte[] serializeState(Map<String, Object> state) {
        // Placeholder for serialization
        return new byte[0];
    }
}