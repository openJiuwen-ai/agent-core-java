/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Team basic information.
 *
 * <p>Mirrors Python's {@code TeamInfo} in
 * {@code openjiuwen/agent_teams/monitor/models.py}.</p>
 */
public record TeamInfo(
        @JsonProperty("team_name") String teamName,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("leader_member_name") String leaderMemberName,
        String desc,
        long created
) {

    public static TeamInfo fromInternal(Object team) {
        return new TeamInfo(
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(team, "team_name", "teamId")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(team, "display_name", "name")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(team, "leader_member_name", "leaderId")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(team, "desc", "description")),
                MonitorModelSupport.longValue(MonitorModelSupport.firstPresent(team, "created", "created_at"), 0L)
        );
    }
}
