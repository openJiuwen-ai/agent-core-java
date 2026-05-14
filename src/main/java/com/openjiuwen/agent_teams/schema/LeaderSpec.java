/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

/**
 * Minimal leader identity specification.
 *
 * <p>Mirrors Python's {@code LeaderSpec} in
 * {@code openjiuwen.agent_teams.schema.blueprint}.
 */
public class LeaderSpec {

    private String memberName = "team_leader";
    private String displayName = "Team Leader";
    private String persona = "Lead the team and coordinate task execution.";
    private String modelName;

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
