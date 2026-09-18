/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_paths} module in
 * {@code tests/unit_tests/agent_teams/test_paths.py}.
 */
class AgentTeamPathsTest {

    private final String originalUserHome = System.getProperty("user.home");

    @AfterEach
    void tearDown() {
        AgentTeamPaths.resetOpenjiuwenHome();
        if (originalUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void defaultOpenjiuwenHomeUsesUserHome() {
        System.setProperty("user.home", "/tmp/test-home");

        assertEquals(Path.of("/tmp/test-home/.openjiuwen"), AgentTeamPaths.getOpenjiuwenHome());
        assertEquals(Path.of("/tmp/test-home/.openjiuwen/.agent_teams"), AgentTeamPaths.getAgentTeamsHome());
    }

    @Test
    void configureOpenjiuwenHomeOverridesDerivedPaths() {
        Path customHome = Path.of("/tmp/custom-home/.jiuwenclaw");
        AgentTeamPaths.configureOpenjiuwenHome(customHome);

        assertEquals(customHome, AgentTeamPaths.getOpenjiuwenHome());
        assertEquals(customHome.resolve(".agent_teams"), AgentTeamPaths.getAgentTeamsHome());
        assertEquals(customHome.resolve(".agent_teams").resolve("demo-team"), AgentTeamPaths.teamHome("demo-team"));
        assertEquals(customHome.resolve("alice_workspace"), AgentTeamPaths.independentMemberWorkspace("alice"));
    }

    @Test
    void resetOpenjiuwenHomeRestoresDefault() {
        System.setProperty("user.home", "/tmp/reset-home");
        AgentTeamPaths.configureOpenjiuwenHome("/tmp/custom-home/.jiuwenclaw");

        AgentTeamPaths.resetOpenjiuwenHome();

        assertEquals(Path.of("/tmp/reset-home/.openjiuwen"), AgentTeamPaths.getOpenjiuwenHome());
    }
}
