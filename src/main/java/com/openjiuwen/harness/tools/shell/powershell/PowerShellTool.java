/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.shell.bash.BashTool;

import java.nio.file.Path;
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
    private static final int DEFAULT_MAX_TIMEOUT_SECONDS = 3600;
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 20000;
    private static final int MIN_MAX_OUTPUT_CHARS = 200;

    private final SysOperation operation;
    private final PermissionConfig permission;
    private final String agentId;

    public PowerShellTool() {
        this(null, "cn", PermissionMode.AUTO, null, null, null);
    }

    public PowerShellTool(SysOperation operation) {
        this(operation, "cn", PermissionMode.AUTO, null, null, null);
    }

    public PowerShellTool(SysOperation operation, PermissionMode permissionMode, String agentId) {
        this(operation, "cn", permissionMode, null, null, agentId);
    }

    public PowerShellTool(
            SysOperation operation,
            String language,
            PermissionMode permissionMode,
            List<String> denyPatterns,
            List<String> allowPatterns,
            String agentId
    ) {
        super(toolCard("powershell", "powershell", "Run a PowerShell command."));
        this.operation = operation;
        this.permission = new PermissionConfig();
        this.permission.setMode(permissionMode == null ? PermissionMode.AUTO : permissionMode);
        this.permission.setDenyPatterns(PermissionConfig.compilePatterns(denyPatterns));
        this.permission.setAllowPatterns(PermissionConfig.compilePatterns(allowPatterns));
        this.agentId = agentId == null || agentId.isBlank() ? "default" : agentId;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        PowerShellInputs parsed = parseInputs(inputs);
        if (parsed.command().isBlank()) {
            return ToolOutput.failure("command cannot be empty");
        }

        PowerShellSecurity.SecurityCheck security = PowerShellSecurity.checkInjection(parsed.command());
        if (security.isBlocked()) {
            return ToolOutput.failure(security.getReason());
        }

        PermissionResult permissionResult = PowerShellPermission.checkPermission(parsed.command(), permission);
        if (!permissionResult.isAllowed()) {
            return ToolOutput.failure(permissionResult.getReason());
        }

        String warning = PowerShellSecurity.getDestructiveWarning(parsed.command());
        if (operation != null) {
            return executeWithOperation(parsed, warning);
        }
        return BashTool.execute(
                List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", parsed.command()),
                parsed.workdir(),
                parsed.timeout()
        );
    }

    String buildHistoryPath(AgentSessionApi session) {
        String sessionId = session.getSessionId();
        String baseDir = Cwd.getWorkspace();
        if (baseDir == null || baseDir.isBlank()) {
            baseDir = Path.of(Cwd.getCwd()).toAbsolutePath().normalize().toString();
        }
        return Path.of(baseDir, ".agent_history", "file_ops_" + agentId + "_" + sessionId + ".json").toString();
    }

    private ToolOutput executeWithOperation(PowerShellInputs parsed, String warning) {
        ExecuteCmdResult result;
        try {
            result = executeOperation(parsed);
        } catch (RuntimeException exception) {
            return ToolOutput.failure(exception.getMessage());
        }
        if (result == null) {
            return ToolOutput.failure("PowerShell operation returned no result");
        }
        ExecuteCmdData data = result.getData();
        String stdout = data == null || data.getStdout() == null ? "" : data.getStdout();
        String stderr = data == null || data.getStderr() == null ? "" : data.getStderr();
        int exitCode = data == null || data.getExitCode() == null ? -1 : data.getExitCode();
        if (result.getCode() != StatusCode.SUCCESS.code()) {
            String partial = PowerShellOutput.renderPartialOnFailure(
                    new PowerShellOutput.CommandOutput(
                            stdout,
                            stderr,
                            exitCode,
                            warning,
                            parsed.maxOutputChars()
                    ),
                    result.getMessage()
            );
            return partial == null ? ToolOutput.failure(result.getMessage())
                    : ToolOutput.of(false, Map.of("content", partial), partial);
        }

        ExitCodeMeaning meaning = PowerShellSemantics.interpretExitCode(
                parsed.command(),
                exitCode,
                stdout,
                stderr
        );
        PowerShellOutput.RenderedContent rendered = PowerShellOutput.renderToolContent(
                new PowerShellOutput.CommandOutput(
                        stdout,
                        stderr,
                        exitCode,
                        warning,
                        parsed.maxOutputChars()
                ),
                meaning.isError()
        );
        return ToolOutput.of(!rendered.isError(), Map.of("content", rendered.content()),
                rendered.isError() ? rendered.content() : null);
    }

    private ExecuteCmdResult executeOperation(PowerShellInputs parsed) {
        BaseShellOperation shell = operation.shell();
        if (shell == null) {
            throw new IllegalStateException("PowerShell operation has no shell");
        }
        return shell.executeCmd(
                parsed.command(),
                parsed.workdir(),
                parsed.timeout(),
                Map.of(),
                Map.of(),
                BaseShellOperation.ShellType.POWERSHELL
        ).join();
    }

    private static PowerShellInputs parseInputs(Map<String, Object> inputs) {
        String command = stringValue(inputs == null ? null : inputs.get("command")).trim();
        String workdir = stringValue(inputs == null ? null : firstPresent(inputs, "workdir", "cwd"));
        if (workdir.isBlank()) {
            workdir = Cwd.getCwd();
        }
        return new PowerShellInputs(
                command,
                resolveTimeout(inputs == null ? null : inputs.get("timeout")),
                workdir,
                boolValue(inputs == null ? null : inputs.get("background"), false),
                resolveMaxOutputChars(inputs == null ? null : inputs.get("max_output_chars")),
                stringValue(inputs == null ? null : inputs.get("description"))
        );
    }

    private static Object firstPresent(Map<String, Object> inputs, String first, String second) {
        if (inputs == null) {
            return null;
        }
        Object firstValue = inputs.get(first);
        return firstValue == null ? inputs.get(second) : firstValue;
    }

    private static int resolveTimeout(Object rawValue) {
        int timeout = intValue(rawValue, DEFAULT_TIMEOUT_SECONDS);
        int maxTimeout = intSystemProperty("POWER_SHELL_TOOL_MAX_TIMEOUT_SECONDS", DEFAULT_MAX_TIMEOUT_SECONDS);
        return Math.max(1, Math.min(timeout, Math.max(1, maxTimeout)));
    }

    private static int resolveMaxOutputChars(Object rawValue) {
        int value = intValue(rawValue, 0);
        if (value == 0) {
            return 0;
        }
        int maxChars = intSystemProperty("POWER_SHELL_TOOL_MAX_OUTPUT_CHARS", DEFAULT_MAX_OUTPUT_CHARS);
        return Math.max(MIN_MAX_OUTPUT_CHARS, Math.min(value, Math.max(MIN_MAX_OUTPUT_CHARS, maxChars)));
    }

    private static int intSystemProperty(String key, int defaultValue) {
        try {
            String property = System.getProperty(key);
            return property == null || property.isBlank() ? defaultValue : Integer.parseInt(property);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private record PowerShellInputs(
            String command,
            int timeout,
            String workdir,
            boolean background,
            int maxOutputChars,
            String description
    ) {
    }
}
