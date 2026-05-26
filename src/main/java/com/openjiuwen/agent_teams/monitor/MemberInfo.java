// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.monitor;

/**
 * Team member information.
 * 
 * Mirrors Python's agent_teams.monitor.models.MemberInfo
 * 
 * @since 0.1.12
 */
public class MemberInfo {
    
    /** Member identifier */
    private String memberId;
    
    /** Team identifier */
    private String teamId;
    
    /** Member display name */
    private String name;
    
    /** Member description */
    private String desc;
    
    /** MemberStatus value */
    private String status;
    
    /** ExecutionStatus value */
    private String executionStatus;
    
    /** MemberMode value */
    private String mode;
    
    public MemberInfo() {
    }
    
    public MemberInfo(String memberId, String teamId, String name, String status, String mode) {
        this.memberId = memberId;
        this.teamId = teamId;
        this.name = name;
        this.status = status;
        this.mode = mode;
    }
    
    public String getMemberId() {
        return memberId;
    }
    
    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }
    
    public String getTeamId() {
        return teamId;
    }
    
    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getExecutionStatus() {
        return executionStatus;
    }
    
    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    public static MemberInfo fromInternal(Object member) {
        // Placeholder: convert internal member model
        return new MemberInfo("", "", "", "active", "member");
    }
}