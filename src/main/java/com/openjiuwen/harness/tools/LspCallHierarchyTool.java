/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness prepare-call-hierarchy tool.
 *
 * <p>Mirrors Python's call hierarchy preparation path in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspCallHierarchyTool extends AbstractHarnessTool {

    public LspCallHierarchyTool() {
        super(toolCard("harness.lsp.prepare_call_hierarchy", "prepare_call_hierarchy",
                "Prepare call hierarchy items for the symbol at a position."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        int line = inputs.get("line") instanceof Number number ? number.intValue() : 1;
        int character = inputs.get("character") instanceof Number number ? number.intValue() : 1;
        Object data = LspServerManager.getInstance().prepareCallHierarchy(filePath, line, character);
        return new ToolOutput(true, LspToolSupport.toToolPayload(LspOperation.PREPARE_CALL_HIERARCHY, filePath, data).payload(), null);
    }
}
