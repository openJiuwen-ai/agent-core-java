/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.models.ModelPoolEntry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for team-level schema models.
 *
 * <p>Mirrors Python's schema dataclass and pydantic model behavior in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
class TeamSchemaTest {

    @Test
    void memberOpResultFactoriesPreserveTruthinessAndReason() {
        MemberOpResult success = MemberOpResult.success();
        MemberOpResult failure = MemberOpResult.fail("duplicate member");

        assertThat(success.isOk()).isTrue();
        assertThat(success.asBoolean()).isTrue();
        assertThat(success.getReason()).isEmpty();
        assertThat(failure.isOk()).isFalse();
        assertThat(failure.asBoolean()).isFalse();
        assertThat(failure.getReason()).isEqualTo("duplicate member");
    }

    @Test
    void enumValuesRoundTripPythonWireNames() {
        assertThat(TeamLifecycle.fromValue("persistent")).isEqualTo(TeamLifecycle.PERSISTENT);
        assertThat(TeamRole.fromValue("human_agent")).isEqualTo(TeamRole.HUMAN_AGENT);
        assertThat(BridgeMailboxInjectMode.fromValue("rephrase")).isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(TeamRole.BRIDGE_AGENT.toConfiguratorRole())
                .isEqualTo(AgentConfigurator.TeamRole.BRIDGE_AGENT);
    }

    @Test
    void baseMemberSpecRejectsBridgeRoleAndBridgeSpecRequiresIt() {
        TeamMemberSpec teammate = new TeamMemberSpec("dev", "Developer", TeamRole.TEAMMATE, "write code");

        assertThat(teammate.getRoleType()).isEqualTo(TeamRole.TEAMMATE);
        assertThatThrownBy(() -> teammate.setRoleType(TeamRole.BRIDGE_AGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bridge_agent");

        BridgeMemberSpec bridge = new BridgeMemberSpec();
        bridge.setMemberName("remote");
        bridge.setAdapterConfig(Map.of("endpoint", "http://example.test"));

        assertThat(bridge.getRoleType()).isEqualTo(TeamRole.BRIDGE_AGENT);
        assertThat(bridge.getMailboxInjectMode()).isEqualTo(BridgeMailboxInjectMode.PASSTHROUGH);
        assertThat(bridge.getProtocol()).isEmpty();
        assertThat(bridge.getAdapterConfig()).containsEntry("endpoint", "http://example.test");
        assertThatThrownBy(() -> bridge.setRoleType(TeamRole.TEAMMATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be bridge_agent");
    }

    @Test
    void externalCliDefaultsMatchPythonModelDefaults() {
        ExternalCliAgentSpec spec = new ExternalCliAgentSpec();

        assertThat(spec.isInjectMcp()).isTrue();
        assertThat(spec.getCommand()).isNull();
        assertThat(spec.getCwd()).isNull();
        assertThat(spec.getMcpServerCommand()).containsExactly("openjiuwen-team-mcp");
        assertThat(spec.getEnv()).isEmpty();
    }

    @Test
    void teamSpecCopiesMutableCollectionsAndConvertsToConfiguratorSpec() {
        ModelPoolEntry entry = new ModelPoolEntry("gpt", "key", "url", "provider");
        TeamSpec spec = new TeamSpec("team-a", "Team A", "leader");
        spec.setLanguage("en");
        spec.setMetadata(Map.of("purpose", "qa"));
        spec.setModelPool(List.of(entry));
        spec.setModelPoolStrategy("by_model_name");

        AgentConfigurator.TeamSpec runtime = spec.toConfiguratorSpec();

        assertThat(runtime.getTeamName()).isEqualTo("team-a");
        assertThat(runtime.getLanguage()).isEqualTo("en");
        assertThat(runtime.getMetadata()).containsEntry("purpose", "qa");
        assertThat(runtime.getModelPool()).hasSize(1);
        assertThat(runtime.getModelPool().get(0)).isSameAs(entry);
        assertThat(runtime.getModelPoolStrategy()).isEqualTo("by_model_name");
        assertThat(spec.getModelPool()).containsExactly(entry);
    }

    @Test
    void runtimeContextDefaultsAndConvertsToConfiguratorContext() {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setMemberName("leader");
        context.setPersona("coordinate");
        context.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        context.setDbConfig(Map.of("db_type", "memory", "connection_string", ""));
        context.setCliAgent("codex");

        AgentConfigurator.TeamRuntimeContext runtime = context.toConfiguratorContext();

        assertThat(context.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(runtime.getRole()).isEqualTo(AgentConfigurator.TeamRole.LEADER);
        assertThat(runtime.getMemberName()).isEqualTo("leader");
        assertThat(runtime.getPersona()).isEqualTo("coordinate");
        assertThat(runtime.getTeamSpec().getTeamName()).isEqualTo("team-a");
        assertThat(runtime.getDbConfig()).containsEntry("db_type", "memory");
        assertThat(runtime.getCliAgent()).isEqualTo("codex");
    }

    @Test
    void packageFacadeListsTeamModuleExports() {
        assertThat(TeamSchemaPackage.PYTHON_MODULE).isEqualTo("openjiuwen/agent_teams/schema/team.py");
        assertThat(TeamSchemaPackage.EXPORTED_SYMBOLS)
                .contains("MemberOpResult", "TeamRole", "BridgeMemberSpec", "TeamRuntimeContext");
    }
}
