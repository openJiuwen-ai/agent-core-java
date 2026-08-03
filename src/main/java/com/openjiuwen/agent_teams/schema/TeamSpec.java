/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.models.ModelPoolEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definition of a team and its goal.
 *
 * <p>Mirrors Python's {@code TeamSpec} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamSpec {

    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("leader_member_name")
    private String leaderMemberName;

    private String language;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @JsonProperty("model_pool")
    private List<ModelPoolEntry> modelPool = new ArrayList<>();

    @JsonProperty("model_pool_strategy")
    private String modelPoolStrategy = "round_robin";

    public TeamSpec() {
    }

    public TeamSpec(String teamName, String displayName, String leaderMemberName) {
        this.teamName = teamName;
        this.displayName = displayName;
        this.leaderMemberName = leaderMemberName;
    }

    public AgentConfigurator.TeamSpec toConfiguratorSpec() {
        AgentConfigurator.TeamSpec spec = new AgentConfigurator.TeamSpec(teamName, displayName, leaderMemberName);
        spec.setLanguage(language);
        spec.setMetadata(metadata);
        spec.setModelPool(modelPool);
        spec.setModelPoolStrategy(modelPoolStrategy);
        return spec;
    }

    public static TeamSpec fromConfiguratorSpec(AgentConfigurator.TeamSpec source) {
        TeamSpec spec = new TeamSpec();
        if (source == null) {
            return spec;
        }
        spec.setTeamName(source.getTeamName());
        spec.setDisplayName(source.getDisplayName());
        spec.setLeaderMemberName(source.getLeaderMemberName());
        spec.setLanguage(source.getLanguage());
        spec.setMetadata(source.getMetadata());
        spec.setModelPool(modelPoolEntries(source.getModelPool()));
        spec.setModelPoolStrategy(source.getModelPoolStrategy());
        return spec;
    }

    private static List<ModelPoolEntry> modelPoolEntries(List<?> source) {
        List<ModelPoolEntry> entries = new ArrayList<>();
        for (Object value : source == null ? List.of() : source) {
            if (value instanceof ModelPoolEntry entry) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLeaderMemberName() {
        return leaderMemberName;
    }

    public void setLeaderMemberName(String leaderMemberName) {
        this.leaderMemberName = leaderMemberName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public List<ModelPoolEntry> getModelPool() {
        return new ArrayList<>(modelPool);
    }

    public void setModelPool(List<ModelPoolEntry> modelPool) {
        this.modelPool = modelPool == null ? new ArrayList<>() : new ArrayList<>(modelPool);
    }

    public String getModelPoolStrategy() {
        return modelPoolStrategy;
    }

    public void setModelPoolStrategy(String modelPoolStrategy) {
        this.modelPoolStrategy = modelPoolStrategy == null ? "round_robin" : modelPoolStrategy;
    }
}
