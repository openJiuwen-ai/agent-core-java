/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal team identity and metadata definition.
 *
 * <p>Mirrors Python's {@code TeamSpec} in
 * {@code openjiuwen.agent_teams.schema.team}.
 */
public class TeamSpec {

    private String teamName;
    private String displayName;
    private String leaderMemberName;
    private String language;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }
}
