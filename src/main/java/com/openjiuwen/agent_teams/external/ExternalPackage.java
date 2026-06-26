/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import java.util.List;

/**
 * External-agent access surface for agent teams.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.external} module in
 * {@code openjiuwen/agent_teams/external/__init__.py}.</p>
 */
public final class ExternalPackage {

    public static final String DESCRIPTION = """
            External-agent access surface for agent teams.

            This package lets an agent that lives outside the team process act as a
            first-class team member by talking directly to the shared team database
            and messager.
            """;

    public static final List<String> EXPORTED_NAMES = List.of(
            "TEAM_JOIN_ENV",
            "ExternalTeamClient",
            "TeamJoinDescriptor"
    );

    public static final String TEAM_JOIN_ENV = TeamJoinDescriptor.TEAM_JOIN_ENV;

    public static final List<Class<?>> EXPORTED_TYPES = List.of(
            ExternalTeamClient.class,
            TeamJoinDescriptor.class
    );

    private ExternalPackage() {
    }
}
