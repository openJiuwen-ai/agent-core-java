/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseEngine;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_human_agent_role_restore} in
 * {@code tests/unit_tests/agent_teams/test_human_agent_role_restore.py}.
 */
class HumanAgentRoleRestorePythonParityTest {

    private static final String TEAM_NAME = "restore_team";
    private static final String LEADER_NAME = "team_leader";

    @Test
    void testSpawnMemberPersistsHumanAgentRole() {
        InMemoryTeamDatabase database = seededDatabase();
        TeamBackend backend = backend(database);

        MemberOpResult result = backend.spawnMember(
                "alice",
                "Alice",
                new AgentCard(),
                "user avatar",
                null,
                MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE,
                MemberMode.BUILD_MODE,
                null,
                com.openjiuwen.agent_teams.schema.TeamRole.HUMAN_AGENT
        ).toCompletableFuture().join();

        assertThat(result.isOk()).isTrue();
        assertThat(database.getMember("alice", TEAM_NAME).join()).get()
                .extracting(com.openjiuwen.agent_teams.tools.TeamMember::getRole)
                .isEqualTo(com.openjiuwen.agent_teams.schema.TeamRole.HUMAN_AGENT.value());
    }

    @Test
    void testSpawnMemberDefaultRoleIsTeammate() {
        InMemoryTeamDatabase database = seededDatabase();
        TeamBackend backend = backend(database);

        MemberOpResult result = backend.spawnMember(
                "dev-1",
                "Dev 1",
                new AgentCard(),
                "backend dev",
                null,
                MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE,
                MemberMode.BUILD_MODE,
                null,
                null
        ).toCompletableFuture().join();

        assertThat(result.isOk()).isTrue();
        assertThat(database.getMember("dev-1", TEAM_NAME).join()).get()
                .extracting(com.openjiuwen.agent_teams.tools.TeamMember::getRole)
                .isEqualTo(com.openjiuwen.agent_teams.schema.TeamRole.TEAMMATE.value());
    }

    @Test
    void testDynamicHumanAgentSurvivesBackendRestart() {
        InMemoryTeamDatabase database = seededDatabase();
        TeamBackend backend1 = backend(database);

        MemberOpResult spawnResult = backend1.spawnHumanAgent(
                "alice",
                "Alice",
                "user avatar",
                null
        ).toCompletableFuture().join();
        assertThat(spawnResult.isOk()).isTrue();
        assertThat(backend1.isHumanAgent("alice").toCompletableFuture().join()).isTrue();

        TeamBackend backend2 = backend(database);

        assertThat(backend2.isHumanAgent("alice").toCompletableFuture().join()).isTrue();
        assertThat(backend2.humanAgentNames().toCompletableFuture().join()).contains("alice");
    }

    @Test
    void testPredefinedHumanAgentSurvivesBackendRestart() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend predefinedBackend = backendWithPredefinedHuman(database);
        predefinedBackend.buildTeam("Restore", "t", "Leader", "p").toCompletableFuture().join();
        assertThat(predefinedBackend.isHumanAgent("alice").toCompletableFuture().join()).isTrue();

        TeamBackend backend2 = backend(database);

