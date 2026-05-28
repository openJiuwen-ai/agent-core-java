/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced PowerShell tool with command semantics, smart truncation, and security.
 *
 * <p>Mirrors Python's PowerShellTool in
 * {@code openjiuwen.harness.tools.shell.powershell._tool}.
 *
 * <p>This tool provides:
 * <ul>
 *   <li>Command execution with timeout and working directory control</li>
 *   <li>Background execution support for long-running commands</li>
 *   <li>Output truncation with large output persistence</li>
 *   <li>Permission checking for destructive commands</li>
 *   <li>Security checks for injection patterns</li>
 *   <li>Exit code interpretation with semantic meaning</li>
 * </ul>
 */
public class PowerShellTool extends Tool {

    private final SysOperation operation;
    private final PowerShellPermission.PermissionConfig permission;

    /**
     * Creates a PowerShellTool with default permission mode.
     *
     * @param operation the system operation provider
     */
    public PowerShellTool(SysOperation operation) {
        this(operation, "auto", null, null, null);
    }

    /**
     * Creates a PowerShellTool with configurable permissions.
     *
     * @param operation the system operation provider
     * @param permissionMode the permission mode: "auto", "bypass", or "strict"
     * @param denyPatterns list of regex patterns for denied commands
     * @param allowPatterns list of regex patterns for allowed commands
     * @param agentId optional agent identifier for logging
     */
    public PowerShellTool(
            SysOperation operation,
            String permissionMode,
            List<String> denyPatterns,
            List<String> allowPatterns,
            String agentId
    ) {
        super(buildToolCard(agentId));
        this.operation = operation;
        this.permission = new PowerShellPermission.PermissionConfig(
                PowerShellPermission.PermissionMode.fromValue(permissionMode),
                PowerShellPermission.compilePatterns(denyPatterns),
                PowerShellPermission.compilePatterns(allowPatterns)
        );
    }

    private static ToolCard buildToolCard(String agentId) {
        ToolCard card = new ToolCard();
        assignCardField(card, "id", "harness.powershell");
        assignCardField(card, "name", "PowerShellTool");
        assignCardField(card, "description", "Execute PowerShell commands in the active workspace.");
        if (agentId != null) {
            assignCardField(card, "agentId", agentId);
        }
        return card;
    }

