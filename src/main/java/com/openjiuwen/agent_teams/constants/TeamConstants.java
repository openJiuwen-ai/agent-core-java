/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.constants;

import java.util.Set;

/**
 * Module-wide constants for Java agent teams.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.constants} in
 * {@code openjiuwen/agent_teams/constants.py}.
 */
public final class TeamConstants {

    public static final String HUMAN_AGENT_MEMBER_NAME = "human_agent";
    public static final String USER_PSEUDO_MEMBER_NAME = "user";
    public static final String DEFAULT_LEADER_MEMBER_NAME = "team_leader";
    public static final Set<String> RESERVED_MEMBER_NAMES = Set.of(
            HUMAN_AGENT_MEMBER_NAME,
            USER_PSEUDO_MEMBER_NAME,
            DEFAULT_LEADER_MEMBER_NAME
    );

    private TeamConstants() {
    }
}
