/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

/**
 * Minimal declarative team member specification.
 *
 * <p>Mirrors Python's {@code TeamMemberSpec} in
 * {@code openjiuwen.agent_teams.schema.team}.
 */
public class TeamMemberSpec {

    private String memberName;
    private String displayName;
    private TeamRole roleType = TeamRole.TEAMMATE;
    private String persona;
    private String promptHint;
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

    public TeamRole getRoleType() {
        return roleType;
    }

    public void setRoleType(TeamRole roleType) {
        this.roleType = roleType;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getPromptHint() {
        return promptHint;
    }

    public void setPromptHint(String promptHint) {
        this.promptHint = promptHint;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
