/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

/**
 * Centralized section names owned by TeamRail.
 * <p>
 * Mirrors Python's {@code TeamSectionName} in
 * {@code openjiuwen.agent_teams.agent.team_rail}.
 */
public final class TeamSectionName {
    
    public static final String ROLE = "team_role";
    public static final String HITT = "team_hitt";
    public static final String WORKFLOW = "team_workflow";
    public static final String LIFECYCLE = "team_lifecycle";
    public static final String PERSONA = "team_persona";
    public static final String EXTRA = "team_extra";
    public static final String INFO = "team_info";
    public static final String MEMBERS = "team_members";
    
    private TeamSectionName() {
        // Prevent instantiation
    }
}