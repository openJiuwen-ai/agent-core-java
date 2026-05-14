/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal document sync tool for Java harness LSP open/change flows.
 *
 * <p>Mirrors the Python manager's {@code open_file}/{@code change_file} bridge
 * at a simplified tool surface.
 */
public class LspDocumentSyncTool extends AbstractHarnessTool {

    public LspDocumentSyncTool() {
        super(toolCard("harness.lsp.document_sync", "lsp_document_sync",
                "Open or change a document and feed diagnostics into the Java LSP registry."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String action = inputs.get("action") == null ? "open" : String.valueOf(inputs.get("action"));
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        String languageId = inputs.get("language_id") == null ? "" : String.valueOf(inputs.get("language_id"));
        String content = inputs.get("content") == null ? null : String.valueOf(inputs.get("content"));

        if ("change".equalsIgnoreCase(action)) {
            LspServerManager.getInstance().changeFile(filePath, languageId, content);
        } else {
            LspServerManager.getInstance().openFile(filePath, languageId);
        }

        String fileUri = LspServerManager.getInstance().toFileUri(filePath);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("file_path", filePath);
        data.put("file_uri", fileUri);
        data.put("open", LspServerManager.getInstance().isFileOpen(fileUri));
        return new ToolOutput(true, data, null);
    }
}
