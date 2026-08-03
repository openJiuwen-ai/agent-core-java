/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;

/**
 * Declarative input for pre-defining a team member.
 *
 * <p>Mirrors Python's {@code TeamMemberSpec} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "role_type",
        visible = true,
        defaultImpl = TeamMemberSpec.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BridgeMemberSpec.class, name = "bridge_agent")
})
public class TeamMemberSpec {

    @JsonProperty("member_name")
    private String memberName;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("role_type")
    private TeamRole roleType = TeamRole.TEAMMATE;

    private String persona = "";

    @JsonProperty("prompt_hint")
    private String promptHint;

    @JsonProperty("model_name")
    private String modelName;

    public TeamMemberSpec() {
    }

    public TeamMemberSpec(String memberName, String displayName, TeamRole roleType, String persona) {
        this.memberName = memberName;
        this.displayName = displayName;
        setRoleType(roleType);
        this.persona = persona == null ? "" : persona;
    }

    public AgentConfigurator.TeamMemberSpec toConfiguratorSpec() {
        AgentConfigurator.TeamMemberSpec spec = new AgentConfigurator.TeamMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(displayName);
        spec.setRoleType(roleType == null ? null : roleType.toConfiguratorRole());
        spec.setPersona(persona);
        spec.setPromptHint(promptHint);
        spec.setModelName(modelName);
        return spec;
    }

    public static TeamMemberSpec fromConfiguratorSpec(AgentConfigurator.TeamMemberSpec source) {
        if (source == null) {
            return new TeamMemberSpec();
        }
        TeamRole role = TeamRole.fromConfiguratorRole(source.getRoleType());
        TeamMemberSpec spec = role == TeamRole.BRIDGE_AGENT ? new BridgeMemberSpec() : new TeamMemberSpec();
        spec.setMemberName(source.getMemberName());
        spec.setDisplayName(source.getDisplayName());
        spec.setRoleType(role);
        spec.setPersona(source.getPersona());
        spec.setPromptHint(source.getPromptHint());
        spec.setModelName(source.getModelName());
        return spec;
    }

    protected TeamRole defaultRoleType() {
        return TeamRole.TEAMMATE;
    }

    protected boolean isRoleAllowed(TeamRole role) {
        return role != TeamRole.BRIDGE_AGENT;
    }

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
        TeamRole resolved = roleType == null ? defaultRoleType() : roleType;
        if (!isRoleAllowed(resolved)) {
            throw new IllegalArgumentException("TeamMemberSpec.role_type does not accept bridge_agent");
        }
        this.roleType = resolved;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona == null ? "" : persona;
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
