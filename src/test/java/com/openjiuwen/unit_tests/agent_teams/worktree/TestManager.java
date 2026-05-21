/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeManager;
import com.openjiuwen.agent_teams.worktree.WorktreeSession;
import com.openjiuwen.agent_teams.worktree.WorktreeSessionHolder;
import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;

/**
 * Tests for worktree manager module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_manager}.
 */
class TestManager {

    private static final String MOCK_WORKSPACE = "/mock/workspace";

    @BeforeEach
    void setUp() {
        InProcessMessager.cleanupBus();
        WorktreeSessionHolder.setCurrentSession(null);
    }

    @AfterEach
    void tearDown() {
        InProcessMessager.cleanupBus();
        WorktreeSessionHolder.setCurrentSession(null);
    }

    // ---------------------------------------------------------------------------
    // TestEnter
    // ---------------------------------------------------------------------------

    @Nested
    class TestEnter {

        @Test
        @Tag("level0")
        void testEnterCreatesWorktreeSession() {
            MessagerTransportConfig transportConfig = new MessagerTransportConfig();
            transportConfig.setNodeId("leader");
            InProcessMessager messager = new InProcessMessager(transportConfig);
            WorktreeConfig config = new WorktreeConfig(true);
            config.setBaseDir("build/test-worktrees");

            WorktreeManager mgr = new WorktreeManager(config, messager, "workspace-root");
            WorktreeSession session = mgr.enter("test-slug", "member-1", "team-1");

            assertNotNull(session);
            assertEquals("test-slug", session.getSlug());
            assertEquals("member-1", session.getMemberName());
            assertEquals("team-1", session.getTeamName());
            assertEquals(session, WorktreeSessionHolder.getCurrentSession());
        }

        @Test
        @Tag("level0")
        void testEnterInvalidSlugRaises() {
            MessagerTransportConfig transportConfig = new MessagerTransportConfig();
            transportConfig.setNodeId("leader");
            InProcessMessager messager = new InProcessMessager(transportConfig);
            WorktreeConfig config = new WorktreeConfig(true);

            WorktreeManager mgr = new WorktreeManager(config, messager, "workspace-root");

            assertThrows(IllegalArgumentException.class, () ->
                mgr.enter("../escape", "m1", "t1")
            );
        }
    }

    // ---------------------------------------------------------------------------
    // TestExit
    // ---------------------------------------------------------------------------

    @Nested
    class TestExit {

        @Test
        @Tag("level0")
        void testExitRemovesSession() {
            MessagerTransportConfig transportConfig = new MessagerTransportConfig();
            transportConfig.setNodeId("leader");
            InProcessMessager messager = new InProcessMessager(transportConfig);
            WorktreeConfig config = new WorktreeConfig(true);
            config.setBaseDir("build/test-worktrees");

            WorktreeManager mgr = new WorktreeManager(config, messager, "workspace-root");
            mgr.enter("exit-slug", "m1", "t1");

            boolean removed = mgr.removeCurrent(true);
            assertTrue(removed);
            assertNull(WorktreeSessionHolder.getCurrentSession());
        }
    }

    // ---------------------------------------------------------------------------
    // TestConfig
    // ---------------------------------------------------------------------------

    @Nested
    class TestConfig {

        @Test
        @Tag("level0")
        void testConfigDefaults() {
            WorktreeConfig config = new WorktreeConfig();
            assertFalse(config.isEnabled());
            assertTrue(config.isAutoCleanupOnShutdown());
        }

        @Test
        @Tag("level0")
        void testConfigEnabled() {
            WorktreeConfig config = new WorktreeConfig(true);
            assertTrue(config.isEnabled());
        }

        @Test
        @Tag("level0")
        void testConfigBaseDir() {
            WorktreeConfig config = new WorktreeConfig();
            config.setBaseDir("/custom/path");
            assertEquals("/custom/path", config.getBaseDir());
        }
    }
}