/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal tool surface for injecting publishDiagnostics-style notifications.
 *
 * <p>Mirrors the Python-side manager notification bridge around
 * {@code textDocument/publishDiagnostics} registration and delivery.
 */
public class LspPublishDiagnosticsTool extends AbstractHarnessTool {

    public LspPublishDiagnosticsTool() {
        super(toolCard("harness.lsp.publish_diagnostics", "lsp_publish_diagnostics",
                "Inject a publishDiagnostics-style notification into the Java LSP registry."), null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String serverId = inputs.get("server_id") == null ? "" : String.valueOf(inputs.get("server_id"));
        String uri = inputs.get("uri") == null ? "" : String.valueOf(inputs.get("uri"));
        Object diagnosticsObj = inputs.get("diagnostics");
        List<Map<String, Object>> diagnostics = diagnosticsObj instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uri", uri);
        params.put("diagnostics", diagnostics);

        String batchId = LspServerManager.getInstance().handlePublishDiagnostics(serverId, params);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("server_id", serverId);
        data.put("uri", uri);
        data.put("batch_id", batchId);
        data.put("accepted", batchId != null && !batchId.isBlank());
        return new ToolOutput(true, data, null);
    }
}
