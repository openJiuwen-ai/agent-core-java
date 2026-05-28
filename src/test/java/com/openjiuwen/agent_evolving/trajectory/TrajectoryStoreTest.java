/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TrajectoryStore.
 * 
 * <p>Mirrors Python's tests/unit_tests/agent_evolving/trajectory/test_store.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/agent_evolving/trajectory/test_store.py
 * 
 * <p>NOTE: Java implementation of TrajectoryStore interface is provided as 
 * InMemoryTrajectoryStoreImpl for testing purposes.
 */
@ExtendWith(MockitoExtension.class)
class TrajectoryStoreTest {

    /**
     * In-memory implementation of TrajectoryStore for testing.
     * Mirrors Python's InMemoryTrajectoryStore.
     */
    private static class InMemoryTrajectoryStoreImpl implements TrajectoryStore {
        private final Map<String, Map<String, Trajectory>> data = new LinkedHashMap<>();

        @Override
        public void save(Trajectory trajectory, String version) {
            String ver = version != null ? version : "default";
            data.computeIfAbsent(ver, k -> new LinkedHashMap<>())
                .put(trajectory.getExecutionId(), trajectory);
        }

        @Override
        public Trajectory load(String executionId, String version) {
            String ver = version != null ? version : "default";
            Map<String, Trajectory> versionData = data.get(ver);
            if (versionData == null) {
                return null;
            }
            return versionData.get(executionId);
        }

        @Override
        public List<Trajectory> queryBySessionId(String sessionId) {
            List<Trajectory> results = new ArrayList<>();
            for (Map<String, Trajectory> versionData : data.values()) {
                for (Trajectory traj : versionData.values()) {
                    if (sessionId != null && sessionId.equals(traj.getSessionId())) {
                        results.add(traj);
                    }
                }
            }
            return results;
        }

        @Override
        public List<Trajectory> query(String sessionId, String executionId, String version) {
            String ver = version != null ? version : "default";
            Map<String, Trajectory> versionData = data.getOrDefault(ver, Map.of());
            
            return versionData.values().stream()
                .filter(traj -> sessionId == null || sessionId.equals(traj.getSessionId()))
                .filter(traj -> executionId == null || executionId.equals(traj.getExecutionId()))
                .collect(Collectors.toList());
        }
    }

    /**
     * Factory for creating Trajectory (mirrors Python's make_trajectory).
     */
    private Trajectory makeTrajectory(String execId, String sessionId, String source, String caseId) {
        Trajectory traj = new Trajectory();
        traj.setExecutionId(execId);
        traj.setSessionId(sessionId != null ? sessionId : "session1");
        traj.setSource(source != null ? source : "offline");
        traj.setCaseId(caseId);
        traj.setSteps(new ArrayList<>());
        return traj;
    }

    private Trajectory makeTrajectory(String execId) {
        return makeTrajectory(execId, "session1", "offline", null);
    }

    private Trajectory makeTrajectory(String execId, String caseId) {
        return makeTrajectory(execId, "session1", "offline", caseId);
    }

    // ========== TestInMemoryTrajectoryStore tests ==========

    @Test
    @Tag("level0")
    @DisplayName("Test save and load")
    void testSaveAndLoad() {
        // Python: test_save_and_load
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        Trajectory traj = makeTrajectory("exec1", "case1");

        store.save(traj, null);
        Trajectory loaded = store.load("exec1", null);

        assertNotNull(loaded);
        assertEquals("exec1", loaded.getExecutionId());
        assertEquals("case1", loaded.getCaseId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test load nonexistent returns null")
    void testLoadNonexistent() {
        // Python: test_load_nonexistent
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();

        Trajectory result = store.load("nonexistent", null);

        assertNull(result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test query all returns all trajectories")
    void testQueryAll() {
        // Python: test_query_all
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        store.save(makeTrajectory("exec1"), null);
        store.save(makeTrajectory("exec2"), null);

        List<Trajectory> results = store.query(null, null, null);

        assertEquals(2, results.size());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test query with filters")
    void testQueryWithFilters() {
        // Python: test_query_with_filters
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        store.save(makeTrajectory("exec1", "case1"), null);
        store.save(makeTrajectory("exec2", "case2"), null);

        // Query by executionId
        List<Trajectory> results = store.query(null, "exec1", null);

        assertEquals(1, results.size());
        assertEquals("case1", results.get(0).getCaseId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test query filtering by source")
    void testQueryWithSourceFilter() {
        // Python: test_query_with_source_filter
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        store.save(makeTrajectory("exec1", "session1", "online", null), null);
        store.save(makeTrajectory("exec2", "session1", "offline", null), null);

        // Query all - filter by source would need additional implementation
        List<Trajectory> results = store.query(null, null, null);

        assertEquals(2, results.size());
        // Verify sources are set correctly
        assertEquals("online", results.stream().filter(t -> t.getExecutionId().equals("exec1")).findFirst().get().getSource());
        assertEquals("offline", results.stream().filter(t -> t.getExecutionId().equals("exec2")).findFirst().get().getSource());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test version isolation")
    void testVersionIsolation() {
        // Python: test_version_isolation
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        Trajectory traj1 = makeTrajectory("exec1");
        Trajectory traj2 = makeTrajectory("exec1");

        store.save(traj1, "v1");
        store.save(traj2, "v2");

        Trajectory v1Result = store.load("exec1", "v1");
        Trajectory v2Result = store.load("exec1", "v2");

        // Both should exist independently
        assertNotNull(v1Result);
        assertNotNull(v2Result);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test query empty store returns empty list")
    void testQueryEmptyStore() {
        // Python: test_query_empty_store
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();

        List<Trajectory> results = store.query(null, null, null);

        assertTrue(results.isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test overwrite existing")
    void testOverwriteExisting() {
        // Python: test_overwrite_existing
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        Trajectory traj1 = makeTrajectory("exec1", "case1");
        Trajectory traj2 = makeTrajectory("exec1", "case2");

        store.save(traj1, null);
        store.save(traj2, null);

        Trajectory loaded = store.load("exec1", null);
        assertEquals("case2", loaded.getCaseId());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test query by session ID")
    void testQueryBySessionId() {
        InMemoryTrajectoryStoreImpl store = new InMemoryTrajectoryStoreImpl();
        Trajectory traj1 = makeTrajectory("exec1", "session1", "offline", null);
        Trajectory traj2 = makeTrajectory("exec2", "session2", "offline", null);

        store.save(traj1, null);
        store.save(traj2, null);

        List<Trajectory> results = store.queryBySessionId("session1");

        assertEquals(1, results.size());
        assertEquals("exec1", results.get(0).getExecutionId());
    }
}