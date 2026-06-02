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
 * Minimal file write tool.
 *
 * <p>Mirrors Python's filesystem tool behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class WriteFileTool extends AbstractHarnessTool {

    public WriteFileTool(SysOperation sysOperation) {
        super(toolCard("harness.write_file", "write_file", "Write content to a file in the workspace."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = firstNonBlank(inputs.get("path"), inputs.get("file_path"));
        String content = stringValue(inputs.get("content"));
        if (path.isBlank()) {
            return new ToolOutput(false, null, "path cannot be empty");
        }
        String originalContent = null;
        boolean created = true;
        var currentFile = sysOperation.fs().readFile(path, "text", null, null, null, "UTF-8", 0, Map.of());
        Integer currentFileCode = readIntField(currentFile, "code");
        Object currentPayload = readField(currentFile, "data");
        if (currentFileCode != null
                && currentFileCode == StatusCode.SUCCESS.getCode()
                && currentPayload != null) {
            created = false;
            String resolvedPath = readStringField(currentPayload, "path");
            Object rawCurrentContent = readField(currentPayload, "content");
            originalContent = rawCurrentContent != null ? String.valueOf(rawCurrentContent) : "";
            var snapshot = FileReadRegistry.get(resolvedPath);
            if (snapshot.isEmpty()) {
                return new ToolOutput(false, null, "file must be read before overwriting existing content");
            }
            if (!originalContent.equals(snapshot.get().content())) {
                FileReadRegistry.forget(resolvedPath);
                return new ToolOutput(false, null, "file was modified since read; please read it again");
            }
        }
        var result = sysOperation.fs().writeFile(path, content, "text", false, false, true,
                null, "UTF-8", Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        String resolvedPath = readStringField(payload, "path");
        Integer size = readIntField(payload, "size");
        data.put("path", resolvedPath);
        data.put("size", size);
        data.put("bytes_written", size);
        data.put("mode", readStringField(payload, "mode"));
        data.put("type", created ? "create" : "update");
        data.put("created", created);
        if (!created) {
            data.put("original_file", originalContent);
        }
        FileReadRegistry.remember(resolvedPath, content);
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
