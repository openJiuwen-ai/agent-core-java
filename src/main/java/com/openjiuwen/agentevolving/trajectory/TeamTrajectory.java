/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code TeamTrajectory} in
 * {@code openjiuwen/agent_evolving/trajectory/aggregator.py}.
 */
public class TeamTrajectory {

    private String teamId;
    private String sessionId;
    private Trajectory combined;
    private Map<String, Trajectory> members;

    public TeamTrajectory() {
        this.members = new LinkedHashMap<>();
    }

    public TeamTrajectory(String teamId, String sessionId, Trajectory combined,
                          Map<String, Trajectory> members) {
        this.teamId = teamId;
        this.sessionId = sessionId;
        this.combined = combined;
        this.members = members != null ? new LinkedHashMap<>(members) : new LinkedHashMap<>();
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Trajectory getCombined() {
        return combined;
    }

    public void setCombined(Trajectory combined) {
        this.combined = combined;
    }

    public Map<String, Trajectory> getMembers() {
        return members;
    }

    public void setMembers(Map<String, Trajectory> members) {
        this.members = members != null ? new LinkedHashMap<>(members) : new LinkedHashMap<>();
    }
}
