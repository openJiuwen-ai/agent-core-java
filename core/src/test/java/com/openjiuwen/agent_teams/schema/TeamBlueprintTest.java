/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.models.ModelPoolEntry;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for team blueprint specifications.
 *
 * <p>Mirrors Python's {@code TeamAgentSpec} tests for
 * {@code openjiuwen/agent_teams/schema/blueprint.py}.</p>
 */
class TeamBlueprintTest {

    @Test
    void transportAndStorageSpecsResolveThroughBuiltinRegistries() {
        TransportSpec transport = new TransportSpec("pyzmq", Map.of(
                "team_name", "team-a",
                "node_id", "leader"
        ));
        StorageSpec storage = new StorageSpec("sqlite", Map.of(
                "connection_string", "sqlite:///team.db",
                "db_timeout", 15
        ));

        MessagerTransportConfig transportConfig = (MessagerTransportConfig) transport.build();
        DatabaseConfig databaseConfig = (DatabaseConfig) storage.build();

        assertThat(transportConfig.getBackend()).isEqualTo("pyzmq");
        assertThat(transportConfig.getTeamName()).isEqualTo("team-a");
        assertThat(transportConfig.getNodeId()).isEqualTo("leader");
        assertThat(databaseConfig.getDbType()).isEqualTo(DatabaseType.SQLITE);
        assertThat(databaseConfig.getConnectionString()).isEqualTo("sqlite:///team.db");
        assertThat(databaseConfig.getDbTimeout()).isEqualTo(15);
    }

    @Test
    void buildDefaultsInprocessTransportAndConfiguresLeader() {
        TeamAgentSpec spec = minimalSpec();
        spec.setSpawnMode("inprocess");
        spec.setLanguage("en");

        TeamAgent agent = spec.build();

        assertThat(spec.getTransport()).isNotNull();
        assertThat(spec.getTransport().getType()).isEqualTo("inprocess");
        assertThat(agent.getRuntimeContext().getMemberName()).isEqualTo("team_leader");
        assertThat(agent.getRuntimeContext().getTeamSpec().getLanguage()).isEqualTo("en");
        assertThat(agent.getRuntimeContext().getMessagerConfig().getBackend()).isEqualTo("inprocess");
    }

    @Test
    void buildRejectsMissingLeaderAgent() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of("teammate", new DeepAgentSpec()));

        assertThatThrownBy(spec::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leader");
    }

    @Test
    void buildRejectsPoolRouterConflictAndDuplicateExternalCliNames() {
        TeamAgentSpec conflict = minimalSpec();
        conflict.setModelPool(List.of(new ModelPoolEntry("gpt", "key", "url", "provider")));
        conflict.setModelRouter(new com.openjiuwen.agent_teams.models.ModelRouterConfig(
                "url",
                "key",
                "provider",
                List.of("gpt")
        ));

        assertThatThrownBy(conflict::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model_pool and model_router");

        TeamAgentSpec duplicateCli = minimalSpec();
        duplicateCli.setExternalCliAgents(List.of(
                Map.of("cli_agent", "codex"),
                Map.of("cli_agent", "codex")
        ));

        assertThatThrownBy(duplicateCli::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate cli_agent");
    }

    @Test
    void buildRejectsReservedAndDisabledAvatarMembers() {
        TeamAgentSpec reservedLeader = minimalSpec();
        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("user");
        reservedLeader.setLeader(leader);

        assertThatThrownBy(reservedLeader::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");

        TeamAgentSpec hittDisabled = minimalSpec();
        hittDisabled.setPredefinedMembers(List.of(new TeamMemberSpec("human-a", TeamRole.HUMAN_AGENT, "human")));
        assertThatThrownBy(hittDisabled::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enable_hitt=False");

        TeamAgentSpec bridgeDisabled = minimalSpec();
        bridgeDisabled.setPredefinedMembers(List.of(new TeamMemberSpec("bridge-a", TeamRole.BRIDGE_AGENT, "bridge")));
        assertThatThrownBy(bridgeDisabled::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enable_bridge=False");
    }

    private static TeamAgentSpec minimalSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("en");
        spec.setTeamName("team-a");
        spec.setAgents(Map.of("leader", leader));
        return spec;
    }
}
