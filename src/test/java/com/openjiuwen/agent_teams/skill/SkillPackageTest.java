/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillPackageTest {

    @Test
    void descriptionMatchesPythonDocstringHeadline() {
        assertThat(SkillPackage.DESCRIPTION)
                .isEqualTo("Skill + CLI surface for external agents joining a team.");
    }
}
