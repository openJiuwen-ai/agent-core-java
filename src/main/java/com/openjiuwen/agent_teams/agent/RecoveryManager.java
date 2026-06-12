/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ModelAllocator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Fault tolerance and team recovery coordinator.
 *
 * <p>Mirrors Python's {@code RecoveryManager} in
 * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
 */
public class RecoveryManager {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final AgentConfigurator configurator;
    private final SpawnManagerPort spawnManager;

    public RecoveryManager(AgentConfigurator configurator, SpawnManagerPort spawnManager) {
        this.configurator = configurator;
        this.spawnManager = spawnManager;
    }

    public CompletionStage<List<String>> recoverTeam() {
        MemberRegistry registry = memberRegistry();
        ConfiguredTeamBackend teamBackend = configurator.getTeamBackend();
        if (teamBackend == null || registry == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        String selfMemberName = configurator.getMemberName();
        TEAM_LOGGER.info("[%s] recovering team", selfMemberName == null ? "?" : selfMemberName);
        return registry.listMembers().thenCompose(members ->
                recoverMembers(members, 0, new ArrayList<>(), selfMemberName, configurator.getTeamName(), registry));
    }

    public void persistLeaderConfig(TeamRuntimeMetadata.SessionStateAccess session) {
        AgentConfigurator.TeamAgentSpec spec = configurator.getSpec();
        AgentConfigurator.TeamRuntimeContext ctx = configurator.getCtx();
        String teamName = configurator.getTeamName();
        if (spec == null || ctx == null || teamName == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spec", AgentConfigurator.SpawnPayloadBuilder.dumpTeamAgentSpec(spec));
        payload.put("context", AgentConfigurator.SpawnPayloadBuilder.dumpRuntimeContext(ctx));
        String dbState = TeamRuntimeMetadata.readTeamDbState(session, teamName);
        payload.put(
                TeamRuntimeMetadata.TEAM_DB_STATE_KEY,
                dbState == null ? TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE : dbState
        );
        ModelAllocator allocator = configurator.getModelAllocator();
        if (allocator instanceof StatefulAllocator statefulAllocator) {
            payload.put("model_allocator_state", statefulAllocator.stateDict());
        }
        TeamRuntimeMetadata.writeTeamNamespace(session, teamName, payload);
    }

    public CompletionStage<Boolean> markTeammateRestartingForSessionSwitch(
            String memberName,
            MemberStatus currentStatus
    ) {
        MemberRegistry registry = memberRegistry();
        if (registry == null) {
            return CompletableFuture.completedFuture(false);
        }

        String teamName = configurator.getTeamName();
        if (teamName == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (currentStatus == MemberStatus.RESTARTING) {
            return CompletableFuture.completedFuture(true);
        }

        EnumSet<MemberStatus> directlyRestartable = EnumSet.of(
                MemberStatus.PAUSED,
                MemberStatus.STOPPED,
                MemberStatus.ERROR,
                MemberStatus.SHUTDOWN
        );
        if (!directlyRestartable.contains(currentStatus)) {
            return registry.updateMemberStatus(memberName, teamName, MemberStatus.ERROR.value())
                    .thenCompose(updated -> {
                        if (!Boolean.TRUE.equals(updated)) {
                            TEAM_LOGGER.warning(
                                    "Failed to move teammate %s from %s to ERROR before session rebind",
                                    memberName,
                                    currentStatus.value()
                            );
                            return CompletableFuture.completedFuture(false);
                        }
                        return updateRestarting(registry, memberName, teamName);
                    });
        }
        return updateRestarting(registry, memberName, teamName);
    }

    public CompletionStage<List<RecoverableMember>> collectLiveTeammatesForSessionSwitch() {
        MemberRegistry registry = memberRegistry();
        ConfiguredTeamBackend teamBackend = configurator.getTeamBackend();
        if (configurator.getRole() != TeamRole.LEADER || teamBackend == null || registry == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        String leaderMemberName = configurator.getMemberName();
        Map<String, Object> spawned = spawnManager.spawnedHandles();
        return registry.listMembers().thenApply(members -> {
            List<RecoverableMember> result = new ArrayList<>();
            for (MemberRecord member : members) {
                if (member.memberName().equals(leaderMemberName) || !spawned.containsKey(member.memberName())) {
                    continue;
                }
                if (spawned.get(member.memberName()) == null) {
                    continue;
                }
                MemberStatus status = MemberStatus.fromValue(member.status());
                if (status == MemberStatus.UNSTARTED
                        || status == MemberStatus.SHUTDOWN
                        || status == MemberStatus.STOPPED) {
                    continue;
                }
                result.add(new RecoverableMember(member.memberName(), status));
            }
            return result;
        });
    }

    public CompletionStage<Void> restartForSessionSwitch(
            List<RecoverableMember> recoverableMembers,
            boolean cleanupFirst
    ) {
        return restartForSessionSwitch(recoverableMembers, 0, cleanupFirst);
    }

    public void persistAllocatorState(TeamRuntimeMetadata.SessionStateAccess teamSession) {
        ModelAllocator allocator = configurator.getModelAllocator();
        String teamName = configurator.getTeamName();
        if (teamSession == null || !(allocator instanceof StatefulAllocator statefulAllocator) || teamName == null) {
            return;
        }
        try {
            TeamRuntimeMetadata.mergeTeamNamespace(
                    teamSession,
                    teamName,
                    Map.of("model_allocator_state", statefulAllocator.stateDict())
            );
        } catch (Exception exception) {
            TEAM_LOGGER.error(
                    "[%s] failed to persist allocator state: %s",
                    configurator.getMemberName() == null ? "?" : configurator.getMemberName(),
                    exception.getMessage()
            );
        }
    }

    private CompletionStage<List<String>> recoverMembers(
            List<MemberRecord> members,
            int index,
            List<String> restarted,
            String selfMemberName,
            String teamName,
            MemberRegistry registry
    ) {
        if (index >= members.size()) {
            return CompletableFuture.completedFuture(restarted);
        }
        MemberRecord member = members.get(index);
        if (member.memberName().equals(selfMemberName)) {
            return recoverMembers(members, index + 1, restarted, selfMemberName, teamName, registry);
        }

        CompletionStage<Boolean> statusUpdated = teamName == null
                ? CompletableFuture.completedFuture(true)
                : registry.updateMemberStatus(member.memberName(), teamName, MemberStatus.RESTARTING.value());
        return statusUpdated.thenCompose(ignored -> spawnManager.restartTeammate(member.memberName()))
                .thenCompose(restartedNow -> {
                    if (Boolean.TRUE.equals(restartedNow)) {
                        restarted.add(member.memberName());
                    }
                    return recoverMembers(members, index + 1, restarted, selfMemberName, teamName, registry);
                });
    }

    private CompletionStage<Boolean> updateRestarting(MemberRegistry registry, String memberName, String teamName) {
        return registry.updateMemberStatus(memberName, teamName, MemberStatus.RESTARTING.value())
                .thenApply(updated -> {
                    if (!Boolean.TRUE.equals(updated)) {
                        TEAM_LOGGER.warning(
                                "Failed to move teammate %s into RESTARTING during session rebind",
                                memberName
                        );
                        return false;
                    }
                    return true;
                });
    }

    private CompletionStage<Void> restartForSessionSwitch(
            List<RecoverableMember> recoverableMembers,
            int index,
            boolean cleanupFirst
    ) {
        if (index >= recoverableMembers.size()) {
            return CompletableFuture.completedFuture(null);
        }
        RecoverableMember member = recoverableMembers.get(index);
        CompletionStage<Void> cleanup = cleanupFirst
                ? spawnManager.cleanupTeammate(member.memberName())
                : CompletableFuture.completedFuture(null);
        return cleanup.thenCompose(ignored ->
                markTeammateRestartingForSessionSwitch(member.memberName(), member.status()))
                .thenCompose(marked -> Boolean.TRUE.equals(marked)
                        ? spawnManager.restartTeammate(member.memberName()).thenApply(ignored -> null)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> restartForSessionSwitch(recoverableMembers, index + 1, cleanupFirst));
    }

    private MemberRegistry memberRegistry() {
        ConfiguredTeamBackend teamBackend = configurator.getTeamBackend();
        if (teamBackend == null || !(teamBackend.getMemberStore() instanceof MemberRegistry registry)) {
            return null;
        }
        return registry;
    }

    /**
     * Spawn-manager boundary used by recovery.
     *
     * <p>Mirrors Python's {@code SpawnManager} calls in
     * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
     */
    public interface SpawnManagerPort {
        CompletionStage<Boolean> restartTeammate(String memberName);

        CompletionStage<Void> cleanupTeammate(String memberName);

        Map<String, Object> spawnedHandles();
    }

    /**
     * Member registry boundary used by recovery.
     *
     * <p>Mirrors Python's team backend member access in
     * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
     */
    public interface MemberRegistry extends TeamMember.MemberStore {
        CompletionStage<List<MemberRecord>> listMembers();
    }

    /**
     * Serializable model allocator state.
     *
     * <p>Mirrors Python's {@code allocator.state_dict()} usage in
     * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
     */
    public interface StatefulAllocator {
        Map<String, Object> stateDict();
    }

    /**
     * Persisted member row view used by recovery.
     *
     * <p>Mirrors Python's member row fields read in
     * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
     */
    public record MemberRecord(String memberName, String status) {
    }

    /**
     * Member selected for session-switch recovery.
     *
     * <p>Mirrors Python's {@code tuple[str, MemberStatus]} values in
     * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
     */
    public record RecoverableMember(String memberName, MemberStatus status) {
    }
}
