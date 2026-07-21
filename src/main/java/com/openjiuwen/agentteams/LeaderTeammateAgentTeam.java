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

/**
 * LeaderTeammateAgentTeam.
 * 
 * @since 0.1.7
 */
public class LeaderTeammateAgentTeam {
    /**
     * TRANSPORT_INPROCESS.
     * 
     * @since 0.1.7
     */
    public static final String TRANSPORT_INPROCESS = "inprocess";

    /**
     * TRANSPORT_PYZMQ.
     * 
     * @since 0.1.7
     */
    public static final String TRANSPORT_PYZMQ = "pyzmq";

    /**
     * STORAGE_SQLITE.
     * 
     * @since 0.1.7
     */
    public static final String STORAGE_SQLITE = "sqlite";

    /**
     * STORAGE_MEMORY.
     * 
     * @since 0.1.7
     */
    public static final String STORAGE_MEMORY = "memory";

    /**
     * STORAGE_POSTGRESQL.
     * 
     * @since 0.1.7
     */
    public static final String STORAGE_POSTGRESQL = "postgresql";

    /**
     * STORAGE_MYSQL.
     * 
     * @since 0.1.7
     */
    public static final String STORAGE_MYSQL = "mysql";

    /**
     * LIFECYCLE_TEMPORARY.
     * 
     * @since 0.1.7
     */
    public static final String LIFECYCLE_TEMPORARY = "temporary";

    /**
     * LIFECYCLE_PERSISTENT.
     * 
     * @since 0.1.7
     */
    public static final String LIFECYCLE_PERSISTENT = "persistent";

    /**
     * TEAMMATE_MODE_BUILD.
     * 
     * @since 0.1.7
     */
    public static final String TEAMMATE_MODE_BUILD = "build_mode";

    /**
     * TEAMMATE_MODE_PLAN.
     * 
     * @since 0.1.7
     */
    public static final String TEAMMATE_MODE_PLAN = "plan_mode";

    /**
     * SPAWN_MODE_INPROCESS.
     * 
     * @since 0.1.7
     */
    public static final String SPAWN_MODE_INPROCESS = "inprocess";

    /**
     * SPAWN_MODE_PROCESS.
     * 
     * @since 0.1.7
     */
    public static final String SPAWN_MODE_PROCESS = "process";

    /**
     * TEAM_MODE_DEFAULT.
     * 
     * @since 0.1.7
     */
    public static final String TEAM_MODE_DEFAULT = "default";

    /**
     * TEAM_MODE_PREDEFINED.
     * 
     * @since 0.1.7
     */
    public static final String TEAM_MODE_PREDEFINED = "predefined";

    /**
     * TEAM_MODE_HYBRID.
     * 
     * @since 0.1.7
     */
    public static final String TEAM_MODE_HYBRID = "hybrid";

    private final TeamAgentSpec spec;
    private TeamAgent agent;

    /**
     * LeaderTeammateAgentTeam.
     * 
     * @param spec spec
     * @since 0.1.7
     */
    private LeaderTeammateAgentTeam(TeamAgentSpec spec) {
        this.spec = spec;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * fromSpec.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    public static LeaderTeammateAgentTeam fromSpec(TeamAgentSpec spec) {
        return new LeaderTeammateAgentTeam(spec);
    }

    /**
     * build.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LeaderTeammateAgentTeam build() {
        this.agent = TeamFactory.createAgentTeam(spec);
        return this;
    }

    /**
     * agent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamAgent agent() {
        return agent;
    }

    /**
     * spec.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamAgentSpec spec() {
        return spec;
    }

    /**
     * teamName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String teamName() {
        return spec.getName();
    }

    /**
     * lifecycle.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String lifecycle() {
        return spec.getLifecycle();
    }

    /**
     * spawnMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String spawnMode() {
        return spec.getSpawnMode();
    }

    /**
     * teammateMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String teammateMode() {
        return spec.getTeammateMode();
    }

    /**
     * transport.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String transport() {
        return spec.getTransport();
    }

    /**
     * storage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String storage() {
        return spec.getStorage();
    }

    /**
     * teamMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String teamMode() {
        return spec.getTeamMode();
    }

    /**
     * isHittEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isHittEnabled() {
        return spec.isHittEnabled();
    }

    /**
     * members.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMemberSpec> members() {
        return spec.getMembers();
    }

    /**
     * hasLeader.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasLeader() {
        return spec.getMembers().stream().anyMatch(member -> member.getRole() == TeamRole.LEADER);
    }

    /**
     * teammates.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMemberSpec> teammates() {
        return spec.getMembers().stream().filter(member -> member.getRole() == TeamRole.MEMBER).toList();
    }

    /**
     * humanAgents.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMemberSpec> humanAgents() {
        return spec.getMembers().stream().filter(member -> member.getRole() == TeamRole.HUMAN_AGENT).toList();
    }

    /**
     * interact.
     * 
     * @param message message
     * @since 0.1.7
     */
    public void interact(String message) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.interact(message);
    }

