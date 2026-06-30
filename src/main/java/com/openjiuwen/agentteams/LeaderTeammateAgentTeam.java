/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LeaderTeammateAgentTeam {

    public static final String TRANSPORT_INPROCESS = "inprocess";
    public static final String TRANSPORT_PYZMQ = "pyzmq";

    public static final String STORAGE_SQLITE = "sqlite";
    public static final String STORAGE_MEMORY = "memory";
    public static final String STORAGE_POSTGRESQL = "postgresql";
    public static final String STORAGE_MYSQL = "mysql";

    public static final String LIFECYCLE_TEMPORARY = "temporary";
    public static final String LIFECYCLE_PERSISTENT = "persistent";

    public static final String TEAMMATE_MODE_BUILD = "build_mode";
    public static final String TEAMMATE_MODE_PLAN = "plan_mode";

    public static final String SPAWN_MODE_INPROCESS = "inprocess";
    public static final String SPAWN_MODE_PROCESS = "process";

    public static final String TEAM_MODE_DEFAULT = "default";
    public static final String TEAM_MODE_PREDEFINED = "predefined";
    public static final String TEAM_MODE_HYBRID = "hybrid";

    private final TeamAgentSpec spec;
    private TeamAgent agent;

    private LeaderTeammateAgentTeam(TeamAgentSpec spec) {
        this.spec = spec;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LeaderTeammateAgentTeam fromSpec(TeamAgentSpec spec) {
        return new LeaderTeammateAgentTeam(spec);
    }

    public LeaderTeammateAgentTeam build() {
        this.agent = TeamFactory.createAgentTeam(spec);
        return this;
    }

    public TeamAgent agent() {
        return agent;
    }

    public TeamAgentSpec spec() {
        return spec;
    }

    public String teamName() {
        return spec.getName();
    }

    public String lifecycle() {
        return spec.getLifecycle();
    }

    public String spawnMode() {
        return spec.getSpawnMode();
    }

    public String teammateMode() {
        return spec.getTeammateMode();
    }

    public String transport() {
        return spec.getTransport();
    }

    public String storage() {
        return spec.getStorage();
    }

    public String teamMode() {
        return spec.getTeamMode();
    }

    public boolean isHittEnabled() {
        return spec.isEnableHitt();
    }

    public List<TeamMemberSpec> members() {
        return spec.getMembers();
    }

    public boolean hasLeader() {
        return spec.getMembers().stream()
                .anyMatch(member -> member.getRole() == TeamRole.LEADER);
    }

    public List<TeamMemberSpec> teammates() {
        return spec.getMembers().stream()
                .filter(member -> member.getRole() == TeamRole.MEMBER)
                .toList();
    }

    public List<TeamMemberSpec> humanAgents() {
        return spec.getMembers().stream()
                .filter(member -> member.getRole() == TeamRole.HUMAN_AGENT)
                .toList();
    }

    public void interact(String message) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.interact(message);
    }

    public void deliverInput(String content) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.deliverInput(content);
    }

    public String broadcast(String content) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.broadcast(content);
    }

    public Map<String, Object> dispatchTask(String query) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.dispatchTask(query);
    }

    public LeaderTeammateAgentTeam resumeForNewSession(String sessionId) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.resumeForNewSession(sessionId);
        return this;
    }

    public LeaderTeammateAgentTeam recoverForExistingSession(String sessionId) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.recoverForExistingSession(sessionId);
        return this;
    }

    public List<String> recoverTeam() {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.recoverTeam();
    }

    public Map<String, Object> snapshot() {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.snapshot();
    }

    public void destroyTeam() {
        destroyTeam(true);
    }

    public void destroyTeam(boolean isForceEnabled) {
        if (agent != null) {
            agent.destroyTeam(isForceEnabled);
        }
    }

    public static class Builder {
        private String teamName = "agent_team";
        private String description = "";
        private String lifecycle = LIFECYCLE_TEMPORARY;
        private String teammateMode = TEAMMATE_MODE_BUILD;
        private String spawnMode = SPAWN_MODE_INPROCESS;
        private String transport;
        private String storage = STORAGE_SQLITE;
        private String connectionString;
        private String teamMode;
        private String language;
        private String leaderMemberName = "team_leader";
        private String leaderDisplayName = "Team Leader";
        private String leaderPersona;
        private final List<TeamMemberSpec> predefinedMembers = new ArrayList<>();
        private boolean isHumanAgentEnabled;
        private boolean isExposeHumanAgentsToTeammates;

        public Builder teamName(String name) {
            this.teamName = name;
            return this;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder lifecycle(String lc) {
            this.lifecycle = lc;
            return this;
        }

        public Builder teammateMode(String mode) {
            this.teammateMode = mode;
            return this;
        }

        public Builder spawnMode(String mode) {
            this.spawnMode = mode;
            return this;
        }

        public Builder transport(String transport) {
            this.transport = transport;
            return this;
        }

        public Builder storage(String storage) {
            this.storage = storage;
            return this;
        }

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder teamMode(String teamMode) {
            this.teamMode = teamMode;
            return this;
        }

        public Builder language(String lang) {
            this.language = lang;
            return this;
        }

        public Builder leaderMemberName(String name) {
            this.leaderMemberName = name;
            return this;
        }

        public Builder leaderDisplayName(String name) {
            this.leaderDisplayName = name;
            return this;
        }

        public Builder leaderPersona(String persona) {
            this.leaderPersona = persona;
            return this;
        }

        public Builder addPredefinedMember(TeamMemberSpec member) {
            this.predefinedMembers.add(member);
            return this;
        }

        public Builder humanAgentEnabled(boolean enabled) {
            this.isHumanAgentEnabled = enabled;
            return this;
        }

        public Builder exposeHumanAgentsToTeammates(boolean enabled) {
            this.isExposeHumanAgentsToTeammates = enabled;
            return this;
        }

        public LeaderTeammateAgentTeam build() {
            List<TeamMemberSpec> members = new ArrayList<>();

            TeamMemberSpec leaderSpec = TeamMemberSpec.builder()
                    .name(leaderMemberName)
                    .role(TeamRole.LEADER)
                    .description(leaderPersona != null ? leaderPersona : leaderDisplayName)
                    .build();
            members.add(leaderSpec);

            members.addAll(predefinedMembers);

            String resolvedTeamMode = teamMode;
            if (resolvedTeamMode == null) {
                boolean hasNonHumanPredefined = predefinedMembers.stream()
                        .anyMatch(m -> m.getRole() != TeamRole.HUMAN_AGENT);
                resolvedTeamMode = hasNonHumanPredefined ? TEAM_MODE_HYBRID : TEAM_MODE_DEFAULT;
            }

            TeamAgentSpec spec = TeamAgentSpec.builder()
                    .name(teamName)
                    .description(description)
                    .lifecycle(lifecycle)
                    .teammateMode(teammateMode)
                    .spawnMode(spawnMode)
                    .transport(transport)
                    .storage(storage)
                    .connectionString(connectionString)
                    .teamMode(resolvedTeamMode)
                    .language(language)
                    .members(members)
                    .humanAgentEnabled(isHumanAgentEnabled)
                    .exposeHumanAgentsToTeammates(isExposeHumanAgentsToTeammates)
                    .build();

            return new LeaderTeammateAgentTeam(spec);
        }
    }
}
