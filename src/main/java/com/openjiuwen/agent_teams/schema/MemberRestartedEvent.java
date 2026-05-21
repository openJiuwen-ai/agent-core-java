// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a member process is restarted after failure.
 * 
 * Mirrors Python's agent_teams.schema.events.MemberRestartedEvent
 * 
 * @since 0.1.12
 */
public class MemberRestartedEvent extends BaseEventMessage {
    
    /** Reason for restart */
    private String reason = "health_check_failure";
    
    /** How many times this member has been restarted */
    private int restartCount = 1;
    
    public MemberRestartedEvent() {
        super();
    }
    
    public MemberRestartedEvent(String teamName, String memberName, String reason, int restartCount) {
        super(teamName, memberName);
        this.reason = reason;
        this.restartCount = restartCount;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public int getRestartCount() {
        return restartCount;
    }
    
    public void setRestartCount(int restartCount) {
        this.restartCount = restartCount;
    }
}