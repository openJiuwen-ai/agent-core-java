// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a persistent team enters standby between rounds.
 * 
 * Mirrors Python's agent_teams.schema.events.TeamStandbyEvent
 * 
 * @since 0.1.12
 */
public class TeamStandbyEvent extends BaseEventMessage {
    
    public TeamStandbyEvent() {
        super();
    }
    
    public TeamStandbyEvent(String teamName) {
        super(teamName, null);
    }
}