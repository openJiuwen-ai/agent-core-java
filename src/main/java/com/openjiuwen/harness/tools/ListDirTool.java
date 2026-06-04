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
 * Minimal directory listing tool.
 *
 * <p>Mirrors Python's filesystem directory-listing behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class ListDirTool extends AbstractHarnessTool {

    public ListDirTool(SysOperation sysOperation) {
        super(toolCard("harness.list_files", "list_files", "List directories under a path."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.getOrDefault("path", "."));
        boolean recursive = booleanValue(inputs.get("recursive"));
        var result = sysOperation.fs().listDirectories(path, recursive, null, null, false, Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        @SuppressWarnings("unchecked")
        List<FileSystemItem> items = (List<FileSystemItem>) readField(payload, "listItems");
        List<String> paths = items.stream()
                .map(item -> readStringField(item, "path"))
                .collect(Collectors.toList());
        return new ToolOutput(true, paths, null);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
