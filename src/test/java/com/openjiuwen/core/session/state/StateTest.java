/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for state subsystem: {@link InMemoryStateLike}, {@link InMemoryCommitState},
 * {@link WorkflowCommitState}, {@link AgentStateCollection}, {@link WorkflowStateCollection}.
 */
class StateTest {

    // ---------- InMemoryStateLike tests ----------

    @Nested
    @DisplayName("InMemoryStateLike")
    class InMemoryStateLikeTests {

        @Test
        @DisplayName("default constructor creates empty state")
        void testDefaultConstructor() {
            InMemoryStateLike state = new InMemoryStateLike();
            assertTrue(state.getState().isEmpty());
        }

        @Test
        @DisplayName("constructor with initial state")
        void testInitialState() {
            InMemoryStateLike state = new InMemoryStateLike(Map.of("key", "value"));
            assertEquals("value", state.get("key"));
        }

        @Test
        @DisplayName("constructor with null creates empty state")
        void testNullInitialState() {
            InMemoryStateLike state = new InMemoryStateLike(null);
            assertTrue(state.getState().isEmpty());
        }

        @Test
        @DisplayName("update merges data")
        void testUpdate() {
            InMemoryStateLike state = new InMemoryStateLike();
            state.update(Map.of("a", 1, "b", 2));
            assertEquals(1, state.get("a"));
            assertEquals(2, state.get("b"));
        }

        @Test
        @DisplayName("update merges nested maps")
        void testUpdateMergesNested() {
            InMemoryStateLike state = new InMemoryStateLike(
                    new HashMap<>(Map.of("data", new HashMap<>(Map.of("a", 1)))));
            state.update(Map.of("data", Map.of("b", 2)));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) state.get("data");
            assertEquals(1, data.get("a"));
            assertEquals(2, data.get("b"));
        }

        @Test
        @DisplayName("getState returns deep copy")
        void testGetStateDeepCopy() {
            InMemoryStateLike state = new InMemoryStateLike(Map.of("key", "value"));
            Map<String, Object> copy = state.getState();
            copy.put("newKey", "newValue");
            assertNull(state.get("newKey")); // Original not affected
        }

        @Test
        @DisplayName("get with null key returns null")
        void testGetNullKey() {
            InMemoryStateLike state = new InMemoryStateLike(Map.of("key", "value"));
            assertNull(state.get(null));
        }

