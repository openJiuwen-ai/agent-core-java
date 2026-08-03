/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlaywrightRuntimeBootstrapTest {

    @Test
    void sourceRootUsesBrowserMovePath() {
        String path = PlaywrightRuntimeBootstrap.resolveSourceRoot().toString().replace('\\', '/');

        assertTrue(path.endsWith("openjiuwen/harness/tools/browser_move"));
    }
}
