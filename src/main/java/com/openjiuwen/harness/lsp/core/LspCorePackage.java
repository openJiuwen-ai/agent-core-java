/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for LSP core exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.lsp.core} in
 * {@code openjiuwen/harness/lsp/core/__init__.py}.</p>
 */
public final class LspCorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/lsp/core/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "LspServerState",
            "LspServerStatus",
            "LSPServerManager",
            "ScopedLspServerConfig",
            "ServerInstanceKey",
            "SpawnHandle"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private LspCorePackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("LspServerState", LspServerState.class);
        exports.put("LspServerStatus", LspServerStatus.class);
        exports.put("LSPServerManager", LSPServerManager.class);
        exports.put("ScopedLspServerConfig", ScopedLspServerConfig.class);
        exports.put("ServerInstanceKey", ServerInstanceKey.class);
        exports.put("SpawnHandle", SpawnHandle.class);
        return Map.copyOf(exports);
    }
}
