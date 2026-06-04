/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal text replacement editor.
 *
 * <p>Mirrors Python's edit-file behaviors in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public class EditFileTool extends AbstractHarnessTool {

    public EditFileTool(SysOperation sysOperation) {
        super(toolCard("harness.edit_file", "edit_file", "Edit a text file by replacing an old snippet with a new snippet."),
                sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String path = stringValue(inputs.get("path"));
        String oldText = stringValue(inputs.get("old_text"));
        String newText = stringValue(inputs.get("new_text"));
        if (path.isBlank()) {
            return new ToolOutput(false, null, "path cannot be empty");
        }
        var readResult = sysOperation.fs().readFile(path, "text", null, null, null, "UTF-8", 0, Map.of());
        Integer readCode = readIntField(readResult, "code");
        Object readPayload = readField(readResult, "data");
        if (readCode == null || readCode != StatusCode.SUCCESS.getCode() || readPayload == null) {
            return new ToolOutput(false, null, readStringField(readResult, "message"));
        }
        Object currentContent = readField(readPayload, "content");
        String currentText = currentContent != null ? String.valueOf(currentContent) : "";
        if (oldText.isBlank()) {
            return new ToolOutput(false, null, "old_text cannot be empty");
        }
        if (!currentText.contains(oldText)) {
            return new ToolOutput(false, null, "old_text not found in file");
        }
        String updatedText = currentText.replace(oldText, newText);
        var writeResult = sysOperation.fs().writeFile(path, updatedText, "text", false, false, true,
                null, "UTF-8", Map.of());
        Integer writeCode = readIntField(writeResult, "code");
        Object writePayload = readField(writeResult, "data");
        if (writeCode == null || writeCode != StatusCode.SUCCESS.getCode() || writePayload == null) {
            return new ToolOutput(false, null, readStringField(writeResult, "message"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", readStringField(writePayload, "path"));
        data.put("replaced", oldText);
        data.put("replacement", newText);
        return new ToolOutput(true, data, null);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
