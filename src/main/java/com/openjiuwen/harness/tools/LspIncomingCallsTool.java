/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness incoming-calls tool.
 *
 * <p>Mirrors Python's incoming-call lookup path in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspIncomingCallsTool extends AbstractHarnessTool {

    public LspIncomingCallsTool() {
        super(toolCard("harness.lsp.incoming_calls", "incoming_calls",
                "Find callers of the symbol at a position."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        int line = inputs.get("line") instanceof Number number ? number.intValue() : 1;
        int character = inputs.get("character") instanceof Number number ? number.intValue() : 1;
        Object data = LspServerManager.getInstance().incomingCalls(filePath, line, character);
        return new ToolOutput(true, LspToolSupport.toToolPayload(LspOperation.INCOMING_CALLS, filePath, data).payload(), null);
    }
}
