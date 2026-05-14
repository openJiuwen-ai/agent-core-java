/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness LSP symbols tool.
 *
 * <p>Mirrors Python's symbol-query surface in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspSymbolsTool extends AbstractHarnessTool {

    public LspSymbolsTool() {
        super(toolCard("harness.lsp.symbols", "lsp_symbols", "Get document or workspace symbols."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        String query = inputs.get("query") == null ? "" : String.valueOf(inputs.get("query"));
        int limit = 50;
        if (inputs.get("limit") instanceof Number number) {
            limit = number.intValue();
        }
        Object data = query.isBlank()
                ? LspServerManager.getInstance().getDocumentSymbols(filePath)
                : LspServerManager.getInstance().getWorkspaceSymbols(query, limit);
        LspOperation operation = query.isBlank() ? LspOperation.DOCUMENT_SYMBOL : LspOperation.WORKSPACE_SYMBOL;
        return new ToolOutput(true, LspToolSupport.toToolPayload(operation, filePath, data).payload(), null);
    }
}
