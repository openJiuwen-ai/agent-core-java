/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal harness code execution tool.
 *
 * <p>Mirrors Python's code tool in
 * {@code openjiuwen.harness.tools.code}.
 */
public class CodeTool extends AbstractHarnessTool {

    public CodeTool(SysOperation sysOperation) {
        super(toolCard("harness.code", "code", "Execute source code snippets in the configured runtime."),
                sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String code = stringValue(inputs.get("code"));
        String language = stringValue(inputs.getOrDefault("language", "python"));
        int timeout = intValue(inputs.get("timeout"), 300);
        if (code.isBlank()) {
            return new ToolOutput(false, null, "code cannot be empty");
        }
        var result = sysOperation.code().executeCode(code, language, timeout, Map.of(), Map.of());
        Integer codeValue = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (codeValue == null || codeValue != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stdout", readStringField(payload, "stdout"));
        data.put("stderr", readStringField(payload, "stderr"));
        data.put("exit_code", readIntField(payload, "exitCode"));
        data.put("language", readStringField(payload, "language"));
        Integer exitCode = readIntField(payload, "exitCode");
        return new ToolOutput(exitCode != null && exitCode == 0,
                data,
                exitCode != null && exitCode == 0 ? null : readStringField(payload, "stderr"));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
