// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.agent.TeamMember;

import java.util.Map;

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
        if (member instanceof TeamMember row) {
            MemberInfo info = new MemberInfo(
                    row.getMemberName(),
                    row.getTeamName(),
                    row.getDisplayName(),
                    row.getStatus() != null ? row.getStatus().name() : null,
                    null
            );
            info.setDesc(row.getDesc());
            info.setExecutionStatus(row.getExecutionStatus() != null ? row.getExecutionStatus().name() : null);
            return info;
        }
        if (member instanceof Map<?, ?> map) {
            MemberInfo info = new MemberInfo(
                    stringValue(firstPresent(map, "member_name", "memberName", "member_id", "memberId")),
                    stringValue(firstPresent(map, "team_name", "teamName", "team_id", "teamId")),
                    stringValue(firstPresent(map, "display_name", "displayName", "name")),
                    stringValue(firstPresent(map, "status")),
                    stringValue(firstPresent(map, "mode"))
            );
            info.setDesc(stringValue(firstPresent(map, "desc", "description")));
            info.setExecutionStatus(stringValue(firstPresent(map, "execution_status", "executionStatus")));
            return info;
        }
        return null;
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
