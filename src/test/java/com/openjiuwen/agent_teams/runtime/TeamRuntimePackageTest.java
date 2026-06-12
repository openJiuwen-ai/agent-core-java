/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests runtime package export metadata.
 *
 * <p>Mirrors Python's {@code __all__} in
 * {@code openjiuwen/agent_teams/runtime/__init__.py}.</p>
 */
class TeamRuntimePackageTest {

    @Test
    void pythonModulePathIsExact() {
        assertThat(TeamRuntimePackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/runtime/__init__.py");
    }

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(TeamRuntimePackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(List.of(
                "ActiveTeam",
                "ActiveTeamInfo",
                "RunAction",
                "RunActionKind",
                "RuntimeState",
                "TeamRuntimeActivation",
                "TeamRuntimeManager",
                "TeamRuntimePool",
                "TeamSessionReleaseInfo"
        ));
    }

    @Test
    void exportsReportsKnownSymbolsOnly() {
        assertThat(TeamRuntimePackage.exports("TeamRuntimeManager")).isTrue();
        assertThat(TeamRuntimePackage.exports("TeamRuntimeMetadata")).isFalse();
    }
}
