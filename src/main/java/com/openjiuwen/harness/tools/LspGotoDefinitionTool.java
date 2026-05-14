/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness goto-definition tool.
 *
 * <p>Mirrors Python's definition lookup path in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspGotoDefinitionTool extends AbstractHarnessTool {

    public LspGotoDefinitionTool() {
        super(toolCard("harness.lsp.goto_definition", "goto_definition", "Find where a symbol is defined."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        int line = inputs.get("line") instanceof Number number ? number.intValue() : 1;
        int character = inputs.get("character") instanceof Number number ? number.intValue() : 1;
        Object data = LspServerManager.getInstance().gotoDefinition(filePath, line, character);
        return new ToolOutput(true, LspToolSupport.toToolPayload(LspOperation.GO_TO_DEFINITION, filePath, data).payload(), null);
    }
}
