/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.tools.shell.bash.BashOutputUtils;
import com.openjiuwen.harness.tools.shell.bash.BashPermissionUtils;
import com.openjiuwen.harness.tools.shell.bash.BashSecurityUtils;
import com.openjiuwen.harness.tools.shell.bash.BashSemanticsUtils;
import com.openjiuwen.harness.tools.shell.bash.ExitCodeMeaning;
import com.openjiuwen.harness.tools.shell.bash.PermissionConfig;
import com.openjiuwen.harness.tools.shell.bash.PermissionMode;
import com.openjiuwen.harness.tools.shell.bash.PermissionResult;
import com.openjiuwen.harness.tools.shell.bash.SecurityCheck;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal harness shell tool backed by SysOperation shell execution.
 *
 * <p>Mirrors Python's bash tool flow in
 * {@code openjiuwen.harness.tools.shell.bash._tool}.
 */
public class BashTool extends AbstractHarnessTool {

    private final PermissionConfig permissionConfig;

    public BashTool(SysOperation sysOperation) {
        this(sysOperation, "auto", null, null);
    }

    public BashTool(SysOperation sysOperation, String permissionMode) {
        this(sysOperation, permissionMode, null, null);
    }

    public BashTool(SysOperation sysOperation, String permissionMode, List<String> denyPatterns,
                    List<String> allowPatterns) {
        super(toolCard("harness.bash", "bash", "Execute shell commands in the active workspace."), sysOperation);
        this.permissionConfig = new PermissionConfig(
                PermissionMode.fromString(permissionMode),
                PermissionConfig.compilePatterns(denyPatterns),
                PermissionConfig.compilePatterns(allowPatterns));
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String command = stringValue(inputs.get("command"));
        String workdir = stringValue(inputs.get("workdir"));
        int timeout = intValue(inputs.get("timeout"), 300);
        int maxOutputChars = clamp(intValue(inputs.get("max_output_chars"), 8000), 200, 20000);
        boolean background = boolValue(inputs.get("run_in_background"), false)
                || boolValue(inputs.get("background"), false);
        if (command.isBlank()) {
            return new ToolOutput(false, null, "command cannot be empty");
        }

        SecurityCheck security = BashSecurityUtils.checkInjection(command);
        if (security.isBlocked()) {
            return new ToolOutput(false, null, security.getReason());
        }
        SecurityCheck safety = BashSecurityUtils.checkCommandSafety(command);
        if (safety.isBlocked()) {
            return new ToolOutput(false, null, safety.getReason());
        }

        PermissionResult permission = BashPermissionUtils.checkPermission(command, permissionConfig);
        if (!permission.isAllowed()) {
            return new ToolOutput(false, null, permission.getReason());
        }

        String destructiveWarning = BashSecurityUtils.getDestructiveWarning(command);

        if (background) {
            var backgroundResult = sysOperation.shell().executeCmdBackground(command, workdir, "bash");
            Integer code = readIntField(backgroundResult, "code");
            if (code == null || code != StatusCode.SUCCESS.getCode()) {
                return new ToolOutput(false, null, readStringField(backgroundResult, "message"));
            }
            Object payload = readField(backgroundResult, "data");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", readIntField(payload, "pid"));
            data.put("status", "started");
            return new ToolOutput(true, data, null);
        }

        var result = sysOperation.shell().executeCmd(command, workdir, timeout, Map.of(), Map.of());
        Integer code = readIntField(result, "code");
        Object payload = readField(result, "data");
        if (code == null || code != StatusCode.SUCCESS.getCode() || payload == null) {
            return new ToolOutput(false, null, readStringField(result, "message"));
        }
        String stdout = valueOrEmpty(readStringField(payload, "stdout"));
        String stderr = valueOrEmpty(readStringField(payload, "stderr"));
        Integer exitCode = readIntField(payload, "exitCode");
        int effectiveExitCode = exitCode != null ? exitCode : -1;
        ExitCodeMeaning meaning = BashSemanticsUtils.interpretExitCode(command, effectiveExitCode, stdout, stderr);

        String persistedPath = null;
        Integer persistedSize = null;
        if (stdout.length() + stderr.length() > maxOutputChars) {
            BashOutputUtils.PersistResult persisted = BashOutputUtils.persistLargeOutput(stdout, stderr);
            persistedPath = persisted.getFilePath();
            persistedSize = persisted.getTotalBytes();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stdout", BashOutputUtils.truncateOutput(stdout, maxOutputChars));
        data.put("stderr", BashOutputUtils.truncateOutput(stderr, maxOutputChars));
        data.put("exit_code", effectiveExitCode);
        data.put("cwd", readStringField(payload, "cwd"));
        data.put("return_code_interpretation", meaning.getMessage());
        data.put("no_output_expected", BashSemanticsUtils.isSilent(command));
        data.put("destructive_warning", destructiveWarning);
        data.put("persisted_output_path", persistedPath);
        data.put("persisted_output_size", persistedSize);
        return new ToolOutput(!meaning.isError(),
                data,
                meaning.isError() ? BashOutputUtils.truncateOutput(stderr, maxOutputChars) : null);
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

    private static int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
