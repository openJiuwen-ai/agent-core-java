/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal file write tool.
 *
 * <p>Mirrors Python's filesystem tool behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class WriteFileTool extends AbstractHarnessTool {

    public WriteFileTool(SysOperation sysOperation) {
        super(toolCard("harness.write", "write", "Write content to a file in the workspace."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.get("path"));
        String content = stringValue(inputs.get("content"));
        if (path.isBlank()) {
            return new ToolOutput(false, null, "path cannot be empty");
        }
        var result = sysOperation.fs().writeFile(path, content, "text", false, false, true,
                null, "UTF-8", Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", readStringField(payload, "path"));
        data.put("size", readIntField(payload, "size"));
        data.put("mode", readStringField(payload, "mode"));
        return new ToolOutput(true, data, null);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
