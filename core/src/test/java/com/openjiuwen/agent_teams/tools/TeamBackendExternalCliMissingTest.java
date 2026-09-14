/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.schema.ExternalCliAgentSpec;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Supplemental parity tests for external CLI registration through {@link TeamBackend}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.external.test_external_backend} in
 * {@code tests/unit_tests/agent_teams/external/test_external_backend.py}.</p>
 */
class TeamBackendExternalCliMissingTest {

    private static final String TEAM = "ext_cli_team";

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void spawnExternalCliAgentRegistersMember() {
        BackendFixture fixture = fixture("claude", "codex");

        MemberOpResult result = fixture.backend.spawnExternalCliAgent(
                "cli-1",
                "CLI One",
                "claude",
                "senior reviewer",
                null,
                null).toCompletableFuture().join();

        assertThat(result.isOk()).as(result.getReason()).isTrue();
        assertThat(fixture.backend.isExternalCliAgent("cli-1")).isTrue();
        assertThat(fixture.backend.getExternalCliAgent("cli-1")).isEqualTo("claude");
        assertThat(fixture.backend.externalCliAgentNames()).contains("cli-1");
        assertThat(fixture.backend.getMember("cli-1").toCompletableFuture().join()).get()
                .extracting(TeamMember::getRole)
                .isEqualTo(TeamRole.TEAMMATE.value());
    }

    @Test
    void spawnExternalCliAgentUndeclaredFails() {
        BackendFixture fixture = fixture("codex");

        MemberOpResult result = fixture.backend.spawnExternalCliAgent(
                "cli-x",
                "CLI X",
                "claude",
                "x",
                null,
                null).toCompletableFuture().join();

        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).contains("not declared");
        assertThat(fixture.backend.isExternalCliAgent("cli-x")).isFalse();
    }

    @Test
    void spawnExternalCliAgentUnknownAdapterFails() {
        BackendFixture fixture = fixture("not-a-real-cli");

        MemberOpResult result = fixture.backend.spawnExternalCliAgent(
                "cli-2",
                "CLI Two",
                "not-a-real-cli",
                "x",
                null,
                null).toCompletableFuture().join();

        assertThat(result.isOk()).isFalse();
        assertThat(fixture.backend.isExternalCliAgent("cli-2")).isFalse();
    }

    @Test
    void spawnExternalCliAgentRequiresPersona() {
        BackendFixture fixture = fixture("claude");

        MemberOpResult result = fixture.backend.spawnExternalCliAgent(
                "cli-3",
                "CLI Three",
                "claude",
                "",
                null,
                null).toCompletableFuture().join();

        assertThat(result.isOk()).isFalse();
        assertThat(fixture.backend.isExternalCliAgent("cli-3")).isFalse();
    }

    @Test
    void nonExternalMemberReturnsNone() {
        BackendFixture fixture = fixture("claude");

        assertThat(fixture.backend.getExternalCliAgent("nobody")).isNull();
        assertThat(fixture.backend.isExternalCliAgent("nobody")).isFalse();
    }

    private static BackendFixture fixture(String... declared) {
        AgentTeamsContext.setSessionId("sess");
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.initialize().join();
        database.createTeam(TEAM, "Ext CLI", "leader", null, null).join();
        TeamBackend backend = new TeamBackend(
                TEAM,
                "leader",
                true,
                database,
                null,
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                false,
                true,
                externalConfigs(declared),
                null,
                null,
                null,
                null,
                null);
        return new BackendFixture(database, backend);
    }

    private static List<ExternalCliAgentSpec> externalConfigs(String... cliAgents) {
        return List.of(cliAgents).stream()
                .map(TeamBackendExternalCliMissingTest::externalConfig)
                .toList();
    }

    private static ExternalCliAgentSpec externalConfig(String cliAgent) {
        ExternalCliAgentSpec spec = new ExternalCliAgentSpec();
        spec.setCliAgent(cliAgent);
        return spec;
    }

    /**
     * Fixture matching Python's shared in-memory DB and backend factory.
     *
     * <p>Mirrors Python's {@code make_backend} fixture in
     * {@code tests/unit_tests/agent_teams/external/test_external_backend.py}.</p>
     */
    private record BackendFixture(InMemoryTeamDatabase database, TeamBackend backend) {
    }
}
