/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.prompts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoHarnessPromptsPackageTest {

    @Test
    void descriptionMatchesPythonDocstringHeadline() {
        assertThat(AutoHarnessPromptsPackage.DESCRIPTION)
                .isEqualTo("Auto Harness prompt 组装。");
    }
}
