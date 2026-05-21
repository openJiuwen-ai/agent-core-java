// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a team is created.
 * 
 * Mirrors Python's agent_teams.schema.events.TeamCreatedEvent
 * 
 * @since 0.1.12
 */
public class TeamCreatedEvent extends BaseEventMessage {
    
    /** Team display label */
    private String displayName;
    
    /** Leader member name */
    private String leaderMemberName;
    
    /** Creation timestamp */
    private long created;
    
    public TeamCreatedEvent() {
        super();
    }
    
    public TeamCreatedEvent(String teamName, String displayName, String leaderMemberName, long created) {
        super(teamName, null);
        this.displayName = displayName;
        this.leaderMemberName = leaderMemberName;
        this.created = created;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getLeaderMemberName() {
        return leaderMemberName;
    }
    
    public void setLeaderMemberName(String leaderMemberName) {
        this.leaderMemberName = leaderMemberName;
    }
    
    public long getCreated() {
        return created;
    }
    
    public void setCreated(long created) {
        this.created = created;
    }
}