/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness LSP diagnostics tool.
 *
 * <p>Mirrors Python's diagnostics-oriented LSP tool behavior in
 * {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspDiagnosticsTool extends AbstractHarnessTool {

    public LspDiagnosticsTool() {
        super(toolCard("harness.lsp.diagnostics", "lsp_diagnostics", "Get lightweight LSP diagnostics for a file."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        String severity = inputs.get("severity") == null ? "all" : String.valueOf(inputs.get("severity"));
        boolean pending = inputs.get("pending") instanceof Boolean bool && bool;
        int maxPerFile = inputs.get("max_per_file") instanceof Number number ? number.intValue() : 10;
        int maxTotal = inputs.get("max_total") instanceof Number number ? number.intValue() : 30;
        Object diagnosticsData;
        if (pending) {
            diagnosticsData = LspServerManager.getInstance().getPendingDiagnostics(maxPerFile, maxTotal);
        } else {
            diagnosticsData = LspServerManager.getInstance().getDiagnostics(filePath, severity);
        }
        return new ToolOutput(true, LspToolSupport.toDiagnosticsPayload(filePath, diagnosticsData, pending).payload(), null);
    }
}