        assertThat(backend2.isHumanAgent("alice").toCompletableFuture().join()).isTrue();
    }

    @Test
    void testBuildContextReadsRoleFromMemberRow() {
        InMemoryTeamDatabase database = seededDatabase();
        TeamBackend backend = backend(database);
        backend.spawnMember(
                "alice",
                "Alice",
                new AgentCard(),
                "user avatar",
                null,
                MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE,
                MemberMode.BUILD_MODE,
                null,
                com.openjiuwen.agent_teams.schema.TeamRole.HUMAN_AGENT
        ).toCompletableFuture().join();

        TeamRuntimeContext ctx = spawnManager(database).buildContextFromDb("alice").toCompletableFuture().join();

        assertThat(ctx).isNotNull();
        assertThat(ctx.getRole()).isEqualTo(AgentConfigurator.TeamRole.HUMAN_AGENT);
    }

    @Test
    void testBuildContextReturnsTeammateForOrdinaryMember() {
        InMemoryTeamDatabase database = seededDatabase();
        TeamBackend backend = backend(database);
        backend.spawnMember(
                "dev-1",
                "Dev 1",
                new AgentCard(),
                "backend dev",
                null,
                MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE,
                MemberMode.BUILD_MODE,
                null,
                null
        ).toCompletableFuture().join();

        TeamRuntimeContext ctx = spawnManager(database).buildContextFromDb("dev-1").toCompletableFuture().join();

        assertThat(ctx).isNotNull();
        assertThat(ctx.getRole()).isEqualTo(AgentConfigurator.TeamRole.TEAMMATE);
    }

    @Test
    void testLegacyTeamMemberTableGetsRoleColumn(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("legacy.db");
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            connection.createStatement().execute("""
                    CREATE TABLE team_member (
                        member_name TEXT NOT NULL,
                        team_name TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        agent_card TEXT NOT NULL,
                        status TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        PRIMARY KEY (member_name, team_name)
                    )
                    """);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO team_member (
                        member_name, team_name, display_name, agent_card, status, mode
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, "legacy_member");
                statement.setString(2, TEAM_NAME);
                statement.setString(3, "Legacy");
                statement.setString(4, "{}");
                statement.setString(5, "READY");
                statement.setString(6, "build_mode");
                statement.executeUpdate();
            }
        }

        DatabaseConfig config = DatabaseConfig.builder()
                .dbType(DatabaseType.SQLITE)
                .connectionString(dbPath.toString())
                .build();
        DatabaseEngine engine = new DatabaseEngine(config).initialize().join();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            List<String> columns;
            try (var resultSet = connection.createStatement().executeQuery("PRAGMA table_info(team_member)")) {
                java.util.ArrayList<String> names = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    names.add(resultSet.getString("name"));
                }
                columns = names;
            }
            assertThat(columns).contains("role");
            try (var statement = connection.prepareStatement(
                    "SELECT role FROM team_member WHERE member_name = ?")) {
                statement.setString(1, "legacy_member");
                try (var resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("role"))
                            .isEqualTo(com.openjiuwen.agent_teams.schema.TeamRole.TEAMMATE.value());
                }
            }
        } finally {
            engine.close();
        }
    }

    private static InMemoryTeamDatabase seededDatabase() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createTeam(TEAM_NAME, "Restore", LEADER_NAME, null, null).join();
        return database;
    }

    private static TeamBackend backend(InMemoryTeamDatabase database) {
        return new TeamBackend(
                TEAM_NAME,
                LEADER_NAME,
                true,
                database,
                null,
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                null,
                null,
                LEADER_NAME
        );
    }

    private static TeamBackend backendWithPredefinedHuman(InMemoryTeamDatabase database) {
        com.openjiuwen.agent_teams.schema.TeamMemberSpec alice =
                new com.openjiuwen.agent_teams.schema.TeamMemberSpec(
                        "alice",
                        "Alice",
                        com.openjiuwen.agent_teams.schema.TeamRole.HUMAN_AGENT,
                        "Visual designer"
                );
        return new TeamBackend(
                TEAM_NAME,
                LEADER_NAME,
                true,
                database,
                null,
                MemberMode.BUILD_MODE,
                List.of(alice),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                null,
                null,
                LEADER_NAME
        );
    }

    private static SpawnManager spawnManager(InMemoryTeamDatabase database) {
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("leader", "Leader", "p"));
        AgentConfigurator.TeamAgentSpec spec = new AgentConfigurator.TeamAgentSpec();
        spec.setTeamName(TEAM_NAME);
        spec.setAgents(Map.of("leader", new AgentConfigurator.DeepAgentSpec()));
        TeamRuntimeContext baseCtx = new TeamRuntimeContext();
        baseCtx.setRole(AgentConfigurator.TeamRole.LEADER);
        baseCtx.setMemberName(LEADER_NAME);
        baseCtx.setTeamSpec(new AgentConfigurator.TeamSpec(TEAM_NAME, "Restore", LEADER_NAME));
        configurator.setupInfra(spec, baseCtx);
        configurator.setTeamBackend(new ConfiguredTeamBackend(
                TEAM_NAME,
                LEADER_NAME,
                true,
                Map.of(),
                null,
                MemberMode.BUILD_MODE.value(),
                List.of(),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                LEADER_NAME,
                new MemoryMemberStore(database)
        ));
        return new SpawnManager(new EmptyState(), configurator, () -> null);
    }

    private record MemoryMemberStore(InMemoryTeamDatabase database)
            implements com.openjiuwen.agent_teams.agent.TeamMember.MemberStore {
        @Override
        public CompletionStage<com.openjiuwen.agent_teams.agent.TeamMember.MemberSnapshot> getMember(
                String memberName, String teamName) {
            return database.getMember(memberName, teamName).thenApply(optional -> optional
                    .map(member -> new com.openjiuwen.agent_teams.agent.TeamMember.MemberSnapshot(
                            member.getStatus(),
                            member.getExecutionStatus(),
                            member.getMemberName(),
                            member.getRole(),
                            member.getDesc(),
                            member.getPrompt(),
                            member.getModelRefJson()
                    ))
                    .orElse(null));
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            return database.updateMemberStatus(memberName, teamName, status);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            return database.updateMemberExecutionStatus(memberName, teamName, status);
        }
    }

    private static final class EmptyState implements SessionManager.TeamAgentStateView {
        @Override
        public SessionManager.AgentTeamSessionView getTeamSession() {
            return null;
        }

        @Override
        public void setTeamSession(SessionManager.AgentTeamSessionView session) {
            // Build-context tests do not touch session state.
        }
    }
}