    /**
     * deliverInput.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void deliverInput(String content) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.deliverInput(content);
    }

    /**
     * broadcast.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public String broadcast(String content) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.broadcast(content);
    }

    /**
     * dispatchTask.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> dispatchTask(String query) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.dispatchTask(query);
    }

    /**
     * resumeForNewSession.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public LeaderTeammateAgentTeam resumeForNewSession(String sessionId) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.resumeForNewSession(sessionId);
        return this;
    }

    /**
     * recoverForExistingSession.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public LeaderTeammateAgentTeam recoverForExistingSession(String sessionId) {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        agent.recoverForExistingSession(sessionId);
        return this;
    }

    /**
     * recoverTeam.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> recoverTeam() {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.recoverTeam();
    }

    /**
     * snapshot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> snapshot() {
        if (agent == null) {
            throw new IllegalStateException("Team not built yet; call build() first");
        }
        return agent.snapshot();
    }

    /**
     * destroyTeam.
     * 
     * @since 0.1.7
     */
    public void destroyTeam() {
        destroyTeam(true);
    }

    /**
     * destroyTeam.
     * 
     * @param isForceEnabled isForceEnabled
     * @since 0.1.7
     */
    public void destroyTeam(boolean isForceEnabled) {
        if (agent != null) {
            agent.destroyTeam(isForceEnabled);
        }
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
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

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<TeamMemberSpec> predefinedMembers = new ArrayList<>();
        private boolean isHumanAgentEnabled;
        private boolean isExposeHumanAgentsToTeammates;

        /**
         * teamName.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder teamName(String name) {
            this.teamName = name;
            return this;
        }

        /**
         * description.
         * 
         * @param desc desc
         * @return the result
         * @since 0.1.7
         */
        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        /**
         * lifecycle.
         * 
         * @param lc lc
         * @return the result
         * @since 0.1.7
         */
        public Builder lifecycle(String lc) {
            this.lifecycle = lc;
            return this;
        }

        /**
         * teammateMode.
         * 
         * @param mode mode
         * @return the result
         * @since 0.1.7
         */
        public Builder teammateMode(String mode) {
            this.teammateMode = mode;
            return this;
        }

        /**
         * spawnMode.
         * 
         * @param mode mode
         * @return the result
         * @since 0.1.7
         */
        public Builder spawnMode(String mode) {
            this.spawnMode = mode;
            return this;
        }

        /**
         * transport.
         * 
         * @param transport transport
         * @return the result
         * @since 0.1.7
         */
        public Builder transport(String transport) {
            this.transport = transport;
            return this;
        }

        /**
         * storage.
         * 
         * @param storage storage
         * @return the result
         * @since 0.1.7
         */
        public Builder storage(String storage) {
            this.storage = storage;
            return this;
        }

        /**
         * connectionString.
         * 
         * @param connectionString connectionString
         * @return the result
         * @since 0.1.7
         */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        /**
         * teamMode.
         * 
         * @param teamMode teamMode
         * @return the result
         * @since 0.1.7
         */
        public Builder teamMode(String teamMode) {
            this.teamMode = teamMode;
            return this;
        }

        /**
         * language.
         * 
         * @param lang lang
         * @return the result
         * @since 0.1.7
         */
        public Builder language(String lang) {
            this.language = lang;
            return this;
        }

        /**
         * leaderMemberName.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder leaderMemberName(String name) {
            this.leaderMemberName = name;
            return this;
        }

        /**
         * leaderDisplayName.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder leaderDisplayName(String name) {
            this.leaderDisplayName = name;
            return this;
        }

        /**
         * leaderPersona.
         * 
         * @param persona persona
         * @return the result
         * @since 0.1.7
         */
        public Builder leaderPersona(String persona) {
            this.leaderPersona = persona;
            return this;
        }

        /**
         * addPredefinedMember.
         * 
         * @param member member
         * @return the result
         * @since 0.1.7
         */
        public Builder addPredefinedMember(TeamMemberSpec member) {
            this.predefinedMembers.add(member);
            return this;
        }

        /**
         * humanAgentEnabled.
         * 
         * @param enabled enabled
         * @return the result
         * @since 0.1.7
         */
        public Builder humanAgentEnabled(boolean enabled) {
            this.isHumanAgentEnabled = enabled;
            return this;
        }

        /**
         * isExposeHumanAgentsToTeammates.
         *
         * @param isEnabled enabled
         * @return the result
         * @since 0.1.7
         */
        public Builder isExposeHumanAgentsToTeammates(boolean isEnabled) {
            this.isExposeHumanAgentsToTeammates = isEnabled;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public LeaderTeammateAgentTeam build() {
            List<TeamMemberSpec> members = new ArrayList<>();

            TeamMemberSpec leaderSpec = TeamMemberSpec.builder().name(leaderMemberName).role(TeamRole.LEADER)
                    .description(leaderPersona != null ? leaderPersona : leaderDisplayName).build();
            members.add(leaderSpec);

            members.addAll(predefinedMembers);

            String resolvedTeamMode = teamMode;
            if (resolvedTeamMode == null) {
                boolean hasNonHumanPredefined =
                    predefinedMembers.stream().anyMatch(m -> m.getRole() != TeamRole.HUMAN_AGENT);
                resolvedTeamMode = hasNonHumanPredefined ? TEAM_MODE_HYBRID : TEAM_MODE_DEFAULT;
            }

            TeamAgentSpec builtSpec = TeamAgentSpec.builder().name(teamName).description(description)
                    .lifecycle(lifecycle).teammateMode(teammateMode).spawnMode(spawnMode).transport(transport)
                    .storage(storage).connectionString(connectionString).teamMode(resolvedTeamMode).language(language)
                    .members(members).humanAgentEnabled(isHumanAgentEnabled)
                    .isExposeHumanAgentsToTeammates(isExposeHumanAgentsToTeammates).build();

            return new LeaderTeammateAgentTeam(builtSpec);
        }
    }
}
