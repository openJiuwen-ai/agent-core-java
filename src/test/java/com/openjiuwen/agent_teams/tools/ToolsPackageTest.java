/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsPackageTest {

    @Test
    void descriptionMatchesPythonDocstring() {
        assertThat(ToolsPackage.DESCRIPTION).isEqualTo("Tools for team orchestration.");
    }
}
