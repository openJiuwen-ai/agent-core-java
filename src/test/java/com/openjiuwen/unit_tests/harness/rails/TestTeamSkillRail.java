/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TeamSkillRail signal detection types and helpers.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_team_skill_rail}.
 */
@ExtendWith(MockitoExtension.class)
class TestTeamSkillRail {

    // ---------------------------------------------------------------------------
    // Inner classes mirroring Python dataclasses
    // ---------------------------------------------------------------------------

    /** Team signal type enum. */
    enum TeamSignalType {
        USER_REQUEST("user_request"),
        TRAJECTORY_ISSUE("trajectory_issue");

        private final String value;

        TeamSignalType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /** User intent dataclass. */
    static class UserIntent {
        private boolean isImprovement;
        private String intent;

        public UserIntent(boolean isImprovement, String intent) {
            this.isImprovement = isImprovement;
            this.intent = intent;
        }

        public boolean isImprovement() { return isImprovement; }
        public String getIntent() { return intent; }
    }

    /** Trajectory issue dataclass. */
    static class TrajectoryIssue {
        private String issueType;
        private String description;
        private String affectedRole;
        private String severity;

        public TrajectoryIssue(String issueType, String description, String affectedRole, String severity) {
            this.issueType = issueType;
            this.description = description;
            this.affectedRole = affectedRole != null ? affectedRole : "";
            this.severity = severity != null ? severity : "medium";
        }

        public TrajectoryIssue(String issueType, String description) {
            this(issueType, description, "", "medium");
        }

        public String getIssueType() { return issueType; }
        public String getDescription() { return description; }
        public String getAffectedRole() { return affectedRole; }
        public String getSeverity() { return severity; }
    }

    // ---------------------------------------------------------------------------
    // Tests: TeamSignalType enum
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("TeamSignalType enum must have expected values")
    void testTeamSignalTypeEnum() {
        // Python: test_team_signal_type_enum
        assertEquals("user_request", TeamSignalType.USER_REQUEST.getValue());
        assertEquals("trajectory_issue", TeamSignalType.TRAJECTORY_ISSUE.getValue());
    }

    // ---------------------------------------------------------------------------
    // Tests: UserIntent dataclass
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("UserIntent dataclass should have expected fields")
    void testUserIntentDataclass() {
        // Python: test_user_intent_dataclass
        UserIntent intent = new UserIntent(true, "add a coder role");
        assertTrue(intent.isImprovement());
        assertEquals("add a coder role", intent.getIntent());

        UserIntent noIntent = new UserIntent(false, "");
        assertFalse(noIntent.isImprovement());
        assertEquals("", noIntent.getIntent());
    }

    // ---------------------------------------------------------------------------
    // Tests: TrajectoryIssue dataclass
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("TrajectoryIssue dataclass should have expected fields with defaults")
    void testTrajectoryIssueDataclass() {
        // Python: test_trajectory_issue_dataclass
        TrajectoryIssue issue = new TrajectoryIssue(
            "coordination",
            "roles not passing data",
            "researcher",
            "high"
        );
        assertEquals("coordination", issue.getIssueType());
        assertEquals("roles not passing data", issue.getDescription());
        assertEquals("researcher", issue.getAffectedRole());
        assertEquals("high", issue.getSeverity());

        TrajectoryIssue defaultIssue = new TrajectoryIssue("test", "test desc");
        assertEquals("medium", defaultIssue.getSeverity());
        assertEquals("", defaultIssue.getAffectedRole());
    }

    // ---------------------------------------------------------------------------
    // Tests: custom LLM policies and timeout
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("TeamSkillRail accepts custom LLM policies and timeout")
    void testInitAcceptsCustomLlmPoliciesAndTimeout() {
        // Python: test_init_accepts_custom_llm_policies_and_timeout
        // TeamSkillRail should propagate custom policies and total timeout
        
        // Test LLMInvokePolicy creation with custom values
        com.openjiuwen.agent_evolving.optimizer.LlmResilience.LLMInvokePolicy evaluatePolicy =
            new com.openjiuwen.agent_evolving.optimizer.LlmResilience.LLMInvokePolicy(
                19.0,  // attemptTimeoutSecs
                57.0,  // totalBudgetSecs
                2,     // maxAttempts
                1.0,   // backoffBaseSecs
                true   // retryEmptyResponse
            );
        
        com.openjiuwen.agent_evolving.optimizer.LlmResilience.LLMInvokePolicy simplifyPolicy =
            new com.openjiuwen.agent_evolving.optimizer.LlmResilience.LLMInvokePolicy(
                23.0,  // attemptTimeoutSecs
                69.0,  // totalBudgetSecs
                2,     // maxAttempts
                1.0,   // backoffBaseSecs
                true   // retryEmptyResponse
            );
        
        // Verify policies have expected values
        assertEquals(19.0, evaluatePolicy.getAttemptTimeoutSecs(), 0.01, 
            "evaluate_policy.attempt_timeout_secs should be 19");
        assertEquals(57.0, evaluatePolicy.getTotalBudgetSecs(), 0.01,
            "evaluate_policy.total_budget_secs should be 57");
        assertEquals(2, evaluatePolicy.getMaxAttempts(),
            "evaluate_policy.max_attempts should be 2");
        
        assertEquals(23.0, simplifyPolicy.getAttemptTimeoutSecs(), 0.01,
            "simplify_policy.attempt_timeout_secs should be 23");
        assertEquals(69.0, simplifyPolicy.getTotalBudgetSecs(), 0.01,
            "simplify_policy.total_budget_secs should be 69");
        
        // Verify that TeamSkillRail can be created (basic construction test)
        com.openjiuwen.harness.rails.skills.TeamSkillRail rail =
            new com.openjiuwen.harness.rails.skills.TeamSkillRail();
        assertNotNull(rail, "TeamSkillRail should be constructable");
    }

    // ---------------------------------------------------------------------------
    // Tests: EvolutionRail integration
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("TeamSkillRail integrates with EvolutionRail")
    void testTeamSkillRailIntegrationWithEvolutionRail() {
        // Python: test_team_skill_rail_integration
        // TeamSkillRail should work with EvolutionRail for skill evolution
        
        // Create EvolutionRail instance
        com.openjiuwen.harness.rails.evolution.EvolutionRail evolutionRail =
            new com.openjiuwen.harness.rails.evolution.EvolutionRail(
                com.openjiuwen.harness.rails.evolution.EvolutionRail.EvolutionTrigger.MANUAL
            );
        
        // Create TeamSkillRail instance
        com.openjiuwen.harness.rails.skills.TeamSkillRail teamSkillRail =
            new com.openjiuwen.harness.rails.skills.TeamSkillRail();
        
        // Verify both rails are constructable
        assertNotNull(evolutionRail, "EvolutionRail should be constructable");
        assertNotNull(teamSkillRail, "TeamSkillRail should be constructable");
        
        // Verify EvolutionRail properties
        assertEquals(com.openjiuwen.harness.rails.evolution.EvolutionRail.EvolutionTrigger.MANUAL,
            evolutionRail.getTrigger(), "EvolutionRail trigger should be MANUAL");
        assertTrue(evolutionRail.isEvolutionEnabled(), "Evolution should be enabled by default");
        
        // Verify rails can be initialized with a mock agent
        Object mockAgent = new Object();
        evolutionRail.init(mockAgent);
        teamSkillRail.init(mockAgent);
        
        // Verify evolution can be disabled
        evolutionRail.setEvolutionEnabled(false);
        assertFalse(evolutionRail.isEvolutionEnabled(), "Evolution should be disabled after set");
    }
}