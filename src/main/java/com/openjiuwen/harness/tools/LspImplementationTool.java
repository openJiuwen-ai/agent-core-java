/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness goto-implementation tool.
 *
 * <p>Mirrors Python's implementation lookup path in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspImplementationTool extends AbstractHarnessTool {

    public LspImplementationTool() {
        super(toolCard("harness.lsp.goto_implementation", "goto_implementation",
                "Find implementations of an interface or abstract method."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        int line = inputs.get("line") instanceof Number number ? number.intValue() : 1;
        int character = inputs.get("character") instanceof Number number ? number.intValue() : 1;
        Object data = LspServerManager.getInstance().gotoImplementation(filePath, line, character);
        return new ToolOutput(true, LspToolSupport.toToolPayload(LspOperation.GO_TO_IMPLEMENTATION, filePath, data).payload(), null);
    }
}
