// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.monitor;

/**
 * Team basic information.
 * 
 * Mirrors Python's agent_teams.monitor.models.TeamInfo
 * 
 * @since 0.1.12
 */
public class TeamInfo {
    
    /** Team identifier */
    private String teamId;
    
    /** Team display name */
    private String name;
    
    /** Leader member identifier */
    private String leaderId;
    
    /** Team description */
    private String desc;
    
    /** Creation timestamp in milliseconds */
    private long created;
    
    public TeamInfo() {
    }
    
    public TeamInfo(String teamId, String name, String leaderId, String desc, long created) {
        this.teamId = teamId;
        this.name = name;
        this.leaderId = leaderId;
        this.desc = desc;
        this.created = created;
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
    
    public String getLeaderId() {
        return leaderId;
    }
    
    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
    }
}