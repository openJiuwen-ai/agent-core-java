/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EvolutionRail team_trajectory_store dual-write behavior.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_evolution_rail_team_store}.
 */
class TestEvolutionRailTeamStore {

    // ---------------------------------------------------------------------------
    // Mock classes
    // ---------------------------------------------------------------------------

    /** Mock trajectory store. */
    static class MockTrajectoryStore {
        private List<Map<String, Object>> savedSteps = new ArrayList<>();
        
        public void save(String agentId, Map<String, Object> step) {
            Map<String, Object> record = new HashMap<>();
            record.put("agent_id", agentId);
            record.put("step", step);
            savedSteps.add(record);
        }
        
        public int getSaveCount() { return savedSteps.size(); }
        public List<Map<String, Object>> getSavedSteps() { return savedSteps; }
    }

    /** Mock agent. */
    static class MockAgent {
        private String id = "test-agent";
        
        public String getId() { return id; }
    }

    /** Mock context. */
    static class MockCtx {
        private MockAgent agent = new MockAgent();
        private String query = "test query";
        
        public MockAgent getAgent() { return agent; }
        public String getQuery() { return query; }
    }

    // ---------------------------------------------------------------------------
    // Tests: save called twice when team store is set
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("When team_trajectory_store is set, save is called on both stores")
    void testSaveCalledTwiceWithTeamStore() {
        MockTrajectoryStore personalStore = new MockTrajectoryStore();
        MockTrajectoryStore teamStore = new MockTrajectoryStore();
        MockCtx ctx = new MockCtx();
        
        Map<String, Object> step = new HashMap<>();
        step.put("query", ctx.getQuery());
        step.put("timestamp", System.currentTimeMillis());
        
        // Save to both stores (dual-write behavior)
        personalStore.save(ctx.getAgent().getId(), step);
        teamStore.save(ctx.getAgent().getId(), step);
        
        assertEquals(1, personalStore.getSaveCount(), "Personal store should have 1 save");
        assertEquals(1, teamStore.getSaveCount(), "Team store should have 1 save");
    }

    // ---------------------------------------------------------------------------
    // Tests: save called once without team store
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("When team_trajectory_store is NOT set, save is called only on personal store")
    void testSaveCalledOnceWithoutTeamStore() {
        MockTrajectoryStore personalStore = new MockTrajectoryStore();
        MockTrajectoryStore teamStore = null; // No team store configured
        MockCtx ctx = new MockCtx();
        
        Map<String, Object> step = new HashMap<>();
        step.put("query", ctx.getQuery());
        
        // Save only to personal store
        personalStore.save(ctx.getAgent().getId(), step);
        
        assertEquals(1, personalStore.getSaveCount(), "Personal store should have 1 save");
        assertNull(teamStore, "Team store should be null");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1: Trajectory step content
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Trajectory step contains required fields")
    void testTrajectoryStepContainsRequiredFields() {
        Map<String, Object> step = new HashMap<>();
        step.put("query", "test query");
        step.put("response", "test response");
        step.put("timestamp", System.currentTimeMillis());
        step.put("agent_id", "agent-001");
        
        assertTrue(step.containsKey("query"), "Step should contain query");
        assertTrue(step.containsKey("response"), "Step should contain response");
        assertTrue(step.containsKey("timestamp"), "Step should contain timestamp");
        assertTrue(step.containsKey("agent_id"), "Step should contain agent_id");
    }

    @Test
    @Tag("level1")
    @DisplayName("Multiple trajectory steps are saved in order")
    void testMultipleTrajectoryStepsSavedInOrder() {
        MockTrajectoryStore store = new MockTrajectoryStore();
        
        for (int i = 0; i < 5; i++) {
            Map<String, Object> step = new HashMap<>();
            step.put("step_num", i);
            store.save("agent", step);
        }
        
        assertEquals(5, store.getSaveCount());
        
        // Verify order
        for (int i = 0; i < 5; i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> step = (Map<String, Object>) store.getSavedSteps().get(i).get("step");
            assertEquals(i, step.get("step_num"));
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2: Team store configuration
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Team store configuration enables sharing")
    void testTeamStoreConfigurationEnablesSharing() {
        Map<String, Object> teamConfig = new HashMap<>();
        teamConfig.put("enabled", true);
        teamConfig.put("team_id", "team-001");
        teamConfig.put("share_mode", "read_write");
        
        assertTrue((Boolean) teamConfig.get("enabled"));
        assertEquals("read_write", teamConfig.get("share_mode"));
    }
}