    private static void assignCardField(Object target, String fieldName, Object value) {
        if (target == null || value == null) return;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to assign field '" + fieldName + "'", e);
            }
        }
    }

    /**
     * Resolves and validates a timeout value.
     *
     * @param rawValue the raw timeout input
     * @param defaultTimeout the default timeout if parsing fails
     * @return the resolved timeout, clamped to max timeout
     */
    public static int resolveTimeout(Object rawValue, int defaultTimeout) {
        int timeout = defaultTimeout;
        if (rawValue instanceof Number number) {
            timeout = number.intValue();
        } else if (rawValue != null) {
            try {
                timeout = Integer.parseInt(String.valueOf(rawValue));
            } catch (NumberFormatException ignored) {
            }
        }
        int maxTimeout = 3600;
        String maxTimeoutEnv = System.getenv("POWER_SHELL_TOOL_MAX_TIMEOUT_SECONDS");
        if (maxTimeoutEnv != null) {
            try {
                maxTimeout = Math.max(1, Integer.parseInt(maxTimeoutEnv));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(1, Math.min(timeout, maxTimeout));
    }

    /**
     * Parses and clamps tool inputs.
     *
     * @param inputs the raw input map
     * @return a parsed inputs record
     */
    public static ParsedInputs parseInputs(Map<String, Object> inputs) {
        String command = inputs.containsKey("command")
                ? String.valueOf(inputs.get("command")).trim()
                : "";
        int timeout = resolveTimeout(inputs.get("timeout"), 300);
        String workdir = inputs.containsKey("workdir")
                ? String.valueOf(inputs.get("workdir"))
                : "";
        boolean background = inputs.containsKey("background")
                && Boolean.TRUE.equals(inputs.get("background"));
        int maxOutputChars = 8000;
        if (inputs.containsKey("max_output_chars")) {
            Object raw = inputs.get("max_output_chars");
            int val = raw instanceof Number number ? number.intValue() : 8000;
            maxOutputChars = Math.max(200, Math.min(val, 20000));
        }
        String description = inputs.containsKey("description")
                ? String.valueOf(inputs.get("description"))
                : "";
        return new ParsedInputs(command, timeout, workdir, background, maxOutputChars, description);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        ParsedInputs p = parseInputs(inputs);

        if (p.command.isEmpty()) {
            return new ToolOutput(false, null, "command cannot be empty");
        }

        String resolvedCwd = resolveWorkingDirectory(p.workdir);

        // Guard check in strict mode
        if ("1".equals(System.getenv("OPENJIUWEN_BASH_STRICT"))) {
            ToolOutput guard = guard(p);
            if (guard != null) {
                return guard;
            }
        }

        String warning = PowerShellSecurity.getDestructiveWarning(p.command);

        if (!p.description.isEmpty()) {
            Loggers.SYS_OPERATION.info("PowerShellTool: " + p.description + " - " + p.command);
        }

        // Background execution
        if (p.background) {
            var res = operation.shell().executeCmdBackground(
                    p.command,
                    resolvedCwd,
                    "powershell"
            );
            Integer code = readIntField(res, "code");
            if (code == null || code != StatusCode.SUCCESS.getCode()) {
                return new ToolOutput(false, null, readStringField(res, "message"));
            }
            Object data = readField(res, "data");
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("pid", readIntField(data, "pid"));
            output.put("status", "started");
            return new ToolOutput(true, output, null);
        }

        // Synchronous execution
        var res = operation.shell().executeCmd(
                p.command,
                resolvedCwd,
                p.timeout,
                null,  // environment
                null   // options
        );
        Integer code = readIntField(res, "code");
        if (code == null || code != StatusCode.SUCCESS.getCode()) {
            return new ToolOutput(false, null, readStringField(res, "message"));
        }

        Object data = readField(res, "data");
        int exitCode = readIntField(data, "exitCode") != null ? readIntField(data, "exitCode") : -1;
        String stdout = readStringField(data, "stdout") != null ? readStringField(data, "stdout") : "";
        String stderr = readStringField(data, "stderr") != null ? readStringField(data, "stderr") : "";

        ExitCodeMeaning meaning = PowerShellSemantics.interpretExitCode(
                p.command, exitCode, stdout, stderr
        );
        boolean silent = PowerShellSemantics.isSilent(p.command);

        // Handle large output persistence
        String persistedPath = null;
        Integer persistedSize = null;
        if (stdout.length() + stderr.length() > p.maxOutputChars) {
            PowerShellOutput.PersistResult persisted = PowerShellOutput.persistLargeOutput(stdout, stderr);
            if (persisted != null) {
                persistedPath = persisted.getPath();
                persistedSize = persisted.getByteCount();
            }
        }

        Map<String, Object> outputData = new LinkedHashMap<>();
        outputData.put("stdout", PowerShellOutput.truncateOutput(stdout, p.maxOutputChars));
        outputData.put("stderr", PowerShellOutput.truncateOutput(stderr, p.maxOutputChars));
        outputData.put("exit_code", exitCode);
        outputData.put("return_code_interpretation", meaning.getMessage());
        outputData.put("no_output_expected", silent);
        outputData.put("destructive_warning", warning);
        outputData.put("persisted_output_path", persistedPath);
        outputData.put("persisted_output_size", persistedSize);

        return new ToolOutput(
                !meaning.isError(),
                outputData,
                meaning.isError() ? PowerShellOutput.truncateOutput(stderr, p.maxOutputChars) : null
        );
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        ParsedInputs p = parseInputs(inputs);

        if (p.command.isEmpty()) {
            java.util.List<Object> emptyList = new java.util.ArrayList<>();
            emptyList.add(new ToolOutput(false, null, "command cannot be empty"));
            return emptyList.iterator();
        }

        String resolvedCwd = resolveWorkingDirectory(p.workdir);

        // Guard check in strict mode
        if ("1".equals(System.getenv("OPENJIUWEN_BASH_STRICT"))) {
            ToolOutput guard = guard(p);
            if (guard != null) {
                java.util.List<Object> guardList = new java.util.ArrayList<>();
                guardList.add(guard);
                return guardList.iterator();
            }
        }

        String warning = PowerShellSecurity.getDestructiveWarning(p.command);

        if (!p.description.isEmpty()) {
            Loggers.SYS_OPERATION.info("PowerShellTool(stream): " + p.description + " - " + p.command);
        }

        long start = System.nanoTime();
        StringBuilder accumulatedStdout = new StringBuilder();
        StringBuilder accumulatedStderr = new StringBuilder();
        int finalExitCode = -1;

        // Stream execution - simplified for Java
        Iterator<ExecuteCmdStreamResult> chunks = operation.shell().executeCmdStream(
                p.command,
                resolvedCwd,
                p.timeout,
                null,  // environment
                null   // options
        );

        java.util.List<Object> results = new java.util.ArrayList<>();

        // Convert Iterator to List for for-each loop
        java.util.List<ExecuteCmdStreamResult> chunkList = new java.util.ArrayList<>();
        while (chunks.hasNext()) {
            chunkList.add(chunks.next());
        }

        for (ExecuteCmdStreamResult chunk : chunkList) {
            Integer chunkCode = chunk.getCode();
            if (chunkCode == null || chunkCode != StatusCode.SUCCESS.getCode()) {
                results.add(new ToolOutput(false, null, chunk.getMessage()));
                return results.iterator();
            }

            ExecuteCmdChunkData chunkData = chunk.getData();
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;

            Integer chunkExitCode = chunkData.getExitCode();
            if (chunkExitCode != null) {
                finalExitCode = chunkExitCode;
            }

            String text = chunkData.getText() != null ? chunkData.getText() : "";
            String streamType = chunkData.getType() != null ? chunkData.getType() : "stdout";

            if ("stderr".equals(streamType)) {
                accumulatedStderr.append(text);
            } else {
                accumulatedStdout.append(text);
            }

            Map<String, Object> chunkOutput = new LinkedHashMap<>();
            chunkOutput.put("text", text);
            chunkOutput.put("type", streamType);
            chunkOutput.put("chunk_index", chunkData.getChunkIndex());
            chunkOutput.put("exit_code", chunkExitCode);
            chunkOutput.put("elapsed_time_seconds", Math.round(elapsed * 100.0) / 100.0);

            results.add(new ToolOutput(true, chunkOutput, null));
        }

        // Final summary
        ExitCodeMeaning meaning = PowerShellSemantics.interpretExitCode(
                p.command, finalExitCode, accumulatedStdout.toString(), accumulatedStderr.toString()
        );
        boolean silent = PowerShellSemantics.isSilent(p.command);

        double totalElapsed = (System.nanoTime() - start) / 1_000_000_000.0;

        Map<String, Object> finalData = new LinkedHashMap<>();
        finalData.put("stdout", PowerShellOutput.truncateOutput(accumulatedStdout.toString(), p.maxOutputChars));
        finalData.put("stderr", PowerShellOutput.truncateOutput(accumulatedStderr.toString(), p.maxOutputChars));
        finalData.put("exit_code", finalExitCode);
        finalData.put("return_code_interpretation", meaning.getMessage());
        finalData.put("no_output_expected", silent);
        finalData.put("destructive_warning", warning);
        finalData.put("elapsed_time_seconds", Math.round(totalElapsed * 100.0) / 100.0);

        results.add(new ToolOutput(
                !meaning.isError(),
                finalData,
                meaning.isError() ? PowerShellOutput.truncateOutput(accumulatedStderr.toString(), p.maxOutputChars) : null
        ));

        return results.iterator();
    }

    /**
     * Security and permission guard check.
     *
     * @param p the parsed inputs
     * @return a ToolOutput with error if blocked, null if allowed
     */
    private ToolOutput guard(ParsedInputs p) {
        PowerShellSecurity.SecurityCheck sec = PowerShellSecurity.checkInjection(p.command);
        if (sec.isBlocked()) {
            return new ToolOutput(false, null, sec.getReason());
        }

        PowerShellPermission.PermissionResult perm = PowerShellPermission.checkPermission(p.command, permission);
        if (!perm.isAllowed()) {
            return new ToolOutput(false, null, perm.getReason());
        }

        return null;
    }

    /**
     * Resolves the working directory for command execution.
     *
     * @param workdir the explicit working directory from inputs
     * @return the resolved working directory
     */
    private String resolveWorkingDirectory(String workdir) {
        if (workdir != null && !workdir.isEmpty()) {
            return workdir;
        }
        // Fallback to system cwd/workspace
        String cwd = System.getProperty("user.dir");
        return cwd != null ? cwd : "";
    }

    // Helper field readers
    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }

    private static Integer readIntField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        if (value instanceof Integer integer) return integer;
        if (value instanceof Number number) return number.intValue();
        return null;
    }

    /**
     * Parsed and clamped inputs for a PowerShellTool invocation.
     */
    public record ParsedInputs(
            String command,
            int timeout,
            String workdir,
            boolean background,
            int maxOutputChars,
            String description
    ) {}
}