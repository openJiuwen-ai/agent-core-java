/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal file read tool.
 *
 * <p>Mirrors Python's filesystem tool behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class ReadFileTool extends AbstractHarnessTool {

    public ReadFileTool(SysOperation sysOperation) {
        super(toolCard("harness.read", "read", "Read a file from the workspace."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.get("path"));
        if (path.isBlank()) {
            return new ToolOutput(false, null, "path cannot be empty");
        }
        var result = sysOperation.fs().readFile(path, "text", null, null, null, "UTF-8", 0, Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", readStringField(payload, "path"));
        Object content = readField(payload, "content");
        data.put("content", content != null ? String.valueOf(content) : null);
        return new ToolOutput(true, data, null);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
