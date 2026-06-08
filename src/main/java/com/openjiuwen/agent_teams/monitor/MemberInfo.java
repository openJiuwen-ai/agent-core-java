/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Team member information.
 *
 * <p>Mirrors Python's {@code MemberInfo} in
 * {@code openjiuwen/agent_teams/monitor/models.py}.</p>
 */
public record MemberInfo(
        @JsonProperty("member_name") String memberName,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("display_name") String displayName,
        String desc,
        String status,
        @JsonProperty("execution_status") String executionStatus,
        String mode
) {

    public static MemberInfo fromInternal(Object member) {
        return new MemberInfo(
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "member_name", "member_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "team_name", "team_id")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "display_name", "name")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "desc", "description")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "status")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "execution_status")),
                MonitorModelSupport.stringValue(MonitorModelSupport.firstPresent(member, "mode"))
        );
    }
}
