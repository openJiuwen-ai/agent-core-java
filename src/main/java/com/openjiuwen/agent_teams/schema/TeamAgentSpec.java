/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.agent.ModelPoolEntry;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.core.memory.team.TeamMemoryConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal serializable team-agent blueprint.
 *
 * <p>Mirrors Python's {@code TeamAgentSpec} in
 * {@code openjiuwen.agent_teams.schema.blueprint}.
 */
public class TeamAgentSpec {

    private Map<String, DeepAgentSpec> agents = new LinkedHashMap<>();
    private String teamName = "agent_team";
    private TeamLifecycle lifecycle = TeamLifecycle.TEMPORARY;
    private String teammateMode = "build_mode";
    private String spawnMode = "process";
    private String teamMode;
    private List<ModelPoolEntry> modelPool = new ArrayList<>();
    private String modelPoolStrategy = "round_robin";
    private LeaderSpec leader = new LeaderSpec();
    private List<TeamMemberSpec> predefinedMembers = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String language;
    private WorktreeConfig worktree;
    private TeamWorkspaceConfig workspace;
    private TeamMemoryConfig memory;
    private boolean enableHitt;

    public Map<String, DeepAgentSpec> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, DeepAgentSpec> agents) {
        this.agents = agents != null ? new LinkedHashMap<>(agents) : new LinkedHashMap<>();
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public TeamLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(TeamLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getTeammateMode() {
        return teammateMode;
    }

    public void setTeammateMode(String teammateMode) {
        this.teammateMode = teammateMode;
    }

    public String getSpawnMode() {
        return spawnMode;
    }

    public void setSpawnMode(String spawnMode) {
        this.spawnMode = spawnMode;
    }

    public String getTeamMode() {
        return teamMode;
    }

    public void setTeamMode(String teamMode) {
        this.teamMode = teamMode;
    }

    public List<ModelPoolEntry> getModelPool() {
        return new ArrayList<>(modelPool);
    }

    public void setModelPool(List<ModelPoolEntry> modelPool) {
        this.modelPool = modelPool != null ? new ArrayList<>(modelPool) : new ArrayList<>();
    }

    public String getModelPoolStrategy() {
        return modelPoolStrategy;
    }

    public void setModelPoolStrategy(String modelPoolStrategy) {
        this.modelPoolStrategy = modelPoolStrategy != null ? modelPoolStrategy : "round_robin";
    }

    public LeaderSpec getLeader() {
        return leader;
    }

    public void setLeader(LeaderSpec leader) {
        this.leader = leader;
    }

    public List<TeamMemberSpec> getPredefinedMembers() {
        return predefinedMembers;
    }

    public void setPredefinedMembers(List<TeamMemberSpec> predefinedMembers) {
        this.predefinedMembers = predefinedMembers != null ? new ArrayList<>(predefinedMembers) : new ArrayList<>();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public WorktreeConfig getWorktree() {
        return worktree;
    }

    public void setWorktree(WorktreeConfig worktree) {
        this.worktree = worktree;
    }

    public TeamWorkspaceConfig getWorkspace() {
        return workspace;
    }

    public void setWorkspace(TeamWorkspaceConfig workspace) {
        this.workspace = workspace;
    }

    public TeamMemoryConfig getMemory() {
        return memory;
    }

    public void setMemory(TeamMemoryConfig memory) {
        this.memory = memory;
    }

    public boolean isEnableHitt() {
        return enableHitt;
    }

    public void setEnableHitt(boolean enableHitt) {
        this.enableHitt = enableHitt;
    }

    public TeamAgent build() {
        validateReservedMemberNames();
        injectHumanAgentIfEnabled();
        propagateResolvedLanguage();
        return TeamAgent.fromSpec(this);
    }

    public static String resolveLanguage(String language) {
        if ("cn".equals(language) || "en".equals(language)) {
            return language;
        }
        String envLanguage = System.getenv("AGENT_PROMPT_LANGUAGE");
        if ("cn".equals(envLanguage) || "en".equals(envLanguage)) {
            return envLanguage;
        }
        return "cn";
    }

    private void validateReservedMemberNames() {
        Set<String> leaderForbiddenNames = new HashSet<>(TeamConstants.RESERVED_MEMBER_NAMES);
        leaderForbiddenNames.remove(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        if (leader != null && leaderForbiddenNames.contains(leader.getMemberName())) {
            throw new IllegalArgumentException("Leader member name is reserved by the runtime: '" + leader.getMemberName() + "'");
        }
        Set<String> seen = new HashSet<>();
        for (TeamMemberSpec memberSpec : predefinedMembers) {
            if (memberSpec == null) {
                continue;
            }
            String memberName = memberSpec.getMemberName();
            if (memberName == null || memberName.isBlank()) {
                continue;
            }
            if (TeamConstants.RESERVED_MEMBER_NAMES.contains(memberName)) {
                throw new IllegalArgumentException(
                        "Predefined member name '" + memberName + "' is reserved by the runtime"
                );
            }
            if (!seen.add(memberName)) {
                throw new IllegalArgumentException(
                        "Duplicate predefined member name: '" + memberName + "'"
                );
            }
        }
    }

    private void propagateResolvedLanguage() {
        String resolvedLanguage = resolveLanguage(language);
        for (DeepAgentSpec roleSpec : agents.values()) {
            if (roleSpec != null && roleSpec.getLanguage() == null) {
                roleSpec.setLanguage(resolvedLanguage);
            }
        }
    }

    public void injectHumanAgentIfEnabled() {
        if (!enableHitt) {
            return;
        }
        for (TeamMemberSpec memberSpec : predefinedMembers) {
            if (memberSpec != null && TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(memberSpec.getMemberName())) {
                return;
            }
        }
        TeamMemberSpec humanAgent = new TeamMemberSpec();
        humanAgent.setMemberName(TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        humanAgent.setDisplayName("Human Agent");
        humanAgent.setRoleType(TeamRole.HUMAN_AGENT);
        humanAgent.setPersona("Human collaborator in the team");
        predefinedMembers.add(humanAgent);
    }
}
