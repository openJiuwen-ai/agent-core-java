package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_paths.py}.
 */
class PathsTest {

    private final String originalUserHome = System.getProperty("user.home");

    @AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
        Paths.resetOpenjiuwenHome();
    }

    @Test
    void defaultOpenjiuwenHomeFollowsUserHome() {
        System.setProperty("user.home", "/tmp/test-home");
        Paths.resetOpenjiuwenHome();

        assertEquals(Path.of("/tmp/test-home", ".openjiuwen"), Paths.getOpenjiuwenHome());
        assertEquals(Path.of("/tmp/test-home", ".openjiuwen"), Paths.OPENJIUWEN_HOME);
        assertEquals(Path.of("/tmp/test-home", ".openjiuwen", ".agent_teams"), Paths.getAgentTeamsHome());
        assertEquals(Path.of("/tmp/test-home", ".openjiuwen", ".agent_teams"), Paths.AGENT_TEAMS_HOME);
    }

    @Test
    void configureOpenjiuwenHomeOverridesDerivedPaths() {
        Path customHome = Path.of("/tmp/custom-home/.jiuwenclaw");

        Paths.configureOpenjiuwenHome(customHome);

        assertEquals(customHome, Paths.getOpenjiuwenHome());
        assertEquals(customHome, Paths.OPENJIUWEN_HOME);
        assertEquals(customHome.resolve(".agent_teams"), Paths.getAgentTeamsHome());
        assertEquals(customHome.resolve(".agent_teams"), Paths.AGENT_TEAMS_HOME);
        assertEquals(customHome.resolve(".agent_teams").resolve("demo-team"), Paths.teamHome("demo-team"));
        assertEquals(customHome.resolve("alice_workspace"), Paths.independentMemberWorkspace("alice"));
    }

    @Test
    void resetOpenjiuwenHomeRestoresDefault() {
        System.setProperty("user.home", "/tmp/reset-home");
        Paths.configureOpenjiuwenHome("/tmp/custom-home/.jiuwenclaw");

        Paths.resetOpenjiuwenHome();

        assertEquals(Path.of("/tmp/reset-home", ".openjiuwen"), Paths.getOpenjiuwenHome());
    }
}
