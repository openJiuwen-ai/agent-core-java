/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.clients;

import java.util.List;

/**
 * Package marker for browser runtime MCP clients.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.browser_move.clients} in
 * {@code openjiuwen/harness/tools/browser_move/clients/__init__.py}.</p>
 */
public final class BrowserMoveClientsPackage {

    public static final List<Class<?>> EXPORTED_CLIENTS = List.of(
            BrowserMoveStdioClient.class,
            BrowserMoveStreamableHttpClient.class
    );

    private BrowserMoveClientsPackage() {
    }

    public static List<Class<?>> exports() {
        return EXPORTED_CLIENTS;
    }
}
