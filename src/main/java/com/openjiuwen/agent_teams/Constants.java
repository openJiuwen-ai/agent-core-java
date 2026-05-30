// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams;

import java.util.Collections;
import java.util.Set;

/**
 * Module-wide constants for agent teams.
 * 
 * Central home for reserved member names so the runtime has a single source
 * of truth instead of scattered string literals. Adding a new reserved name
 * means updating this module and nothing else.
 * 
 * Mirrors Python's {@code constants.py} module in {@code openjiuwen.agent_teams.constants}.
 * 
 * @since 0.1.12
 */
public final class Constants {
    
    /**
     * Reserved member name for the human collaborator in a HITT team.
     */
    public static final String HUMAN_AGENT_MEMBER_NAME = "human_agent";
    
    /**
     * Pseudo-member representing the external caller (not a team member).
     */
    public static final String USER_PSEUDO_MEMBER_NAME = "user";
    
    /**
     * Default leader member name when no explicit override is provided.
     */
    public static final String DEFAULT_LEADER_MEMBER_NAME = "team_leader";
    
    /**
     * Names that user-declared members must never take.
     * 
     * Enforced at TeamAgentSpec.build() time. human_agent is allowed only
     * when the runtime injects it via enable_hitt=True; manual declarations
     * under these names are rejected to keep model-facing identities stable.
     */
    public static final Set<String> RESERVED_MEMBER_NAMES = Collections.unmodifiableSet(
        Set.of(
            HUMAN_AGENT_MEMBER_NAME,
            USER_PSEUDO_MEMBER_NAME,
            DEFAULT_LEADER_MEMBER_NAME
        )
    );
    
    // Private constructor to prevent instantiation
    private Constants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
}
