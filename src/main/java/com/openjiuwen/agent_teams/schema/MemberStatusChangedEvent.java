// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a member's status changes.
 * 
 * Mirrors Python's agent_teams.schema.events.MemberStatusChangedEvent
 * 
 * @since 0.1.12
 */
public class MemberStatusChangedEvent extends BaseEventMessage {
    
    /** Previous status */
    private String oldStatus;
    
    /** New status */
    private String newStatus;
    
    public MemberStatusChangedEvent() {
        super();
    }
    
    public MemberStatusChangedEvent(String teamName, String memberName, String oldStatus, String newStatus) {
        super(teamName, memberName);
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
    
    public String getOldStatus() {
        return oldStatus;
    }
    
    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }
    
    public String getNewStatus() {
        return newStatus;
    }
    
    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }
}