/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import java.util.List;
import java.util.Map;

/**
 * Minimal publishDiagnostics notification bridge.
 *
 * <p>Mirrors Python's notification-handler bridge from
 * {@code LSPServerManager._ensure_diagnostic_handler()} into
 * {@code LspDiagnosticRegistry.register(...)}.
 */
public final class LspNotificationBridge {

    private final LspDiagnosticRegistry registry;

    public LspNotificationBridge() {
        this(LspDiagnosticRegistry.getInstance());
    }

    public LspNotificationBridge(LspDiagnosticRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public String publishDiagnostics(String serverName, Map<String, Object> params) {
        if (params == null) {
            return "";
        }
        String uri = params.get("uri") != null ? String.valueOf(params.get("uri")) : "";
        Object diagnosticsObj = params.get("diagnostics");
        List<Map<String, Object>> diagnostics = diagnosticsObj instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        return registry.register(serverName, uri, diagnostics);
    }
}
