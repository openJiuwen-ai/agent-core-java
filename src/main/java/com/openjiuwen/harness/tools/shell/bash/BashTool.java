/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes a bash command.
 *
 * <p>Mirrors Python's {@code BashTool} in
 * {@code openjiuwen/harness/tools/shell/bash/_tool.py}.</p>
 */
public class BashTool extends AbstractHarnessTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public BashTool() {
        super(toolCard("bash", "BashTool", "Run a bash command."));
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String command = stringValue(inputs == null ? null : inputs.get("command"));
        if (command.isBlank()) {
            return ToolOutput.failure("command is required");
        }
        int timeoutSeconds = intValue(inputs == null ? null : inputs.get("timeout"), DEFAULT_TIMEOUT_SECONDS);
        String cwd = stringValue(inputs == null ? null : inputs.get("cwd"));
        return execute(List.of("bash", "-lc", command), cwd, timeoutSeconds);
    }

    public static ToolOutput execute(List<String> commandLine, String cwd, int timeoutSeconds) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            if (cwd != null && !cwd.isBlank()) {
                builder.directory(new File(cwd));
            }
            Process process = builder.start();
            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolOutput.failure("command timed out after " + timeoutSeconds + " seconds");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stdout", stdout);
            data.put("stderr", stderr);
            data.put("exit_code", process.exitValue());
            return ToolOutput.of(process.exitValue() == 0, data, process.exitValue() == 0 ? null : stderr);
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }
}
