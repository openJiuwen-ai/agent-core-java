/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests package facade exports for agent-team rails.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/rails/__init__.py}.</p>
 */
class TeamRailsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(TeamRailsPackage.PYTHON_MODULE).isEqualTo("openjiuwen/agent_teams/rails/__init__.py");
        assertThat(TeamRailsPackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(List.of(
                "FirstIterationGate",
                "TeamPolicyRail",
                "TeamPlanModeRail",
                "TeamToolApprovalRail",
                "TeamToolRail",
                "qualify_team_tool_ids"
        ));
    }

    @Test
    void exportsHelperChecksSymbolPresence() {
        assertThat(TeamRailsPackage.exports("FirstIterationGate")).isTrue();
        assertThat(TeamRailsPackage.exports("qualify_team_tool_ids")).isTrue();
        assertThat(TeamRailsPackage.exports("missing")).isFalse();
    }

    @Test
    void exportListIsImmutableLikeModuleAllTupleUsage() {
        assertThatThrownBy(() -> TeamRailsPackage.EXPORTED_SYMBOLS.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
