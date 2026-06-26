/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.clients;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/tools/browser_move/clients/__init__.py}.
 */
class BrowserMoveClientsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        BrowserMoveStdioClient.class,
                        BrowserMoveStreamableHttpClient.class
                ),
                BrowserMoveClientsPackage.exports()
        );
    }
}
