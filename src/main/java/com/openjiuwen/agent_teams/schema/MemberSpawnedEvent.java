// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.schema;

/**
 * Event published when a team member is spawned.
 * 
 * Mirrors Python's agent_teams.schema.events.MemberSpawnedEvent
 * 
 * @since 0.1.12
 */
public class MemberSpawnedEvent extends BaseEventMessage {
    
    public MemberSpawnedEvent() {
        super();
    }
    
    public MemberSpawnedEvent(String teamName, String memberName) {
        super(teamName, memberName);
    }
}