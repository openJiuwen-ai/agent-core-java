/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.shell.bash.BashTool;

import java.util.List;
import java.util.Map;

/**
 * Executes a PowerShell command.
 *
 * <p>Mirrors Python's {@code PowerShellTool} in
 * {@code openjiuwen/harness/tools/shell/powershell/_tool.py}.</p>
 */
public class PowerShellTool extends AbstractHarnessTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public PowerShellTool() {
        super(toolCard("powershell", "PowerShellTool", "Run a PowerShell command."));
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String command = stringValue(inputs == null ? null : inputs.get("command"));
        if (command.isBlank()) {
            return ToolOutput.failure("command is required");
        }
        int timeoutSeconds = intValue(inputs == null ? null : inputs.get("timeout"), DEFAULT_TIMEOUT_SECONDS);
        String cwd = stringValue(inputs == null ? null : inputs.get("cwd"));
        return BashTool.execute(List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command),
                cwd, timeoutSeconds);
    }
}
