// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.tools.Team;

import java.util.Map;

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
    
    public static TeamInfo fromInternal(Object team) {
        if (team instanceof Team row) {
            return new TeamInfo(
                    row.getTeamName(),
                    row.getDisplayName(),
                    row.getLeaderMemberName(),
                    row.getDesc(),
                    row.getCreated() != null ? row.getCreated() : 0L
            );
        }
        if (team instanceof Map<?, ?> map) {
            String teamName = stringValue(firstPresent(map, "team_name", "teamName", "team_id", "teamId"));
            return new TeamInfo(
                    teamName,
                    stringValue(firstPresent(map, "display_name", "displayName", "name")),
                    stringValue(firstPresent(map, "leader_member_name", "leaderMemberName", "leader_id", "leaderId")),
                    stringValue(firstPresent(map, "desc", "description")),
                    longValue(firstPresent(map, "created", "created_at", "createdAt"))
            );
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

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
