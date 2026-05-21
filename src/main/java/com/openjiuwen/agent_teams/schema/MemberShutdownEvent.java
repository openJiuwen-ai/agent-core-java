// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a member is shut down.
 * 
 * Mirrors Python's agent_teams.schema.events.MemberShutdownEvent
 * 
 * @since 0.1.12
 */
public class MemberShutdownEvent extends BaseEventMessage {
    
    /** Force member shut down */
    private boolean force;
    
    public MemberShutdownEvent() {
        super();
    }
    
    public MemberShutdownEvent(String teamName, String memberName, boolean force) {
        super(teamName, memberName);
        this.force = force;
    }
    
    public boolean isForce() {
        return force;
    }
    
    public void setForce(boolean force) {
        this.force = force;
    }
}