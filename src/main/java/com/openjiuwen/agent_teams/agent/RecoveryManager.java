/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.core.session.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recovery helper for persistent TeamAgent session rebinding.
 *
 * <p>Mirrors Python's {@code RecoveryManager} in
 * {@code openjiuwen.agent_teams.agent.recovery_manager}.</p>
 */
public class RecoveryManager {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    static final String LEADER_STATE_KEY = "agent_team_leader_state";
    static final String RECOVERABLE_MEMBERS_KEY = "agent_team_recoverable_members";

    private final TeamAgentSpec spec;
    private final TeamRuntimeContext runtimeContext;
    private final TeamBackend teamBackend;
    private ModelAllocator modelAllocator;

    public RecoveryManager(TeamAgentSpec spec, TeamRuntimeContext runtimeContext, TeamBackend teamBackend) {
        this.spec = spec;
        this.runtimeContext = runtimeContext;
        this.teamBackend = teamBackend;
    }

    public void setModelAllocator(ModelAllocator modelAllocator) {
        this.modelAllocator = modelAllocator;
    }

    public void persistLeaderConfig(Session session) {
        if (session == null || spec == null || runtimeContext == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("team_name", spec.getTeamName());
        payload.put("language", spec.getLanguage());
        payload.put("lifecycle", spec.getLifecycle() != null ? spec.getLifecycle().name() : null);
        payload.put("metadata", copyMap(spec.getMetadata()));
        payload.put("context", serializeRuntimeContext(runtimeContext));
        payload.put("leader", serializeLeaderSpec(spec));
        payload.put("recoverable_members", snapshotRecoverableMembers());
        if (modelAllocator != null) {
            payload.put("model_allocator_state", modelAllocator.stateDict());
        }
        session.updateState(Map.of(LEADER_STATE_KEY, payload));
    }

    public void persistAllocatorState(Session teamSession) {
        if (teamSession == null || modelAllocator == null) {
            return;
        }
        teamSession.updateState(Map.of("model_allocator_state", modelAllocator.stateDict()));
    }

    public List<RecoverableMember> collectLiveTeammatesForSessionSwitch() {
        if (teamBackend == null) {
            return List.of();
        }
        List<RecoverableMember> result = new ArrayList<>();
        for (TeamMember member : teamBackend.listMembers()) {
            if (member == null || member.getMemberName() == null) {
                continue;
            }
            if (runtimeContext != null && member.getMemberName().equals(runtimeContext.getMemberName())) {
                continue;
            }
            MemberStatus status = member.getStatus();
            if (status == null || status == MemberStatus.UNSTARTED || status == MemberStatus.SHUTDOWN) {
                continue;
            }
            TeamMemberRuntime runtime = teamBackend.getMemberRuntime(member.getMemberName());
            if (runtime == null) {
                continue;
            }
            result.add(new RecoverableMember(member.getMemberName(), status));
        }
        return result;
    }

    public void restartForSessionSwitch(List<RecoverableMember> recoverableMembers, boolean cleanupFirst) {
        if (teamBackend == null || recoverableMembers == null || recoverableMembers.isEmpty()) {
            return;
        }
        for (RecoverableMember recoverableMember : recoverableMembers) {
            if (recoverableMember == null || recoverableMember.memberName() == null) {
                continue;
            }
            if (cleanupFirst) {
                teamBackend.clearMemberRuntime(recoverableMember.memberName());
            }
            TeamMember member = teamBackend.getMember(recoverableMember.memberName());
            if (member == null) {
                continue;
            }
            member.setStatus(MemberStatus.ERROR);
            member.setStatus(MemberStatus.RESTARTING);
            teamBackend.rebindMemberSession(recoverableMember.memberName());
            teamBackend.ensureMemberRuntime(recoverableMember.memberName());
            member.setStatus(MemberStatus.READY);
        }
    }

    public void restoreLeaderConfig(Session session) {
        if (session == null || spec == null) {
            return;
        }
        Object raw = session.getState(LEADER_STATE_KEY);
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> state = OBJECT_MAPPER.convertValue(map, MAP_TYPE);
        Object savedTeamName = state.get("team_name");
        if (savedTeamName instanceof String teamName && !teamName.isBlank()) {
            spec.setTeamName(teamName);
        }
        Object savedLanguage = state.get("language");
        if (savedLanguage instanceof String language) {
            spec.setLanguage(language);
        }
        Object savedMetadata = state.get("metadata");
        if (savedMetadata instanceof Map<?, ?> metadata) {
            spec.setMetadata(OBJECT_MAPPER.convertValue(metadata, MAP_TYPE));
        }
        Object rawAllocatorState = state.get("model_allocator_state");
        if (modelAllocator != null && rawAllocatorState instanceof Map<?, ?> allocatorState) {
            modelAllocator.loadStateDict(OBJECT_MAPPER.convertValue(allocatorState, MAP_TYPE));
        }
        restoreLeaderSpec(state.get("leader"));
        restoreRuntimeContext(state.get("context"));
        restoreRecoverableMembers(state.get("recoverable_members"));
    }

    public List<Map<String, Object>> snapshotRecoverableMembers() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (RecoverableMember member : collectLiveTeammatesForSessionSwitch()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("member_name", member.memberName());
            entry.put("status", member.status() != null ? member.status().name() : null);
            snapshot.add(entry);
        }
        return snapshot;
    }

