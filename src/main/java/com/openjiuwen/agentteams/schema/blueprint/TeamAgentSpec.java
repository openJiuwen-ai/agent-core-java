/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.blueprint;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.memory.team.TeamMemoryConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Public class TeamAgentSpec used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamAgentSpec {
    private String name;
    @Builder.Default
    private String description = "";
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<TeamMemberSpec> members = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<ModelPoolEntry> modelPool = new ArrayList<>();
    @Builder.Default
    private String modelPoolStrategy = "round_robin";
    @Builder.Default
    private boolean isHumanAgentEnabled = false;
    @Builder.Default
    private String lifecycle = "temporary";
    @Builder.Default
    private String teammateMode = "build_mode";
    @Builder.Default
    private String spawnMode = "process";
    @Builder.Default
    private String transport = null;
    @Builder.Default
    private String storage = "sqlite";
    @Builder.Default
    private String connectionString = null;
    @Builder.Default
    private String teamMode = null;
    @Builder.Default
    private boolean enableHitt = false;
    @Builder.Default
    private boolean exposeHumanAgentsToTeammates = false;
    private String language;
    private TeamMemoryConfig memory;

    /**
     * TeamAgentSpecBuilder.
     * 
     * @since 0.1.7
     */
    public static class TeamAgentSpecBuilder {
        /**
         * humanAgentEnabled.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public TeamAgentSpecBuilder humanAgentEnabled(boolean value) {
            return this.isHumanAgentEnabled(value);
        }
    }

    /**
     * build.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamAgentSpec build() {
        this.members = new ArrayList<>(members);
        this.modelPool = new ArrayList<>(modelPool);
        defaultTransportForSpawnMode();
        validate();
        validateHittConsistency();
        ensureLeader();
        return this;
    }

    /**
     * defaultTransportForSpawnMode.
     * 
     * @since 0.1.7
     */
    public void defaultTransportForSpawnMode() {
        if (transport == null && "inprocess".equals(spawnMode)) {
            transport = "inprocess";
        }
    }

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        Set<String> seen = new LinkedHashSet<>();
        for (TeamMemberSpec member : members) {
            if (member.getName() == null || member.getName().isBlank()) {
                throw new IllegalArgumentException("Team member name cannot be blank");
            }
            if (!seen.add(member.getName())) {
                throw new IllegalArgumentException("Duplicate team member name: " + member.getName());
            }
            if (member.getRole() == TeamRole.LEADER) {
                if (TeamConstants.USER_PSEUDO_MEMBER_NAME.equals(member.getName())
                        || TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(member.getName())) {
                    throw new IllegalArgumentException("Reserved team member name is not allowed: " + member.getName());
                }
                continue;
            }
            if (TeamConstants.RESERVED_MEMBER_NAMES.contains(member.getName())) {
                if (member.getRole() == TeamRole.HUMAN_AGENT
                        && TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(member.getName()) && isHumanAgentEnabled) {
                    continue;
                }
                throw new IllegalArgumentException("Reserved team member name is not allowed: " + member.getName());
            }
            if (member.getRole() == TeamRole.HUMAN_AGENT && !isHumanAgentEnabled) {
                throw new IllegalArgumentException("human_agent member requires isHumanAgentEnabled=true");
            }
        }
    }

    /**
     * validateHittConsistency.
     * 
     * @since 0.1.7
     */
    public void validateHittConsistency() {
        if (enableHitt) {
            return;
        }
        boolean hasHumanAgent = members.stream().anyMatch(member -> member.getRole() == TeamRole.HUMAN_AGENT);
        if (hasHumanAgent) {
            throw new IllegalArgumentException("predefined_members contains HUMAN_AGENT role(s) but enableHitt=false; "
                    + "set enableHitt=true or remove the human member(s)");
        }
    }

    /**
     * ensureLeader.
     * 
     * @since 0.1.7
     */
    public void ensureLeader() {
        boolean hasLeader = members.stream().anyMatch(member -> member.getRole() == TeamRole.LEADER);
        if (!hasLeader) {
            members.add(0, TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER)
                    .description("Default team isLeader").build());
        }
    }
}
