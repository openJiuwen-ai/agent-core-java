// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for all team event messages.
 * 
 * All events include team_name for routing and tracking purposes.
 * member_name is optional — present on member-scoped events.
 * 
 * Mirrors Python's agent_teams.schema.events.BaseEventMessage
 * 
 * @since 0.1.12
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "event_type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TeamCreatedEvent.class, name = TeamEvent.CREATED),
    @JsonSubTypes.Type(value = TeamCleanedEvent.class, name = TeamEvent.CLEANED),
    @JsonSubTypes.Type(value = TeamStandbyEvent.class, name = TeamEvent.STANDBY),
    @JsonSubTypes.Type(value = MemberSpawnedEvent.class, name = TeamEvent.MEMBER_SPAWNED),
    @JsonSubTypes.Type(value = MemberRestartedEvent.class, name = TeamEvent.MEMBER_RESTARTED),
    @JsonSubTypes.Type(value = MemberStatusChangedEvent.class, name = TeamEvent.MEMBER_STATUS_CHANGED),
    @JsonSubTypes.Type(value = MemberExecutionChangedEvent.class, name = TeamEvent.MEMBER_EXECUTION_CHANGED),
    @JsonSubTypes.Type(value = MemberShutdownEvent.class, name = TeamEvent.MEMBER_SHUTDOWN),
    @JsonSubTypes.Type(value = MemberCanceledEvent.class, name = TeamEvent.MEMBER_CANCELED)
})
public abstract class BaseEventMessage {
    
    /** Team identifier for event routing */
    protected String teamName;
    
    /** Member identifier, present on member-scoped events */
    protected String memberName;
    
    protected BaseEventMessage() {
    }
    
    protected BaseEventMessage(String teamName, String memberName) {
        this.teamName = teamName;
        this.memberName = memberName;
    }
    
    public String getTeamName() {
        return teamName;
    }
    
    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }
    
    public String getMemberName() {
        return memberName;
    }
    
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }
}