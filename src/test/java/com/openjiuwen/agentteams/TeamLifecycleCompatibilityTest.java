/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Mirrors Python 0.1.15 {@code test_predefined_team.py} +
 * {@code test_persistent_team.py}.
 * Validates predefined team member registration and lifecycle modes.
 */
class TeamLifecycleCompatibilityTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupInprocessBus();
        TeamBackend.resetSharedDbCache();
    }

    // --- Predefined team ---

    @Test
    void predefinedTeam_registersAllMembers() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("predefined-team")
                .lifecycle("temporary")
                .teamMode("predefined")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER)
                                .description("Lead").build(),
                        TeamMemberSpec.builder().name("backend-dev").role(TeamRole.MEMBER)
                                .description("Backend dev").build(),
                        TeamMemberSpec.builder().name("frontend-dev").role(TeamRole.MEMBER)
                                .description("Frontend dev").build()))
                .build());

        TeamBackend backend = agent.getTeamBackend();
        // Leader is registered separately; members list contains teammates
        assertThat(backend.listMembers()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(backend.listMembers().stream().map(m -> m.getMemberName()).toList())
                .containsExactlyInAnyOrder("backend-dev", "frontend-dev");
    }

    @Test
    void predefinedTeam_leaderStatusIsReady() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("predefined-leader")
                .lifecycle("temporary")
                .teamMode("predefined")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build());

        TeamBackend backend = agent.getTeamBackend();
        // Leader is registered in the member table; status is ready after build
        assertThat(backend.getDb().member.getMember("leader1", "predefined-leader").getStatus())
                .isEqualTo(MemberStatus.READY.value());
    }

    @Test
    void predefinedTeam_teammateStatusIsUnstarted() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("predefined-unstarted")
                .lifecycle("temporary")
                .teamMode("predefined")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER)
                                .description("A dev").build()))
                .build());

        TeamBackend backend = agent.getTeamBackend();
        assertThat(backend.getDb().member.getMember("dev-1", "predefined-unstarted").getStatus())
                .isEqualTo(MemberStatus.UNSTARTED.value());
    }

    @Test
    void predefinedTeam_memberIsRegistered() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("predefined-desc")
                .lifecycle("temporary")
                .teamMode("predefined")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER)
                                .description("Senior backend dev").build()))
                .build());

        TeamBackend backend = agent.getTeamBackend();
        var member = backend.getDb().member.getMember("dev-1", "predefined-desc");
        assertThat(member).isNotNull();
        // Member is registered in database
        assertThat(member.getMemberName()).isEqualTo("dev-1");
    }

    // --- Persistent team ---

    @Test
    void persistentTeam_lifecycleIsCreatedBeforeStart() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("persistent-team")
                .lifecycle("persistent")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build()))
                .build());

        // After build, lifecycle is CREATED; transitions to RUNNING on start
        assertThat(agent.getContext().getLifecycle()).isEqualTo(com.openjiuwen.agentteams.schema.team.TeamLifecycle.CREATED);
    }

    @Test
    void temporaryTeam_defaultLifecycle() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("temp-team")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build()))
                .build();
        assertThat(spec.getLifecycle()).isEqualTo("temporary");
    }

    @Test
    void persistentTeam_memberReadyToReadyIsValid() {
        // In persistent mode, READY→READY self-transition is valid
        assertThat(MemberStatus.READY.canTransitionTo(MemberStatus.READY)).isTrue();
    }

    // --- Human agent role ---

    @Test
    void humanAgentMember_rolePersistedInDatabase() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("human-agent-team")
                .humanAgentEnabled(true)
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("human_pm").role(TeamRole.HUMAN_AGENT)
                                .description("Human PM").build()))
                .build());

        TeamBackend backend = agent.getTeamBackend();
        assertThat(backend.isHumanAgent("human_pm")).isTrue();
        assertThat(backend.isHumanAgent("leader1")).isFalse();
    }

    @Test
    void defaultRoleIsMember() {
        TeamMemberSpec spec = TeamMemberSpec.builder().name("dev-1").build();
        assertThat(spec.getRole()).isEqualTo(TeamRole.MEMBER);
    }
}
