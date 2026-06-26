/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the team workspace package facade.
 *
 * <p>Mirrors Python's {@code __all__} in
 * {@code openjiuwen/agent_teams/team_workspace/__init__.py}.</p>
 */
class TeamWorkspacePackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        List<String> expected = List.of(
                "ConflictStrategy",
                "TeamWorkspaceConfig",
                "WorkspaceFileLock",
                "WorkspaceMode",
                "TeamWorkspaceManager",
                "WorkspaceMetaTool",
                "TeamWorkspaceRail"
        );

        assertThat(TeamWorkspacePackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/team_workspace/__init__.py");
        assertThat(TeamWorkspacePackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(expected);
        assertThat(TeamWorkspacePackage.all()).isSameAs(TeamWorkspacePackage.EXPORTED_SYMBOLS);
    }

    @Test
    void resolvesExportedTypesAndSymbolPresence() {
        assertThat(TeamWorkspacePackage.typeFor("ConflictStrategy")).isEqualTo(ConflictStrategy.class);
        assertThat(TeamWorkspacePackage.typeFor("TeamWorkspaceConfig")).isEqualTo(TeamWorkspaceConfig.class);
        assertThat(TeamWorkspacePackage.typeFor("WorkspaceFileLock")).isEqualTo(WorkspaceFileLock.class);
        assertThat(TeamWorkspacePackage.typeFor("WorkspaceMode")).isEqualTo(WorkspaceMode.class);
        assertThat(TeamWorkspacePackage.typeFor("TeamWorkspaceManager")).isEqualTo(TeamWorkspaceManager.class);
        assertThat(TeamWorkspacePackage.typeFor("WorkspaceMetaTool")).isEqualTo(WorkspaceMetaTool.class);
        assertThat(TeamWorkspacePackage.typeFor("TeamWorkspaceRail")).isEqualTo(TeamWorkspaceRail.class);
        assertThat(TeamWorkspacePackage.exports("WorkspaceMetaTool")).isTrue();
        assertThat(TeamWorkspacePackage.exports("missing")).isFalse();
    }
}
