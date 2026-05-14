/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.FileSystemItem;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal glob-like listing tool using recursive file listing.
 *
 * <p>Mirrors Python's filesystem search behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class GlobTool extends AbstractHarnessTool {

    public GlobTool(SysOperation sysOperation) {
        super(toolCard("harness.glob", "glob", "List files under a path using lightweight pattern filtering."),
                sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.getOrDefault("path", "."));
        String pattern = stringValue(inputs.get("pattern"));
        var result = sysOperation.fs().listFiles(path, true, null, null, false, null, Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        @SuppressWarnings("unchecked")
        List<FileSystemItem> items = (List<FileSystemItem>) readField(payload, "listItems");
        List<String> files = items.stream()
                .map(item -> readStringField(item, "path"))
                .filter(file -> pattern.isBlank() || file.contains(pattern.replace("**/", "").replace("*", "")))
                .collect(Collectors.toList());
        return new ToolOutput(true, files, null);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
