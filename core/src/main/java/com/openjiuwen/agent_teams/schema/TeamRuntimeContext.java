/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight runtime context for a single team member.
 *
 * <p>Mirrors Python's {@code TeamRuntimeContext} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamRuntimeContext {

    private TeamRole role = TeamRole.LEADER;

    @JsonProperty("member_name")
    private String memberName;

    private String persona = "";

    @JsonProperty("team_spec")
    private TeamSpec teamSpec;

    @JsonProperty("messager_config")
    private MessagerTransportConfig messagerConfig;

    @JsonProperty("db_config")
    private Map<String, Object> dbConfig = databaseConfigMap(new DatabaseConfig());

    @JsonProperty("member_model")
    private TeamModelConfig memberModel;

    @JsonProperty("cli_agent")
    private String cliAgent;

    public AgentConfigurator.TeamRuntimeContext toConfiguratorContext() {
        AgentConfigurator.TeamRuntimeContext context = new AgentConfigurator.TeamRuntimeContext();
        context.setRole(role == null ? null : role.toConfiguratorRole());
        context.setMemberName(memberName);
        context.setPersona(persona);
        context.setTeamSpec(teamSpec == null ? null : teamSpec.toConfiguratorSpec());
        context.setMessagerConfig(messagerConfig);
        context.setDbConfig(dbConfig);
        context.setMemberModel(memberModel);
        context.setCliAgent(cliAgent);
        return context;
    }

    public static TeamRuntimeContext fromConfiguratorContext(AgentConfigurator.TeamRuntimeContext source) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        if (source == null) {
            return context;
        }
        context.setRole(TeamRole.fromConfiguratorRole(source.getRole()));
        context.setMemberName(source.getMemberName());
        context.setPersona(source.getPersona());
        context.setTeamSpec(TeamSpec.fromConfiguratorSpec(source.getTeamSpec()));
        context.setMessagerConfig(source.getMessagerConfig());
        context.setDbConfig(source.getDbConfig());
        if (source.getMemberModel() instanceof TeamModelConfig modelConfig) {
            context.setMemberModel(modelConfig);
        }
        context.setCliAgent(source.getCliAgent());
        return context;
    }

    public static Map<String, Object> databaseConfigMap(DatabaseConfig config) {
        Map<String, Object> values = new LinkedHashMap<>();
        DatabaseConfig source = config == null ? new DatabaseConfig() : config;
        values.put("db_type", source.getDbType() == null ? null : source.getDbType().value());
        values.put("connection_string", source.getConnectionString());
        values.put("db_timeout", source.getDbTimeout());
        values.put("db_enable_wal", source.isDbEnableWal());
        return values;
    }

    public TeamRole getRole() {
        return role;
    }

    public void setRole(TeamRole role) {
        this.role = role == null ? TeamRole.LEADER : role;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona == null ? "" : persona;
    }

    public TeamSpec getTeamSpec() {
        return teamSpec;
    }

    public void setTeamSpec(TeamSpec teamSpec) {
        this.teamSpec = teamSpec;
    }

    public MessagerTransportConfig getMessagerConfig() {
        return messagerConfig;
    }

    public void setMessagerConfig(MessagerTransportConfig messagerConfig) {
        this.messagerConfig = messagerConfig;
    }

    public Map<String, Object> getDbConfig() {
        return new LinkedHashMap<>(dbConfig);
    }

    public void setDbConfig(Map<String, Object> dbConfig) {
        this.dbConfig = dbConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dbConfig);
    }

    public void setDbConfig(DatabaseConfig dbConfig) {
        this.dbConfig = databaseConfigMap(dbConfig);
    }

    public TeamModelConfig getMemberModel() {
        return memberModel;
    }

    public void setMemberModel(TeamModelConfig memberModel) {
        this.memberModel = memberModel;
    }

    public String getCliAgent() {
        return cliAgent;
    }

    public void setCliAgent(String cliAgent) {
        this.cliAgent = cliAgent;
    }
}
