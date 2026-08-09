/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.Cwd;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final int DEFAULT_MAX_TIMEOUT_SECONDS = 3600;
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 20000;
    private static final int MIN_MAX_OUTPUT_CHARS = 200;

    private final PermissionConfig permission;
    private final List<String> commandAllowlist;

    public BashTool() {
        this(PermissionMode.AUTO, null, null);
    }

    public BashTool(PermissionMode permissionMode) {
        this(permissionMode, null, null);
    }

    public BashTool(PermissionMode permissionMode, List<String> denyPatterns, List<String> allowPatterns) {
        this(permissionMode, denyPatterns, allowPatterns, null);
    }

    public BashTool(PermissionMode permissionMode, List<String> denyPatterns, List<String> allowPatterns, List<String> commandAllowlist) {
        super(toolCard("bash", "BashTool", "Run a bash command."));
        permission = new PermissionConfig();
        permission.setMode(permissionMode);
        permission.setDenyPatterns(PermissionConfig.compilePatterns(denyPatterns));
        permission.setAllowPatterns(PermissionConfig.compilePatterns(allowPatterns));
        this.commandAllowlist = normalizeAllowlist(commandAllowlist);
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        BashInputs parsed = parseInputs(inputs);
        String command = parsed.command();
        if (command.isBlank()) {
            return ToolOutput.failure("command cannot be empty");
        }

        BashSecurity.SecurityCheck securityCheck = BashSecurity.checkInjection(command);
        if (securityCheck.isBlocked()) {
            return ToolOutput.failure(securityCheck.getReason());
        }

        BashSecurity.SecurityCheck safetyCheck = BashSecurity.checkSafety(command);
        if (safetyCheck.isBlocked()) {
            return ToolOutput.failure(safetyCheck.getReason());
        }

        String allowlistFailure = checkCommandAllowlist(command);
        if (allowlistFailure != null) {
            return ToolOutput.failure(allowlistFailure);
        }

        PermissionResult permissionResult = BashPermission.checkPermission(command, permission);
        if (!permissionResult.isAllowed()) {
            return ToolOutput.failure(permissionResult.getReason());
        }

        String cwd = parsed.workdir().isBlank() ? Cwd.getCwd() : parsed.workdir();
        if (!cwd.isBlank() && !new File(cwd).isDirectory()) {
            return ToolOutput.failure("workdir does not exist: " + cwd);
        }

        String warning = BashSecurity.getDestructiveWarning(command);
        if (parsed.runInBackground()) {
            return executeBackground(commandLineFor(command, parsed.shellType()), cwd);
        }

        CommandExecution execution = executeForRendering(
                commandLineFor(command, parsed.shellType()),
                cwd,
                parsed.timeoutSeconds()
        );
        if (execution.launchFailure() != null) {
            return ToolOutput.failure(execution.launchFailure());
        }

        if (execution.timedOut()) {
            String partial = BashOutput.renderPartialOnFailure(
                    new BashOutput.CommandOutput(
                            execution.stdout(),
                            execution.stderr(),
                            execution.exitCode(),
                            warning,
                            parsed.maxOutputChars()
                    ),
                    "command timed out after " + parsed.timeoutSeconds() + " seconds"
            );
            if (partial != null) {
                return ToolOutput.of(false, Map.of("content", partial), partial);
            }
            return ToolOutput.failure("command timed out after " + parsed.timeoutSeconds() + " seconds");
        }

        ExitCodeMeaning meaning = BashSemantics.interpretExitCode(
                command,
                execution.exitCode(),
                execution.stdout(),
                execution.stderr()
        );
        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(
                new BashOutput.CommandOutput(
                        execution.stdout(),
                        execution.stderr(),
                        execution.exitCode(),
                        warning,
                        parsed.maxOutputChars()
                ),
                meaning.isError()
        );
        return ToolOutput.of(!rendered.isError(), Map.of("content", rendered.content()),
                rendered.isError() ? rendered.content() : null);
    }

    public static ToolOutput execute(List<String> commandLine, String cwd, int timeoutSeconds) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            if (cwd != null && !cwd.isBlank()) {
                builder.directory(new File(cwd));
            }
            Process process = builder.start();
            StreamCollector stdout = StreamCollector.start(process.getInputStream());
            StreamCollector stderr = StreamCollector.start(process.getErrorStream());
            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                stdout.joinQuietly();
                stderr.joinQuietly();
                return ToolOutput.failure("command timed out after " + timeoutSeconds + " seconds");
            }
            String stdoutText = stdout.join();
            String stderrText = stderr.join();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stdout", stdoutText);
            data.put("stderr", stderrText);
            data.put("exit_code", process.exitValue());
            return ToolOutput.of(process.exitValue() == 0, data, process.exitValue() == 0 ? null : stderrText);
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    static String buildHistoryPath(String baseDir, String sessionId, String agentId) {
        String safeAgentId = agentId == null || agentId.isBlank() ? "default" : agentId;
        return Path.of(baseDir, ".agent_history", "file_ops_" + safeAgentId + "_" + sessionId + ".json").toString();
    }

    private static ToolOutput executeBackground(List<String> commandLine, String cwd) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            if (cwd != null && !cwd.isBlank()) {
                builder.directory(new File(cwd));
            }
            Process process = builder.start();
            if (process.waitFor(250, TimeUnit.MILLISECONDS) && process.exitValue() != 0) {
                String stderr = readProcessStream(process.getErrorStream());
                String stdout = readProcessStream(process.getInputStream());
                String message = !stderr.isBlank()
                        ? stderr
                        : (!stdout.isBlank() ? stdout : "background command failed with exit code " + process.exitValue());
                return ToolOutput.failure(message);
            }
            return ToolOutput.success(Map.of("pid", process.pid(), "status", "started"));
        } catch (IOException exception) {
            return ToolOutput.failure(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ToolOutput.failure(exception.getMessage());
        }
    }

    private static CommandExecution executeForRendering(List<String> commandLine, String cwd, int timeoutSeconds) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            if (cwd != null && !cwd.isBlank()) {
                builder.directory(new File(cwd));
            }
            Process process = builder.start();
            StreamCollector stdout = StreamCollector.start(process.getInputStream());
            StreamCollector stderr = StreamCollector.start(process.getErrorStream());
            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new CommandExecution(stdout.join(), stderr.join(), -1, true, null);
            }
            return new CommandExecution(stdout.join(), stderr.join(), process.exitValue(), false, null);
        } catch (Exception exception) {
            return new CommandExecution("", "", -1, false, exception.getMessage());
        }
    }

    private static String readProcessStream(java.io.InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Drain a process stream on a helper thread so {@code waitFor} cannot deadlock
     * when the OS pipe buffer fills.
     */
    private static final class StreamCollector {
        private final Thread thread;
        private volatile String text = "";
        private volatile IOException failure;

        private StreamCollector(java.io.InputStream stream) {
            this.thread = new Thread(() -> {
                try {
                    text = readProcessStream(stream);
                } catch (IOException exception) {
                    failure = exception;
                }
            }, "bash-tool-stream");
            this.thread.setDaemon(true);
        }

        static StreamCollector start(java.io.InputStream stream) {
            StreamCollector collector = new StreamCollector(stream);
            collector.thread.start();
            return collector;
        }

        String join() throws IOException, InterruptedException {
            thread.join();
            if (failure != null) {
                throw failure;
            }
            return text == null ? "" : text;
        }

        void joinQuietly() {
            try {
                join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Best-effort drain after timeout or kill.
            }
        }
    }

    private static List<String> commandLineFor(String command, String shellType) {
        String normalized = shellType == null || shellType.isBlank() ? "auto" : shellType.toLowerCase();
        if ("cmd".equals(normalized)) {
            return List.of("cmd", "/c", command);
        }
        if ("powershell".equals(normalized)) {
            return List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command);
        }
        if ("sh".equals(normalized)) {
            return List.of("sh", "-lc", command);
        }
        if ("bash".equals(normalized)) {
            return List.of("bash", "-lc", command);
        }
        if (isWindows()) {
            return List.of("cmd", "/c", command);
        }
        return List.of("bash", "-lc", command);
    }

    private static BashInputs parseInputs(Map<String, Object> inputs) {
        String command = makeSudoNonInteractive(stringValue(inputs == null ? null : inputs.get("command")).trim());
        String workdir = stringValue(inputs == null ? null : firstPresent(inputs, "workdir", "cwd"));
        String shellType = normalizeShellType(stringValue(inputs == null ? null : inputs.get("shell_type")));
        return new BashInputs(
                command,
                resolveTimeout(inputs == null ? null : inputs.get("timeout")),
                workdir,
                boolValue(inputs == null ? null : inputs.get("run_in_background"), false),
                resolveMaxOutputChars(inputs == null ? null : inputs.get("max_output_chars")),
                shellType,
                stringValue(inputs == null ? null : inputs.get("description"))
        );
    }

    private static Object firstPresent(Map<String, Object> inputs, String firstKey, String secondKey) {
        if (inputs == null) {
            return null;
        }
        Object first = inputs.get(firstKey);
        return first == null ? inputs.get(secondKey) : first;
    }

    private static int resolveTimeout(Object rawValue) {
        int timeout = intValue(rawValue, DEFAULT_TIMEOUT_SECONDS);
        int maxTimeout = intSystemProperty("BASH_TOOL_MAX_TIMEOUT_SECONDS", DEFAULT_MAX_TIMEOUT_SECONDS);
        return Math.max(1, Math.min(timeout, Math.max(1, maxTimeout)));
    }

    private static int resolveMaxOutputChars(Object rawValue) {
        int value = intValue(rawValue, 0);
        if (value == 0) {
            return 0;
        }
        int maxChars = intSystemProperty("BASH_TOOL_MAX_OUTPUT_CHARS", DEFAULT_MAX_OUTPUT_CHARS);
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

    private static String normalizeShellType(String rawShellType) {
        List<String> validShellTypes = List.of("auto", "cmd", "powershell", "bash", "sh");
        String normalized = rawShellType == null || rawShellType.isBlank() ? "auto" : rawShellType.toLowerCase();
        return validShellTypes.contains(normalized) ? normalized : "auto";
    }

    private static String makeSudoNonInteractive(String command) {
        return command.replaceAll("\\bsudo\\b(?!(?:\\s+-[a-zA-Z]*n|\\s+--non-interactive))(?=\\s)", "sudo -n");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String checkCommandAllowlist(String command) {
        if (commandAllowlist.isEmpty()) {
            return null;
        }
        for (String segment : BashSemantics.splitPipeline(command)) {
            String base = BashSemantics.extractBaseCommand(segment);
            if (!commandAllowlist.contains(base)) {
                return "Command blocked by shell allowlist: " + base;
            }
        }
        return null;
    }

    private static List<String> normalizeAllowlist(List<String> rawAllowlist) {
        if (rawAllowlist == null || rawAllowlist.isEmpty()) {
            return List.of();
        }
        return rawAllowlist.stream()
                .map(value -> value == null ? "" : value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
    }

    static Map<String, Object> dataMap(ToolOutput output) {
        if (output.getData() instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }

    static List<String> commandLineForTesting(String command, String shellType) {
        return new ArrayList<>(commandLineFor(command, shellType));
    }

    private record BashInputs(
            String command,
            int timeoutSeconds,
            String workdir,
            boolean runInBackground,
            int maxOutputChars,
            String shellType,
            String description
    ) {
    }

    private record CommandExecution(
            String stdout,
            String stderr,
            int exitCode,
            boolean timedOut,
            String launchFailure
    ) {
    }
}