    public void restoreRecoverableMembers(Object rawRecoverableMembers) {
        if (teamBackend == null) {
            return;
        }
        if (!(rawRecoverableMembers instanceof Iterable<?> entries)) {
            return;
        }
        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map<?, ?> entry)) {
                continue;
            }
            Object nameObj = entry.get("member_name");
            if (!(nameObj instanceof String memberName) || memberName.isBlank()) {
                continue;
            }
            TeamMember member = teamBackend.getMember(memberName);
            if (member == null) {
                continue;
            }
            MemberStatus status = parseMemberStatus(entry.get("status"));
            member.setStatus(status != null ? status : MemberStatus.READY);
            teamBackend.rebindMemberSession(memberName);
        }
    }

    private Map<String, Object> serializeLeaderSpec(TeamAgentSpec teamAgentSpec) {
        Map<String, Object> leader = new LinkedHashMap<>();
        if (teamAgentSpec.getLeader() != null) {
            leader.put("member_name", teamAgentSpec.getLeader().getMemberName());
            leader.put("persona", teamAgentSpec.getLeader().getPersona());
        }
        DeepAgentSpec leaderSpec = teamAgentSpec.getAgents().get("leader");
        if (leaderSpec != null) {
            leader.put("language", leaderSpec.getLanguage());
            if (leaderSpec.getConfig() != null) {
                leader.put("system_prompt", leaderSpec.getConfig().getSystemPrompt());
            }
        }
        return leader;
    }

    private void restoreLeaderSpec(Object rawLeader) {
        if (!(rawLeader instanceof Map<?, ?> leader) || spec.getLeader() == null) {
            return;
        }
        Object memberName = leader.get("member_name");
        if (memberName instanceof String value) {
            spec.getLeader().setMemberName(value);
        }
        Object persona = leader.get("persona");
        if (persona instanceof String value) {
            spec.getLeader().setPersona(value);
        }
        DeepAgentSpec leaderSpec = spec.getAgents().get("leader");
        if (leaderSpec != null) {
            Object language = leader.get("language");
            if (language instanceof String value) {
                leaderSpec.setLanguage(value);
            }
            Object systemPrompt = leader.get("system_prompt");
            if (systemPrompt instanceof String value && leaderSpec.getConfig() != null) {
                leaderSpec.getConfig().setSystemPrompt(value);
            }
        }
    }

    private Map<String, Object> serializeRuntimeContext(TeamRuntimeContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("member_name", context.getMemberName());
        payload.put("persona", context.getPersona());
        payload.put("role", context.getRole() != null ? context.getRole().name() : null);
        payload.put("metadata", copyMap(context.getMetadata()));
        if (context.getTeamSpec() != null) {
            Map<String, Object> teamSpec = new LinkedHashMap<>();
            teamSpec.put("team_name", context.getTeamSpec().getTeamName());
            teamSpec.put("display_name", context.getTeamSpec().getDisplayName());
            teamSpec.put("leader_member_name", context.getTeamSpec().getLeaderMemberName());
            teamSpec.put("language", context.getTeamSpec().getLanguage());
            teamSpec.put("metadata", copyMap(context.getTeamSpec().getMetadata()));
            teamSpec.put("model_pool_strategy", context.getTeamSpec().getModelPoolStrategy());
            payload.put("team_spec", teamSpec);
        }
        return payload;
    }

    private void restoreRuntimeContext(Object rawContext) {
        if (!(rawContext instanceof Map<?, ?> context) || runtimeContext == null) {
            return;
        }
        Object memberName = context.get("member_name");
        if (memberName instanceof String value) {
            runtimeContext.setMemberName(value);
        }
        Object persona = context.get("persona");
        if (persona instanceof String value) {
            runtimeContext.setPersona(value);
        }
        Object metadata = context.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            runtimeContext.setMetadata(OBJECT_MAPPER.convertValue(map, MAP_TYPE));
        }
        Object rawTeamSpec = context.get("team_spec");
        if (rawTeamSpec instanceof Map<?, ?> teamSpec && runtimeContext.getTeamSpec() != null) {
            Object teamName = teamSpec.get("team_name");
            if (teamName instanceof String value) {
                runtimeContext.getTeamSpec().setTeamName(value);
            }
            Object displayName = teamSpec.get("display_name");
            if (displayName instanceof String value) {
                runtimeContext.getTeamSpec().setDisplayName(value);
            }
            Object leaderMemberName = teamSpec.get("leader_member_name");
            if (leaderMemberName instanceof String value) {
                runtimeContext.getTeamSpec().setLeaderMemberName(value);
            }
            Object language = teamSpec.get("language");
            if (language instanceof String value) {
                runtimeContext.getTeamSpec().setLanguage(value);
            }
            Object teamMetadata = teamSpec.get("metadata");
            if (teamMetadata instanceof Map<?, ?> map) {
                runtimeContext.getTeamSpec().setMetadata(OBJECT_MAPPER.convertValue(map, MAP_TYPE));
            }
            Object strategy = teamSpec.get("model_pool_strategy");
            if (strategy instanceof String value) {
                runtimeContext.getTeamSpec().setModelPoolStrategy(value);
            }
        }
    }

    private MemberStatus parseMemberStatus(Object rawStatus) {
        if (rawStatus instanceof String value && !value.isBlank()) {
            try {
                return MemberStatus.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
    }

    public record RecoverableMember(String memberName, MemberStatus status) {
    }
}
