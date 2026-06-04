/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal runtime context for a team agent member.
 *
 * <p>Mirrors Python's {@code TeamRuntimeContext} in
 * {@code openjiuwen.agent_teams.schema.team}.
 */
public class TeamRuntimeContext {

    private TeamRole role = TeamRole.LEADER;
    private String memberName;
    private String persona = "";
    private TeamSpec teamSpec;
    private MessagerTransportConfig messagerConfig;
    private DatabaseConfig dbConfig;
    private TeamModelConfig memberModel;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public TeamRole getRole() {
        return role;
    }

    public void setRole(TeamRole role) {
        this.role = role;
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
        this.persona = persona;
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

    public DatabaseConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public TeamModelConfig getMemberModel() {
        return memberModel;
    }

    public void setMemberModel(TeamModelConfig memberModel) {
        this.memberModel = memberModel;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }
}
