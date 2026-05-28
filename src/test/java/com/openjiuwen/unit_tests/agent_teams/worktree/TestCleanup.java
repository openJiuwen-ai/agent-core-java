/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.WorktreeCleanup;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for worktree cleanup.
 * 
 * <p>Mirrors Python's {@code test_cleanup} in
 * {@code tests.unit_tests.agent_teams.worktree.test_cleanup}.</p>
 */
@DisplayName("TestCleanup")
class TestCleanup {

    @Nested
    @DisplayName("Test isEphemeralSlug")
    class TestIsEphemeralSlug {

        @Test
        @Tag("level0")
        @DisplayName("teammate hex8 is ephemeral")
        void testTeammateHex8() {
            assertTrue(WorktreeCleanup.isEphemeralSlug("teammate-a1b2c3d4"));
        }

        @Test
        @Tag("level0")
        @DisplayName("agent hex7 is ephemeral")
        void testAgentHex7() {
            assertTrue(WorktreeCleanup.isEphemeralSlug("agent-1234567"));
        }

        @Test
        @Tag("level0")
        @DisplayName("feature branch is not ephemeral")
        void testFeatureBranchNotEphemeral() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("feature-auth"));
        }

        @Test
        @Tag("level0")
        @DisplayName("arbitrary slug is not ephemeral")
        void testArbitrarySlugNotEphemeral() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("my-worktree"));
        }

        @Test
        @Tag("level0")
        @DisplayName("teammate too short is not ephemeral")
        void testTeammateTooShort() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("teammate-abc"));
        }

        @Test
        @Tag("level1")
        @DisplayName("teammate uppercase not matched")
        void testTeammateUppercaseNotMatched() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("teammate-A1B2C3D4"));
        }

        @Test
        @Tag("level1")
        @DisplayName("agent too long is not ephemeral")
        void testAgentTooLong() {
            assertFalse(WorktreeCleanup.isEphemeralSlug("agent-12345678"));
        }

        @Test
        @Tag("level0")
        @DisplayName("null slug is not ephemeral")
        void testNullSlug() {
            assertFalse(WorktreeCleanup.isEphemeralSlug(null));
        }

        @Test
        @Tag("level0")
        @DisplayName("empty string is not ephemeral")
        void testEmptyString() {
            assertFalse(WorktreeCleanup.isEphemeralSlug(""));
        }
    }

    @Nested
    @DisplayName("Test cleanupStaleWorktrees")
    class TestCleanupStaleWorktrees {

        @Test
        @Tag("level1")
        @DisplayName("cleanup returns 0 when disabled")
        void testCleanupDisabled() {
            WorktreeConfig config = new WorktreeConfig();
            config.setEnabled(false);
            
            WorktreeCleanup cleanup = new WorktreeCleanup();
            Integer removed = cleanup.cleanupStaleWorktrees(config, null).join();
            
            assertEquals(0, removed);
        }

        @Test
        @Tag("level1")
        @DisplayName("cleanup returns 0 with null config")
        void testCleanupNullConfig() {
            WorktreeCleanup cleanup = new WorktreeCleanup();
            Integer removed = cleanup.cleanupStaleWorktrees(null, null).join();
            
            assertEquals(0, removed);
        }
    }
}
