/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal harness shell tool backed by SysOperation shell execution.
 *
 * <p>Mirrors Python's bash tool flow in
 * {@code openjiuwen.harness.tools.shell.bash._tool}.
 */
public class BashTool extends AbstractHarnessTool {

    public BashTool(SysOperation sysOperation) {
        super(toolCard("harness.bash", "bash", "Execute shell commands in the active workspace."), sysOperation);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String command = stringValue(inputs.get("command"));
        String workdir = stringValue(inputs.get("workdir"));
        int timeout = intValue(inputs.get("timeout"), 300);
        if (command.isBlank()) {
            return new ToolOutput(false, null, "command cannot be empty");
        }
        var result = sysOperation.shell().executeCmd(command, workdir, timeout, Map.of(), Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stdout", readStringField(payload, "stdout"));
        data.put("stderr", readStringField(payload, "stderr"));
        data.put("exit_code", readIntField(payload, "exitCode"));
        data.put("cwd", readStringField(payload, "cwd"));
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
