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
 * Minimal repository search tool backed by SysOperation searchFiles.
 *
 * <p>Mirrors Python's filesystem search behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class GrepTool extends AbstractHarnessTool {

    public GrepTool(SysOperation sysOperation) {
        super(toolCard("harness.grep", "grep", "Search files under a path using a textual pattern."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.getOrDefault("path", "."));
        String pattern = stringValue(inputs.get("pattern"));
        if (pattern.isBlank()) {
            return new ToolOutput(false, null, "pattern cannot be empty");
        }
        var result = sysOperation.fs().searchFiles(path, pattern, List.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        @SuppressWarnings("unchecked")
        List<FileSystemItem> items = (List<FileSystemItem>) readField(payload, "matchingFiles");
        List<String> matches = items.stream()
                .map(item -> readStringField(item, "path"))
                .collect(Collectors.toList());
        return new ToolOutput(true, matches, null);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
