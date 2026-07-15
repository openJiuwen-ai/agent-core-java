/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import java.util.Set;

/**
 * TeamConstants.
 * 
 * @since 0.1.7
 */
public final class TeamConstants {
    /**
     * DEFAULT_LEADER_MEMBER_NAME.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_LEADER_MEMBER_NAME = "team_leader";

    /**
     * HUMAN_AGENT_MEMBER_NAME.
     * 
     * @since 0.1.7
     */
    public static final String HUMAN_AGENT_MEMBER_NAME = "human_agent";

    /**
     * USER_PSEUDO_MEMBER_NAME.
     * 
     * @since 0.1.7
     */
    public static final String USER_PSEUDO_MEMBER_NAME = "user";

    /**
     * RESERVED_MEMBER_NAMES.
     * 
     * @since 0.1.7
     */
    public static final Set<String> RESERVED_MEMBER_NAMES =
        Set.of(DEFAULT_LEADER_MEMBER_NAME, HUMAN_AGENT_MEMBER_NAME, USER_PSEUDO_MEMBER_NAME);

    /**
     * TeamConstants.
     * 
     * @since 0.1.7
     */
    private TeamConstants() {
    }
}
