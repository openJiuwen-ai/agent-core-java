/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMember;
import com.openjiuwen.core.session.AgentSessionApi;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Narrow Java port of the next Python recovery-manager/session-switch helper slice.
 * <p>
 * Scope intentionally stays small: identify live teammates that need rebinding during
 * session switches, normalize their statuses through ERROR -> RESTARTING when required,
 * and record restart activity. Full DB-backed orchestration remains out of scope.
 * </p>
 * 
 * @since 0.1.7
 */
@RequiredArgsConstructor
public class RecoveryManager {
    private final TeamBackend teamBackend;
    private final String leaderMemberName;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Boolean> spawnedHandles = new ConcurrentHashMap<>();
    private SpawnManager spawnManager;

    /**
     * setSpawnManager.
     * 
     * @param spawnManager spawnManager
     * @since 0.1.7
     */
    public void setSpawnManager(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    /**
     * recoverTeam.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> recoverTeam() {
        List<String> restarted = new ArrayList<>();
        if (spawnManager == null) {
            return restarted;
        }
        for (TeamMember member : teamBackend.listMembers()) {
            if (member == null || Objects.equals(member.getMemberName(), leaderMemberName)) {
                continue;
            }
            teamBackend.forceUpdateMemberStatus(member.getMemberName(), MemberStatus.RESTARTING);
            if (spawnManager.restartTeammate(member.getMemberName())) {
                restarted.add(member.getMemberName());
            }
        }
        return restarted;
    }

    /**
     * persistLeaderConfig.
     * 
     * @param session session
     * @param spec spec
     * @param context context
     * @param modelAllocator modelAllocator
     * @since 0.1.7
     */
    public void persistLeaderConfig(AgentSessionApi session, Object spec, TeamRuntimeContext context,
            ModelAllocator modelAllocator) {
        if (session == null || spec == null || context == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spec", spec);
        payload.put("context", context);
        payload.put("team_name", teamBackend.getTeamName());
        if (modelAllocator != null) {
            payload.put("model_allocator_state", new LinkedHashMap<>(modelAllocator.stateDict()));
        }
        session.updateState(payload);
    }

    /**
     * persistAllocatorState.
     * 
     * @param session session
     * @param modelAllocator modelAllocator
     * @since 0.1.7
     */
    public void persistAllocatorState(AgentSessionApi session, ModelAllocator modelAllocator) {
        if (session == null || modelAllocator == null) {
            return;
        }
        session.updateState(Map.of("model_allocator_state", new LinkedHashMap<>(modelAllocator.stateDict())));
    }

    /**
     * collectLiveTeammatesForSessionSwitch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<RecoverableMember> collectLiveTeammatesForSessionSwitch() {
        List<RecoverableMember> recoverable = new ArrayList<>();
        for (TeamMember member : teamBackend.listMembers()) {
            if (member == null || Objects.equals(member.getMemberName(), leaderMemberName)) {
                continue;
            }
            if (!Boolean.TRUE.equals(spawnedHandles.get(member.getMemberName()))) {
                continue;
            }
            MemberStatus status = member.getStatus() != null ? member.getStatus() : MemberStatus.READY;
            if (!status.isLiveForSessionSwitch()) {
                continue;
            }
            recoverable.add(new RecoverableMember(member.getMemberName(), status));
        }
        return recoverable;
    }

    /**
     * registerSpawnedHandle.
     * 
     * @param memberName memberName
     * @since 0.1.7
     */
    public void registerSpawnedHandle(String memberName) {
        if (memberName != null && !memberName.isBlank()) {
            spawnedHandles.put(memberName, true);
        }
    }

    /**
     * removeSpawnedHandle.
     * 
     * @param memberName memberName
     * @since 0.1.7
     */
    public void removeSpawnedHandle(String memberName) {
        if (memberName != null && !memberName.isBlank()) {
            spawnedHandles.remove(memberName);
        }
    }

    /**
     * restartForSessionSwitch.
     * 
     * @param recoverableMembers recoverableMembers
     * @param isCleanupFirst isCleanupFirst
     * @since 0.1.7
     */
    public void restartForSessionSwitch(List<RecoverableMember> recoverableMembers, boolean isCleanupFirst) {
        if (recoverableMembers == null) {
            return;
        }
        for (RecoverableMember member : recoverableMembers) {
            if (isCleanupFirst) {
                removeSpawnedHandle(member.memberName());
            }
            if (!markTeammateRestartingForSessionSwitch(member.memberName(), member.status())) {
                continue;
            }
            registerSpawnedHandle(member.memberName());
        }
    }

    boolean markTeammateRestartingForSessionSwitch(String memberName, MemberStatus currentStatus) {
        if (memberName == null || memberName.isBlank()) {
            return false;
        }
        MemberStatus normalized = currentStatus != null ? currentStatus : MemberStatus.READY;
        if (normalized == MemberStatus.RESTARTING) {
            return teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.RESTARTING);
        }
        if (normalized != MemberStatus.ERROR && normalized != MemberStatus.SHUTDOWN) {
            if (!teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.ERROR)) {
                return false;
            }
        }
        return teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.RESTARTING);
    }

    /**
     * Public record RecoverableMember used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record RecoverableMember(String memberName, MemberStatus status) {
    }
}
