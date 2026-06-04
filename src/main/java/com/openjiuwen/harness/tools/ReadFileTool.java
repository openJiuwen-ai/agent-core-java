/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.tools.filesystem.FileReadRegistry;

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
        super(toolCard("harness.read_file", "read_file", "Read a file from the workspace."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = firstNonBlank(inputs.get("path"), inputs.get("file_path"));
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
        String resolvedPath = readStringField(payload, "path");
        data.put("path", resolvedPath);
        Object content = readField(payload, "content");
        String textContent = content != null ? String.valueOf(content) : null;
        data.put("content", textContent);
        FileReadRegistry.remember(resolvedPath, textContent);
        return new ToolOutput(true, data, null);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = stringValue(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }
}
