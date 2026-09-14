/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Python parity tests for bridge-agent schema behavior.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_bridge_schema.py}.</p>
 */
class BridgeSchemaPythonParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void bridgeRoleEnumValue() {
        assertThat(TeamRole.BRIDGE_AGENT.value()).isEqualTo("bridge_agent");
    }

    @Test
    void mailboxInjectModeValues() {
        assertThat(BridgeMailboxInjectMode.PASSTHROUGH.value()).isEqualTo("passthrough");
        assertThat(BridgeMailboxInjectMode.REPHRASE.value()).isEqualTo("rephrase");
    }

    @Test
    void bridgeMemberSpecDefaults() {
        BridgeMemberSpec spec = bridge("codex", "Codex Bridge", "Senior python reviewer");

        assertThat(spec.getRoleType()).isEqualTo(TeamRole.BRIDGE_AGENT);
        assertThat(spec.getMailboxInjectMode()).isEqualTo(BridgeMailboxInjectMode.PASSTHROUGH);
        assertThat(spec.getProtocol()).isEmpty();
        assertThat(spec.getAdapterConfig()).isEmpty();
    }

    @Test
    void bridgeMemberSpecRoleTypeLocked() {
        BridgeMemberSpec spec = bridge("codex", "Codex", "x");

        assertThatThrownBy(() -> spec.setRoleType(TeamRole.TEAMMATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be bridge_agent");
    }

    @Test
    void baseTeamMemberSpecRejectsBridgeRole() {
        assertThatThrownBy(() -> new TeamMemberSpec("codex", "Codex", TeamRole.BRIDGE_AGENT, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept bridge_agent");
    }

    @Test
    void bridgeMemberSpecCustomFields() {
        BridgeMemberSpec spec = bridge("claudecode", "Claude Code Bridge", "Pair-programmer");
        spec.setMailboxInjectMode(BridgeMailboxInjectMode.REPHRASE);
        spec.setProtocol("claudecode");
        spec.setAdapterConfig(Map.of("endpoint", "stdio://claude-code", "relay_timeout_s", 60));

        assertThat(spec.getMailboxInjectMode()).isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(spec.getProtocol()).isEqualTo("claudecode");
        assertThat(spec.getAdapterConfig()).containsEntry("relay_timeout_s", 60);
    }

    @Test
    void discriminatorDispatchesBridgeSubclass() {
        TeamMemberSpec member = parseMember(Map.of(
                "member_name", "codex",
                "display_name", "Codex",
                "persona", "x",
                "role_type", "bridge_agent",
                "protocol", "codex"
        ));

        assertThat(member).isInstanceOf(BridgeMemberSpec.class);
        assertThat(((BridgeMemberSpec) member).getProtocol()).isEqualTo("codex");
    }

    @Test
    void discriminatorKeepsBaseForTeammate() {
        TeamMemberSpec member = parseMember(Map.of(
                "member_name", "alice",
                "display_name", "Alice",
                "persona", "x",
                "role_type", "teammate"
        ));

        assertThat(member.getClass()).isEqualTo(TeamMemberSpec.class);
        assertThat(member.getRoleType()).isEqualTo(TeamRole.TEAMMATE);
    }

    @Test
    void discriminatorMixedPredefined() {
        List<TeamMemberSpec> members = List.of(
                parseMember(Map.of(
                        "member_name", "codex",
                        "display_name", "Codex",
                        "persona", "x",
                        "role_type", "bridge_agent"
                )),
                parseMember(Map.of(
                        "member_name", "alice",
                        "display_name", "Alice",
                        "persona", "y",
                        "role_type", "teammate"
                )),
                parseMember(Map.of(
                        "member_name", "bob_human",
                        "display_name", "Bob",
                        "persona", "z",
                        "role_type", "human_agent"
                ))
        );

        assertThat(members.get(0)).isInstanceOf(BridgeMemberSpec.class);
        assertThat(members.get(1).getClass()).isEqualTo(TeamMemberSpec.class);
        assertThat(members.get(1).getRoleType()).isEqualTo(TeamRole.TEAMMATE);
        assertThat(members.get(2).getClass()).isEqualTo(TeamMemberSpec.class);
        assertThat(members.get(2).getRoleType()).isEqualTo(TeamRole.HUMAN_AGENT);
    }

    @Test
    void discriminatorRoundTripViaDict() {
        List<TeamMemberSpec> members = List.of(
                parseMember(Map.of(
                        "member_name", "codex",
                        "display_name", "Codex",
                        "persona", "x",
                        "role_type", "bridge_agent",
                        "mailbox_inject_mode", "rephrase",
                        "protocol", "codex",
                        "adapter_config", Map.of("endpoint", "stdio://codex")
                )),
                parseMember(Map.of(
                        "member_name", "alice",
                        "display_name", "Alice",
                        "persona", "y",
                        "role_type", "teammate"
                ))
        );

        BridgeMemberSpec bridge = (BridgeMemberSpec) members.get(0);
        assertThat(bridge.getMailboxInjectMode()).isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(bridge.getAdapterConfig()).containsEntry("endpoint", "stdio://codex");
        assertThat(members.get(1).getClass()).isEqualTo(TeamMemberSpec.class);
    }

    @Test
    void legacyDumpWithoutBridgeFieldsStillLoads() {
        TeamMemberSpec member = parseMember(Map.of(
                "member_name", "alice",
                "display_name", "Alice",
                "persona", "x",
                "role_type", "teammate"
        ));
        TeamAgentSpec spec = minimalSpec();
        spec.setPredefinedMembers(List.of(member.toConfiguratorSpec()));

        assertThat(spec.isEnableBridge()).isFalse();
        assertThat(member.getClass()).isEqualTo(TeamMemberSpec.class);
    }

    @Test
    void enableBridgeTrueWithPredefinedPasses() {
        BridgeMemberSpec bridge = bridge("codex", "Codex", "x");
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableBridge(true);
        spec.setPredefinedMembers(List.of(bridge.toConfiguratorSpec()));

        assertThatCode(spec::build).doesNotThrowAnyException();
    }

    @Test
    void enableBridgeTrueWithoutPredefinedPasses() {
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableBridge(true);

        assertThatCode(spec::build).doesNotThrowAnyException();
    }

    @Test
    void enableBridgeFalseWithPredefinedRaises() {
        BridgeMemberSpec bridge = bridge("codex", "Codex", "x");
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableBridge(false);
        spec.setPredefinedMembers(List.of(bridge.toConfiguratorSpec()));

        assertThatThrownBy(spec::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enable_bridge=False");
    }

    @Test
    void enableBridgeFalseNoPredefinedPasses() {
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableBridge(false);

        assertThatCode(spec::build).doesNotThrowAnyException();
    }

    @Test
    void enableBridgeDefaultIsFalse() {
        assertThat(minimalSpec().isEnableBridge()).isFalse();
    }

    private static TeamMemberSpec parseMember(Map<String, Object> values) {
        return MAPPER.convertValue(values, TeamMemberSpec.class);
    }

    private static BridgeMemberSpec bridge(String memberName, String displayName, String persona) {
        BridgeMemberSpec spec = new BridgeMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(displayName);
        spec.setPersona(persona);
        return spec;
    }

    private static TeamAgentSpec minimalSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("bridge_team");
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        return spec;
    }
}
