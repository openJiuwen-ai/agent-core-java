/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness find-references tool.
 *
 * <p>Mirrors Python's reference lookup path in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspFindReferencesTool extends AbstractHarnessTool {

    public LspFindReferencesTool() {
        super(toolCard("harness.lsp.find_references", "find_references", "Find references to a symbol."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        int line = inputs.get("line") instanceof Number number ? number.intValue() : 1;
        int character = inputs.get("character") instanceof Number number ? number.intValue() : 1;
        boolean includeDeclaration = !(inputs.get("include_declaration") instanceof Boolean bool) || bool;
        Object data = LspServerManager.getInstance().findReferences(filePath, line, character, includeDeclaration);
        return new ToolOutput(true, LspToolSupport.toToolPayload(LspOperation.FIND_REFERENCES, filePath, data).payload(), null);
    }
}
