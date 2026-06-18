/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.external} module in
 * {@code openjiuwen/agent_teams/external/__init__.py}.
 */
class ExternalPackageTest {

    @Test
    void exportedNamesMirrorPythonAll() {
        assertEquals(
                List.of("TEAM_JOIN_ENV", "ExternalTeamClient", "TeamJoinDescriptor"),
                ExternalPackage.EXPORTED_NAMES
        );
        assertEquals(TeamJoinDescriptor.TEAM_JOIN_ENV, ExternalPackage.TEAM_JOIN_ENV);
        assertEquals(List.of(ExternalTeamClient.class, TeamJoinDescriptor.class), ExternalPackage.EXPORTED_TYPES);
    }

    @Test
    void descriptionPreservesPackageDocstringIntent() {
        assertTrue(ExternalPackage.DESCRIPTION.contains("External-agent access surface"));
        assertTrue(ExternalPackage.DESCRIPTION.contains("first-class team member"));
        assertTrue(ExternalPackage.DESCRIPTION.contains("shared team database"));
    }
}
