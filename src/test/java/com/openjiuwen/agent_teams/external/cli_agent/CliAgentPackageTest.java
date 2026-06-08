/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CliAgentPackageTest {

    @Test
    void descriptionMatchesPythonDocstringHeadline() {
        assertThat(CliAgentPackage.DESCRIPTION)
                .isEqualTo("Auto-launch of third-party CLI agents as team members (P2).");
    }
}
