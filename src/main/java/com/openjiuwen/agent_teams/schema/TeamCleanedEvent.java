// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a team is cleaned up.
 * 
 * Mirrors Python's agent_teams.schema.events.TeamCleanedEvent
 * 
 * @since 0.1.12
 */
public class TeamCleanedEvent extends BaseEventMessage {
    
    public TeamCleanedEvent() {
        super();
    }
    
    public TeamCleanedEvent(String teamName) {
        super(teamName, null);
    }
}