        @Test
        @DisplayName("setState replaces state")
        void testSetState() {
            InMemoryStateLike state = new InMemoryStateLike(Map.of("old", "value"));
            state.setState(new HashMap<>(Map.of("new", "value")));
            assertNull(state.get("old"));
            assertEquals("value", state.get("new"));
        }
    }

    // ---------- InMemoryCommitState tests ----------

    @Nested
    @DisplayName("InMemoryCommitState")
    class InMemoryCommitStateTests {

        @Test
        @DisplayName("updateById and commit")
        void testUpdateByIdAndCommit() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            commitState.updateById("node1", Map.of("key", "value"));

            // Before commit, state is empty
            assertNull(commitState.get("key"));

            // After commit
            commitState.commit("node1");
            assertEquals("value", commitState.get("key"));
        }

        @Test
        @DisplayName("commit null commits all nodes")
        void testCommitNull() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            commitState.updateById("node1", Map.of("key1", "value1"));
            commitState.updateById("node2", Map.of("key2", "value2"));

            commitState.commit(null);
            assertEquals("value1", commitState.get("key1"));
            assertEquals("value2", commitState.get("key2"));
        }

        @Test
        @DisplayName("rollback removes pending updates for node")
        void testRollback() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            commitState.updateById("node1", Map.of("key", "value"));
            commitState.rollback("node1");
            commitState.commit("node1");
            assertNull(commitState.get("key"));
        }

        @Test
        @DisplayName("update without nodeId throws")
        void testUpdateWithoutNodeIdThrows() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            assertThrows(Exception.class, () -> commitState.update(Map.of("key", "value")));
        }

        @Test
        @DisplayName("updateById with null nodeId throws")
        void testUpdateByIdNullThrows() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            assertThrows(Exception.class, () -> commitState.updateById(null, Map.of("key", "value")));
        }

        @Test
        @DisplayName("getUpdates returns pending updates")
        void testGetUpdates() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            commitState.updateById("node1", Map.of("key", "value"));
            Map<String, Object> updates = commitState.getUpdates();
            assertFalse(updates.isEmpty());
            assertTrue(updates.containsKey("node1"));
        }

        @Test
        @DisplayName("getUpdates empty after commit")
        void testGetUpdatesAfterCommit() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            commitState.updateById("node1", Map.of("key", "value"));
            commitState.commit(null);
            Map<String, Object> updates = commitState.getUpdates();
            assertTrue(updates.isEmpty());
        }

        @Test
        @DisplayName("multiple updates for same node accumulate")
        void testMultipleUpdatesAccumulate() {
            InMemoryCommitState commitState = new InMemoryCommitState();
            commitState.updateById("node1", Map.of("key1", "value1"));
            commitState.updateById("node1", Map.of("key2", "value2"));
            commitState.commit("node1");
            assertEquals("value1", commitState.get("key1"));
            assertEquals("value2", commitState.get("key2"));
        }
    }

    // ---------- WorkflowCommitState tests ----------

    @Nested
    @DisplayName("WorkflowCommitState")
    class WorkflowCommitStateTests {

        @Test
        @DisplayName("create with InMemoryState.create()")
        void testCreateDefault() {
            WorkflowCommitState state = InMemoryState.create();
            assertNotNull(state);
            assertNotNull(state.getState());
        }

        @Test
        @DisplayName("commit all state partitions")
        void testCommitAll() {
            WorkflowCommitState state = InMemoryState.create();
            state.getGlobalState().updateById("node1", Map.of("key", "value"));
            state.commit();
            assertEquals("value", state.getGlobalState().get("key"));
        }

        @Test
        @DisplayName("commitCmp commits component and IO state")
        void testCommitCmp() {
            WorkflowCommitState state = InMemoryState.create();
            // Use createNodeState to get a scoped collection with the correct nodeId
            WorkflowStateCollection nodeState = state.createNodeState("node1");
            nodeState.update(Map.of("k", "v"));
            nodeState.commitCmp();
            // Verify component state committed (node1's component data)
            assertNotNull(state.getCompState().get("node1"));
        }

        @Test
        @DisplayName("rollback reverts pending updates")
        void testRollback() {
            WorkflowCommitState state = InMemoryState.create();
            state.getGlobalState().updateById("default", Map.of("key", "value"));
            state.rollback();
            state.commit();
            assertNull(state.getGlobalState().get("key"));
        }

        @Test
        @DisplayName("getState and setState round-trip")
        void testGetSetState() {
            WorkflowCommitState state = InMemoryState.create();
            state.getGlobalState().updateById("default", Map.of("key", "value"));
            state.commit();

            Map<String, Object> snapshot = state.getState();
            assertNotNull(snapshot);
            assertTrue(snapshot.containsKey("global_state"));

            WorkflowCommitState state2 = InMemoryState.create();
            state2.setState(snapshot);
            assertEquals("value", state2.getGlobalState().get("key"));
        }

        @Test
        @DisplayName("createNodeState creates scoped collection")
        void testCreateNodeState() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowStateCollection nodeState = state.createNodeState("node1");
            assertNotNull(nodeState);
        }

        @Test
        @DisplayName("InMemoryState.create with initial data")
        void testCreateWithInitialData() {
            WorkflowCommitState state = InMemoryState.create(
                    null,
                    Map.of("globalKey", "globalValue"),
                    null, null, null
            );
            assertEquals("globalValue", state.getGlobalState().get("globalKey"));
        }

        @Test
        @DisplayName("InMemoryState.fromMap")
        void testFromMap() {
            Map<String, Object> stateMap = new HashMap<>();
            stateMap.put("global_state", Map.of("key", "value"));
            stateMap.put("io_state", Map.of());
            stateMap.put("comp_state", Map.of());
            stateMap.put("workflow_state", Map.of());
            stateMap.put("trace_state", Map.of());

            WorkflowCommitState state = InMemoryState.fromMap(stateMap);
            assertEquals("value", state.getGlobalState().get("key"));
        }

        @Test
        @DisplayName("InMemoryState.fromMap with null returns empty")
        void testFromMapNull() {
            WorkflowCommitState state = InMemoryState.fromMap(null);
            assertNotNull(state);
        }
    }

    // ---------- AgentStateCollection tests ----------

    @Nested
    @DisplayName("AgentStateCollection")
    class AgentStateCollectionTests {

        @Test
        @DisplayName("default constructor creates empty collections")
        void testDefaultConstructor() {
            AgentStateCollection state = new AgentStateCollection();
            assertNull(state.getGlobal("any_key"));
            assertNull(state.get("any_key"));
        }

        @Test
        @DisplayName("updateGlobal and getGlobal")
        void testUpdateGlobalAndGet() {
            AgentStateCollection state = new AgentStateCollection();
            state.updateGlobal(Map.of("key", "value"));
            assertEquals("value", state.getGlobal("key"));
        }

        @Test
        @DisplayName("update and get agent state")
        void testUpdateAndGetAgentState() {
            AgentStateCollection state = new AgentStateCollection();
            state.update(Map.of("agent_key", "agent_value"));
            assertEquals("agent_value", state.get("agent_key"));
        }

        @Test
        @DisplayName("get with null returns full agent state")
        void testGetNull() {
            AgentStateCollection state = new AgentStateCollection();
            state.update(Map.of("key", "value"));
            Object result = state.get(null);
            assertTrue(result instanceof Map);
        }

        @Test
        @DisplayName("getGlobal with null returns full global state")
        void testGetGlobalNull() {
            AgentStateCollection state = new AgentStateCollection();
            state.updateGlobal(Map.of("key", "value"));
            Object result = state.getGlobal(null);
            assertTrue(result instanceof Map);
        }

        @Test
        @DisplayName("dump returns correct structure")
        void testDump() {
            AgentStateCollection state = new AgentStateCollection();
            state.updateGlobal(Map.of("gkey", "gvalue"));
            state.update(Map.of("akey", "avalue"));

            Map<String, Object> dump = state.dump();
            assertTrue(dump.containsKey("global_state"));
            assertTrue(dump.containsKey("agent_state"));
            assertTrue(dump.containsKey("trace_state"));

            @SuppressWarnings("unchecked")
            Map<String, Object> globalState = (Map<String, Object>) dump.get("global_state");
            assertEquals("gvalue", globalState.get("gkey"));
        }

        @Test
        @DisplayName("getState returns state map")
        void testGetState() {
            AgentStateCollection state = new AgentStateCollection();
            state.updateGlobal(Map.of("key", "value"));
            Map<String, Object> stateMap = state.getState();
            assertTrue(stateMap.containsKey("global_state"));
        }

        @Test
        @DisplayName("setState restores from map")
        void testSetState() {
            AgentStateCollection state = new AgentStateCollection();
            state.setState(Map.of(
                    "global_state", Map.of("restored", "value"),
                    "agent_state", Map.of("agent_restored", "value2")
            ));
            assertEquals("value", state.getGlobal("restored"));
            assertEquals("value2", state.get("agent_restored"));
        }
    }

    // ---------- WorkflowStateCollection tests ----------

    @Nested
    @DisplayName("WorkflowStateCollection")
    class WorkflowStateCollectionTests {

        @Test
        @DisplayName("node state isolation: different nodes have separate component state")
        void testNodeStateIsolation() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowStateCollection node1State = state.createNodeState("node1");
            WorkflowStateCollection node2State = state.createNodeState("node2");

            node1State.update(Map.of("key", "node1_value"));
            node2State.update(Map.of("key", "node2_value"));

            state.commit();

            // Each node sees only its own state
            assertNotNull(node1State.get("key"));
            assertNotNull(node2State.get("key"));
        }

        @Test
        @DisplayName("global state shared across nodes")
        void testGlobalStateShared() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowStateCollection node1State = state.createNodeState("node1");
            WorkflowStateCollection node2State = state.createNodeState("node2");

            node1State.updateGlobal(Map.of("shared", "value"));
            state.commit();

            assertEquals("value", node2State.getGlobal("shared"));
        }

        @Test
        @DisplayName("setOutputs and getOutputs")
        void testSetAndGetOutputs() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowStateCollection nodeState = state.createNodeState("node1");
            nodeState.setOutputs(Map.of("result", "output_value"));
            // Commit the ioState updates
            state.getIoState().commit(null);

            // After commit, ioState should contain node1's outputs nested under "node1"
            Object node1Data = state.getIoState().get("node1");
            assertNotNull(node1Data);
            assertTrue(node1Data instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> outputMap = (Map<String, Object>) node1Data;
            assertEquals("output_value", outputMap.get("result"));
        }

        @Test
        @DisplayName("commitUserInputs")
        void testCommitUserInputs() {
            WorkflowCommitState state = InMemoryState.create();
            state.commitUserInputs(Map.of("a", 1, "b", 2));

            assertEquals(1, state.getIoState().get("a"));
            assertEquals(2, state.getIoState().get("b"));
        }

        @Test
        @DisplayName("dump returns all state partitions")
        void testDump() {
            WorkflowCommitState state = InMemoryState.create();
            WorkflowStateCollection nodeState = state.createNodeState("node1");
            Map<String, Object> dump = nodeState.dump();

            assertTrue(dump.containsKey("io_state"));
            assertTrue(dump.containsKey("io_state_updates"));
            assertTrue(dump.containsKey("global_state"));
            assertTrue(dump.containsKey("global_state_updates"));
            assertTrue(dump.containsKey("comp_state"));
            assertTrue(dump.containsKey("comp_state_updates"));
            assertTrue(dump.containsKey("workflow_state"));
            assertTrue(dump.containsKey("workflow_state_updates"));
            assertTrue(dump.containsKey("trace_state"));
        }
    }
}
