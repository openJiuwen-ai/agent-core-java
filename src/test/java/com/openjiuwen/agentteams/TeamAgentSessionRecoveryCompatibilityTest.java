package com.openjiuwen.agentteams;

import com.openjiuwen.agentteams.agent.RecoveryManager;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("agent-teams-recovery-slice")
class TeamAgentSessionRecoveryCompatibilityTest {

    @Test
    void resumePersistentTeamShouldSetSessionIdWithoutChangingTeamId() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("research-team")
                .members(List.of(TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).build()))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        TeamFactory.resumePersistentTeam(agent, "team-session-001");

        assertThat(agent.getContext().getTeamId()).isEqualTo("research-team");
        assertThat(agent.getContext().getSessionId()).isEqualTo("team-session-001");
        assertThat(agent.getContext().getMetadata()).containsEntry("session_id", "team-session-001");
        assertThat(agent.getContext().getMetadata()).containsEntry("recoverable_member_count", 0);
        assertThat(agent.getContext().getMetadata()).containsEntry("session_switch_cleanup", true);
    }

    @Test
    void resumePersistentTeamShouldRebindOnlyLiveTeammates() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("persistent-team")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-busy").role(TeamRole.MEMBER).build(),
                        TeamMemberSpec.builder().name("worker-ready").role(TeamRole.MEMBER).build(),
                        TeamMemberSpec.builder().name("worker-idle-no-handle").role(TeamRole.MEMBER).build(),
                        TeamMemberSpec.builder().name("worker-shutdown").role(TeamRole.MEMBER).build()
                ))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.getTeamBackend().updateMemberStatus("worker-busy", MemberStatus.READY);
        agent.getTeamBackend().updateMemberStatus("worker-busy", MemberStatus.BUSY);
        agent.getTeamBackend().updateMemberStatus("worker-ready", MemberStatus.READY);
        agent.getTeamBackend().updateMemberStatus("worker-idle-no-handle", MemberStatus.READY);
        agent.getTeamBackend().updateMemberStatus("worker-shutdown", MemberStatus.SHUTDOWN);
        agent.getRecoveryManager().registerSpawnedHandle("worker-busy");
        agent.getRecoveryManager().registerSpawnedHandle("worker-ready");

        TeamFactory.resumePersistentTeam(agent, "team-session-010");

        assertThat(agent.getContext().getSessionId()).isEqualTo("team-session-010");
        assertThat(agent.getContext().getMetadata()).containsEntry("recoverable_member_count", 2);
        assertThat(agent.getContext().getMetadata()).containsEntry("session_switch_cleanup", true);
        assertThat(agent.getTeamBackend().getMember("worker-busy").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getTeamBackend().getMember("worker-ready").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getTeamBackend().getMember("worker-idle-no-handle").getStatus()).isEqualTo(MemberStatus.READY);
        assertThat(agent.getTeamBackend().getMember("worker-shutdown").getStatus()).isEqualTo(MemberStatus.SHUTDOWN);
        assertThat(agent.getTeamBackend().getDb().member.getMember("worker-busy", "persistent-team").getStatus())
                .isEqualTo("restarting");
    }

    @Test
    void recoverForExistingSessionShouldRebindWithoutCleanupFlag() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("persistent-team")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-ready").role(TeamRole.MEMBER).build(),
                        TeamMemberSpec.builder().name("worker-restarting").role(TeamRole.MEMBER).build()
                ))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.getTeamBackend().updateMemberStatus("worker-ready", MemberStatus.READY);
        agent.getTeamBackend().forceUpdateMemberStatus("worker-restarting", MemberStatus.RESTARTING);
        agent.getRecoveryManager().registerSpawnedHandle("worker-ready");
        agent.getRecoveryManager().registerSpawnedHandle("worker-restarting");

        agent.recoverForExistingSession("team-session-011");

        assertThat(agent.getContext().getSessionId()).isEqualTo("team-session-011");
        assertThat(agent.getContext().getMetadata()).containsEntry("recoverable_member_count", 2);
        assertThat(agent.getContext().getMetadata()).containsEntry("session_switch_cleanup", false);
        assertThat(agent.getTeamBackend().getMember("worker-ready").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getTeamBackend().getMember("worker-restarting").getStatus()).isEqualTo(MemberStatus.RESTARTING);
    }

    @Test
    void recoveryManagerShouldCollectOnlyLiveNonLeaderNonShutdownTeammates() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("persistent-team")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-ready").role(TeamRole.MEMBER).build(),
                        TeamMemberSpec.builder().name("worker-unstarted").role(TeamRole.MEMBER).build(),
                        TeamMemberSpec.builder().name("worker-shutdown").role(TeamRole.MEMBER).build()
                ))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.getTeamBackend().updateMemberStatus("worker-ready", MemberStatus.READY);
        agent.getTeamBackend().updateMemberStatus("worker-unstarted", MemberStatus.UNSTARTED);
        agent.getTeamBackend().updateMemberStatus("worker-shutdown", MemberStatus.SHUTDOWN);
        agent.getRecoveryManager().registerSpawnedHandle("worker-ready");
        agent.getRecoveryManager().registerSpawnedHandle("worker-unstarted");
        agent.getRecoveryManager().registerSpawnedHandle("worker-shutdown");

        List<RecoveryManager.RecoverableMember> recoverable = agent.getRecoveryManager().collectLiveTeammatesForSessionSwitch();

        assertThat(recoverable)
                .extracting(RecoveryManager.RecoverableMember::memberName, RecoveryManager.RecoverableMember::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("worker-ready", MemberStatus.READY));
    }

    @Test
    void recoverTeamShouldMarkDbMembersRestartingAndRestartNonLeaderMembers() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("db-recovery-team")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-ready").role(TeamRole.MEMBER).description("Ready worker").build(),
                        TeamMemberSpec.builder().name("worker-error").role(TeamRole.MEMBER).description("Error worker").build()
                ))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.resumeForNewSession("team-session-recover");
        agent.getTeamBackend().updateMemberStatus("worker-ready", MemberStatus.READY);
        agent.getTeamBackend().updateMemberStatus("worker-error", MemberStatus.ERROR);

        List<String> restarted = agent.recoverTeam();

        assertThat(restarted).containsExactly("worker-ready", "worker-error");
        assertThat(agent.getTeamBackend().getMember(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).getStatus())
                .isEqualTo(MemberStatus.READY);
        assertThat(agent.getTeamBackend().getMember("worker-ready").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getTeamBackend().getMember("worker-error").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getTeamBackend().getDb().member.getMember("worker-ready", "db-recovery-team").getStatus())
                .isEqualTo("restarting");
        assertThat(agent.getContext().getMetadata()).containsEntry("recovered_members", List.of("worker-ready", "worker-error"));
        assertThat(agent.getSpawnManager().getSpawnedHandles()).containsKeys("worker-ready", "worker-error");

        agent.getSpawnManager().shutdownAllHandles();
    }

    @Test
    void recoverAgentTeamShouldRestoreSessionIdLeaderInboxAndMessages() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("snapshot-team")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build()
                ))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.dispatchTask("Please plan the recovery slice.");
        agent.resumeForNewSession("team-session-002");

        Map<String, Object> snapshot = agent.snapshot();
        TeamAgent recovered = TeamFactory.recoverAgentTeam(snapshot);

        assertThat(recovered.getContext().getSessionId()).isEqualTo("team-session-002");
        assertThat(recovered.getContext().getMetadata()).containsEntry("session_id", "team-session-002");
        assertThat(recovered.getLeaderInbox()).containsExactly("Please plan the recovery slice.");
        assertThat(recovered.getMessageManager().listAllMessages()).isEmpty();
    }

    @Test
    void recoveryManagerShouldPersistLeaderConfigAndAllocatorStateToSessionLikePython() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("session-state-team")
                .modelPool(List.of(
                        ModelPoolEntry.builder().modelId("model-1").modelName("glm").apiBaseUrl("http://one").build(),
                        ModelPoolEntry.builder().modelId("model-2").modelName("glm").apiBaseUrl("http://two").build()
                ))
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build()
                ))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.resumeForNewSession("session-state-001");
        agent.allocateModel(null);
        agent.persistLeaderConfigToSession();

        AgentSessionApi session = agent.getAgentSession();
        assertThat(session.getState("spec")).isSameAs(agent.getSpec());
        assertThat(session.getState("context")).isSameAs(agent.getContext());
        assertThat(session.getState("team_name")).isEqualTo("session-state-team");
        assertThat(session.getState("model_allocator_state")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> allocatorState = (Map<String, Object>) session.getState("model_allocator_state");
        assertThat(allocatorState)
                .isEqualTo(agent.getContext().getMetadata().get("model_allocator_state"))
                .containsKey("pool_digest");
        assertThat(allocatorState.get("index")).isInstanceOf(Number.class);
    }
}
