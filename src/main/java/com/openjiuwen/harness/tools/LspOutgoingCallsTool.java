/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Java harness outgoing-calls tool.
 *
 * <p>Mirrors Python's outgoing-call lookup path in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspOutgoingCallsTool extends AbstractHarnessTool {

    public LspOutgoingCallsTool() {
        super(toolCard("harness.lsp.outgoing_calls", "outgoing_calls",
                "Find symbols called by the function at a position."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String filePath = inputs.get("file_path") == null ? "" : String.valueOf(inputs.get("file_path"));
        int line = inputs.get("line") instanceof Number number ? number.intValue() : 1;
        int character = inputs.get("character") instanceof Number number ? number.intValue() : 1;
        Object data = LspServerManager.getInstance().outgoingCalls(filePath, line, character);
        return new ToolOutput(true, LspToolSupport.toToolPayload(LspOperation.OUTGOING_CALLS, filePath, data).payload(), null);
    }
}
