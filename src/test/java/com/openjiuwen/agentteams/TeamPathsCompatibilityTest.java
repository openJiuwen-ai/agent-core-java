
package com.openjiuwen.agentteams;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

class TeamPathsCompatibilityTest {
    @AfterEach
    void resetHome() {
        TeamPaths.resetOpenjiuwenHome();
    }

    @Test
    void shouldReturnDefaultOpenjiuwenHome() {
        Path home = TeamPaths.getOpenjiuwenHome();
        assertThat(home).isNotNull();
        assertThat(home.endsWith(".openjiuwen")).isTrue();
    }

    @Test
    void shouldReturnAgentTeamsHome() {
        Path home = TeamPaths.getAgentTeamsHome();
        assertThat(home).isNotNull();
        assertThat(home.endsWith(Paths.get(".openjiuwen", ".agent_teams"))).isTrue();
    }

    @Test
    void shouldReturnTeamHome() {
        Path home = TeamPaths.teamHome("test_team");
        assertThat(home).isNotNull();
        assertThat(home.endsWith(Paths.get(".openjiuwen", ".agent_teams", "test_team"))).isTrue();
    }

    @Test
    void shouldReturnIndependentMemberWorkspace() {
        Path ws = TeamPaths.independentMemberWorkspace("member1");
        assertThat(ws).isNotNull();
        assertThat(ws.endsWith(Paths.get(".openjiuwen", "member1_workspace"))).isTrue();
    }

    @Test
    void shouldReturnTeamMemoryDir() {
        Path dir = TeamPaths.teamMemoryDir("test_team");
        assertThat(dir).isNotNull();
        assertThat(dir.endsWith(Paths.get(".openjiuwen", ".agent_teams", "test_team", "team-workspace", "team-memory")))
                .isTrue();
    }

    @Test
    void shouldHonorConfiguredHome() {
        Path custom = Paths.get("/custom/home");
        TeamPaths.configureOpenjiuwenHome(custom);
        Path home = TeamPaths.getOpenjiuwenHome();
        assertThat(home).isEqualTo(custom);
        Path teamHome = TeamPaths.teamHome("myteam");
        assertThat(teamHome.startsWith(custom)).isTrue();
    }

    @Test
    void shouldResetHomeAfterConfigure() {
        TeamPaths.configureOpenjiuwenHome("/tmp/test");
        TeamPaths.resetOpenjiuwenHome();
        Path home = TeamPaths.getOpenjiuwenHome();
        assertThat(home.endsWith(".openjiuwen")).isTrue();
    }
}
