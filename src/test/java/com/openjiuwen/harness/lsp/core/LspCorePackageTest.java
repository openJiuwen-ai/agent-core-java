/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's LSP core package export behavior in
 * {@code openjiuwen/harness/lsp/core/__init__.py}.
 */
class LspCorePackageTest {

    @Test
    void exportsMatchPythonAllOrderAndNames() {
        assertEquals("openjiuwen/harness/lsp/core/__init__.py", LspCorePackage.PYTHON_MODULE);
        assertEquals(List.of(
                "LspServerState",
                "LspServerStatus",
                "LSPServerManager",
                "ScopedLspServerConfig",
                "ServerInstanceKey",
                "SpawnHandle"
        ), LspCorePackage.EXPORTED_SYMBOLS);
        assertEquals(LspServerState.class, LspCorePackage.EXPORTED_TYPES.get("LspServerState"));
        assertEquals(LspServerStatus.class, LspCorePackage.EXPORTED_TYPES.get("LspServerStatus"));
        assertEquals(LSPServerManager.class, LspCorePackage.EXPORTED_TYPES.get("LSPServerManager"));
        assertEquals(ScopedLspServerConfig.class, LspCorePackage.EXPORTED_TYPES.get("ScopedLspServerConfig"));
        assertEquals(ServerInstanceKey.class, LspCorePackage.EXPORTED_TYPES.get("ServerInstanceKey"));
        assertEquals(SpawnHandle.class, LspCorePackage.EXPORTED_TYPES.get("SpawnHandle"));
    }
}
