/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic LSP command facade.
 *
 * <p>Mirrors Python's {@code LspTool}, {@code build_lsp_tool}, and
 * {@code call_lsp_tool} in {@code openjiuwen/harness/tools/lsp_tool/_tool.py}.</p>
 */
public class LspTool extends AbstractHarnessTool {

    private final LspGateway gateway;

    public LspTool(LspGateway gateway) {
        super(toolCard("lsp_tool", "LspTool", "Dispatch a language-server operation."));
        this.gateway = gateway;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        LspOperation operation = LspOperation.fromValue(requiredString(inputs, "operation"));
        if (operation == null) {
            return ToolOutput.failure("unsupported lsp operation");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", operation.value());
        params.put("file_path", stringValue(inputs == null ? null : inputs.get("file_path")));
        params.put("line", intValue(inputs == null ? null : inputs.get("line"), 0));
        params.put("character", intValue(inputs == null ? null : inputs.get("character"), 0));
        if (gateway == null) {
            return ToolOutput.success(params);
        }
        return ToolOutput.success(gateway.call(operation, params, kwargs == null ? Map.of() : kwargs));
    }

    /**
     * Java boundary for Python's language-server session call in
     * {@code openjiuwen/harness/tools/lsp_tool/_tool.py}.
     */
    @FunctionalInterface
    public interface LspGateway {
        Map<String, Object> call(LspOperation operation, Map<String, Object> params, Map<String, Object> kwargs)
                throws Exception;
    }
}
