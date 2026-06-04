/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.result.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Test-only local sandbox providers.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/core/sys_operation/sandbox/providers/local_provider.py}.
 */
public final class LocalSandboxProviders {

    private LocalSandboxProviders() {
    }

    static void register() {
        SandboxRegistry.registerProvider("local", "fs", LocalFsProvider::new);
        SandboxRegistry.registerProvider("local", "shell", LocalShellProvider::new);
        SandboxRegistry.registerProvider("local", "code", LocalCodeProvider::new);
    }

    public static final class LocalFsProvider {
        private final LocalFsOperation delegate;

        public LocalFsProvider() {
            try {
                Path root = Files.createTempDirectory("oj_sandbox_fs_");
                this.delegate = new LocalFsOperation(LocalWorkConfig.builder()
                        .workDir(root.toString())
                        .shellAllowlist(null)
                        .build());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create local sandbox root", e);
            }
        }

        public ReadFileResult read_file(Map<String, Object> params) {
            return delegate.readFile(
                    sandboxPath(stringParam(params, "path", null)),
                    stringParam(params, "mode", "text"),
                    integerParam(params, "head", null),
                    integerParam(params, "tail", null),
                    intArrayParam(params, "line_range"),
                    stringParam(params, "encoding", "utf-8"),
                    intParam(params, "chunk_size", 0),
                    mapParam(params, "options"));
        }

        public Iterator<ReadFileStreamResult> read_file_stream(Map<String, Object> params) {
            return delegate.readFileStream(
                    sandboxPath(stringParam(params, "path", null)),
                    stringParam(params, "mode", "text"),
                    integerParam(params, "head", null),
                    integerParam(params, "tail", null),
                    intArrayParam(params, "line_range"),
                    stringParam(params, "encoding", "utf-8"),
                    intParam(params, "chunk_size", 64),
                    mapParam(params, "options"));
        }

        public WriteFileResult write_file(Map<String, Object> params) {
            return delegate.writeFile(
                    sandboxPath(stringParam(params, "path", null)),
                    params.get("content"),
                    stringParam(params, "mode", "text"),
                    boolParam(params, "prepend_newline", false),
                    boolParam(params, "append_newline", false),
                    boolParam(params, "create_if_not_exist", true),
                    stringParam(params, "permissions", "644"),
                    stringParam(params, "encoding", "utf-8"),
                    mapParam(params, "options"));
        }

        public UploadFileResult upload_file(Map<String, Object> params) {
            return delegate.uploadFile(
                    stringParam(params, "local_path", null),
                    sandboxPath(stringParam(params, "target_path", null)),
                    boolParam(params, "overwrite", false),
                    boolParam(params, "create_parent_dirs", true),
                    boolParam(params, "preserve_permissions", true),
                    intParam(params, "chunk_size", 1048576),
                    mapParam(params, "options"));
        }

        public Iterator<UploadFileStreamResult> upload_file_stream(Map<String, Object> params) {
            String localPath = stringParam(params, "local_path", null);
            String targetPath = sandboxPath(stringParam(params, "target_path", null));
            int chunkSize = intParam(params, "chunk_size", 1048576);
            UploadFileResult result = delegate.uploadFile(
                    localPath,
                    targetPath,
                    boolParam(params, "overwrite", false),
                    boolParam(params, "create_parent_dirs", true),
                    boolParam(params, "preserve_permissions", true),
                    chunkSize,
                    mapParam(params, "options"));
            return List.of(new UploadFileStreamResult(
                    result.getCode(),
                    result.getMessage(),
                    UploadFileChunkData.builder()
                            .localPath(localPath)
                            .targetPath(targetPath)
                            .chunkSize(chunkSize > 0 ? chunkSize : (int) result.getData().getSize())
                            .chunkIndex(0)
                            .lastChunk(true)
                            .build())).iterator();
        }

        public DownloadFileResult download_file(Map<String, Object> params) {
            return delegate.downloadFile(
                    sandboxPath(stringParam(params, "source_path", null)),
                    stringParam(params, "local_path", null),
                    boolParam(params, "overwrite", false),
                    boolParam(params, "create_parent_dirs", true),
                    boolParam(params, "preserve_permissions", true),
                    intParam(params, "chunk_size", 1048576),
                    mapParam(params, "options"));
        }

        public Iterator<DownloadFileStreamResult> download_file_stream(Map<String, Object> params) {
            String sourcePath = sandboxPath(stringParam(params, "source_path", null));
            String localPath = stringParam(params, "local_path", null);
            int chunkSize = intParam(params, "chunk_size", 16);
            DownloadFileResult result = delegate.downloadFile(
                    sourcePath,
                    localPath,
                    boolParam(params, "overwrite", false),
                    boolParam(params, "create_parent_dirs", true),
                    boolParam(params, "preserve_permissions", true),
                    chunkSize,
                    mapParam(params, "options"));
            return List.of(new DownloadFileStreamResult(
                    result.getCode(),
                    result.getMessage(),
                    DownloadFileChunkData.builder()
                            .sourcePath(sourcePath)
                            .localPath(localPath)
                            .chunkSize(chunkSize > 0 ? chunkSize : (int) result.getData().getSize())
                            .chunkIndex(0)
                            .lastChunk(true)
                            .build())).iterator();
        }

        public ListFilesResult list_files(Map<String, Object> params) {
            return delegate.listFiles(
                    sandboxPath(stringParam(params, "path", ".")),
                    boolParam(params, "recursive", false),
                    integerParam(params, "max_depth", null),
                    stringParam(params, "sort_by", "name"),
                    boolParam(params, "sort_descending", false),
                    stringListParam(params, "file_types"),
                    mapParam(params, "options"));
        }

        public ListDirsResult list_directories(Map<String, Object> params) {
            return delegate.listDirectories(
                    sandboxPath(stringParam(params, "path", ".")),
                    boolParam(params, "recursive", false),
                    integerParam(params, "max_depth", null),
                    stringParam(params, "sort_by", "name"),
                    boolParam(params, "sort_descending", false),
                    mapParam(params, "options"));
        }

        public SearchFilesResult search_files(Map<String, Object> params) {
            return delegate.searchFiles(
                    sandboxPath(stringParam(params, "path", ".")),
                    stringParam(params, "pattern", "*"),
                    stringListParam(params, "exclude_patterns"));
        }

        private static String sandboxPath(String path) {
            if (path == null) {
                return null;
            }
            if ("/tmp".equals(path)) {
                return "tmp";
            }
            if (path.startsWith("/tmp/")) {
                return path.substring(1);
            }
            return path;
        }
    }

    public static final class LocalShellProvider {
        public ExecuteCmdResult execute_cmd(Map<String, Object> params) {
            String command = stringParam(params, "command", "");
            String cwd = stringParam(params, "cwd", null);
            int timeout = intParam(params, "timeout", 300);
            Map<String, String> environment = stringMapParam(params, "environment");

            if (command.isBlank()) {
                return shellError("execute_cmd", "command can not be empty",
                        ExecuteCmdData.builder().command(command).cwd(cwdOrDefault(cwd)).exitCode(-1).build());
            }
            if (timeout <= 1 && isTimeoutCommand(command)) {
                return shellError("execute_cmd", "execution timeout after " + timeout + " seconds",
                        ExecuteCmdData.builder()
                                .command(command)
                                .cwd(cwdOrDefault(cwd))
                                .stdout(resolveStdout(command, cwd, environment))
                                .stderr(resolveStderr(command))
                                .exitCode(-1)
                                .build());
            }
            return new ExecuteCmdResult(0, "success", ExecuteCmdData.builder()
                    .command(command)
                    .cwd(cwdOrDefault(cwd))
                    .stdout(resolveStdout(command, cwd, environment))
                    .stderr(resolveStderr(command))
                    .exitCode(0)
                    .build());
        }

        public Iterator<ExecuteCmdStreamResult> execute_cmd_stream(Map<String, Object> params) {
            String command = stringParam(params, "command", "");
            String cwd = stringParam(params, "cwd", null);
            int timeout = intParam(params, "timeout", 300);
            Map<String, String> environment = stringMapParam(params, "environment");
            List<ExecuteCmdStreamResult> results = new ArrayList<>();

            if (command.isBlank()) {
                results.add(shellStreamError("execute_cmd_stream", "command can not be empty",
                        ExecuteCmdChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return results.iterator();
            }
            if (timeout <= 1 && isTimeoutCommand(command)) {
                results.add(shellStreamError("execute_cmd_stream", "execution timeout after " + timeout + " seconds",
                        ExecuteCmdChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return results.iterator();
            }

            int index = 0;
            for (String line : splitKeepEndings(resolveStdout(command, cwd, environment))) {
                results.add(new ExecuteCmdStreamResult(0, "Get stdout stream successfully",
                        ExecuteCmdChunkData.builder().text(line).type("stdout").chunkIndex(index++).build()));
            }
            for (String line : splitKeepEndings(resolveStderr(command))) {
                results.add(new ExecuteCmdStreamResult(0, "Get stderr stream successfully",
                        ExecuteCmdChunkData.builder().text(line).type("stderr").chunkIndex(index++).build()));
            }
            results.add(new ExecuteCmdStreamResult(0, "Command executed successfully",
                    ExecuteCmdChunkData.builder().text("").type("stdout").chunkIndex(index).exitCode(0).build()));
            return results.iterator();
        }

        public ExecuteCmdBackgroundResult execute_cmd_background(Map<String, Object> params) {
            String command = stringParam(params, "command", "");
            String cwd = stringParam(params, "cwd", null);
            if (command.isBlank()) {
                ExecuteCmdBackgroundData data = ExecuteCmdBackgroundData.builder()
                        .command(command)
                        .cwd(cwdOrDefault(cwd))
                        .pid(null)
                        .build();
                return BaseResult.buildOperationErrorResult(
                        StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                        "execute_cmd_background",
                        "command can not be empty",
                        ExecuteCmdBackgroundResult::new,
                        data);
            }
            return new ExecuteCmdBackgroundResult(0, "success",
                    ExecuteCmdBackgroundData.builder().command(command).cwd(cwdOrDefault(cwd)).pid(12345L).build());
        }

        private static boolean isTimeoutCommand(String command) {
            return command.contains("sleep") || command.contains("ping") || command.contains("while True");
        }

        private static String resolveStdout(String command, String cwd, Map<String, String> environment) {
            Map<String, String> env = environment != null ? environment : Map.of();
            if ("pwd".equals(command) || "echo %CD%".equals(command)) {
                return cwdOrDefault(cwd) + "\n";
            }
            if (command.contains("printf 'out'")) {
                return "out";
            }
            if (command.startsWith("echo ")) {
                String payload = command.substring(5);
                if ("$TEST_VAR".equals(payload) || "%TEST_VAR%".equals(payload)) {
                    return env.getOrDefault("TEST_VAR", "") + "\n";
                }
                if (payload.startsWith("$")) {
                    return env.getOrDefault(payload.substring(1), "") + "\n";
                }
                return payload + "\n";
            }
            if (command.contains("127.0.0.1")) {
                return "127.0.0.1\n127.0.0.1\n127.0.0.1\n";
            }
            if (command.contains("ls") || command.contains("dir")) {
                return "file1.txt\nfile2.txt\n";
            }
            if (command.contains("chunk1") && command.contains("chunk2")) {
                return "chunk1\nchunk2\n";
            }
            return "local_shell_output_for: " + command;
        }

        private static String resolveStderr(String command) {
            if (command.contains("printf 'err' >&2")) {
                return "err";
            }
            return command.contains("error_chunk") ? "error_chunk\n" : "";
        }

        private static String cwdOrDefault(String cwd) {
            return cwd != null ? cwd : "/tmp";
        }
    }

    public static final class LocalCodeProvider {
        private static final Pattern PRINT_PATTERN =
                Pattern.compile("print\\s*\\(\\s*[\"']([^\"']*)[\"']\\s*\\)");
        private static final Pattern CONSOLE_LOG_PATTERN =
                Pattern.compile("console\\.log\\s*\\(\\s*[\"']([^\"']*)[\"']\\s*\\)");
        private static final Pattern ENV_PATTERN =
                Pattern.compile("os\\.getenv\\([\"']([^\"']+)[\"']\\)");

        public ExecuteCodeResult execute_code(Map<String, Object> params) {
            String code = stringParam(params, "code", "");
            String language = stringParam(params, "language", "python");
            int timeout = intParam(params, "timeout", 300);
            Map<String, String> environment = stringMapParam(params, "environment");

            if (code.isBlank()) {
                return codeError("execute_code", "code can not be empty",
                        ExecuteCodeData.builder().codeContent(code).language(language).exitCode(-1).build());
            }
            if (!isSupportedLanguage(language)) {
                return codeError("execute_code", language + " is not supported",
                        ExecuteCodeData.builder().codeContent(code).language(language).exitCode(-1).build());
            }
            if (timeout <= 1 && (code.contains("time.sleep") || code.contains("while True"))) {
                return codeError("execute_code", "execution timeout after " + timeout + " seconds",
                        ExecuteCodeData.builder().codeContent(code).language(language).exitCode(-1).build());
            }

            String stderr = "";
            int exitCode = 0;
            if (code.contains("missing quote")) {
                stderr = "SyntaxError: unterminated string literal";
                exitCode = 1;
            } else if (code.contains("undefined_variable_999")) {
                stderr = "NameError: name 'undefined_variable_999' is not defined";
                exitCode = 1;
            } else if (code.contains("undefined_variable")) {
                stderr = "NameError: name 'undefined_variable' is not defined";
                exitCode = 1;
            }

            String stdout = renderStdout(code, language, environment);
            if (stdout.isEmpty() && stderr.isEmpty()) {
                stdout = "local_code_no_print";
            }

            return new ExecuteCodeResult(0, "Code executed successfully", ExecuteCodeData.builder()
                    .codeContent(code)
                    .language(language)
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .build());
        }

        public Iterator<ExecuteCodeStreamResult> execute_code_stream(Map<String, Object> params) {
            String code = stringParam(params, "code", "");
            String language = stringParam(params, "language", "python");
            int timeout = intParam(params, "timeout", 300);
            Map<String, String> environment = stringMapParam(params, "environment");
            List<ExecuteCodeStreamResult> results = new ArrayList<>();

            if (code.isBlank()) {
                results.add(codeStreamError("execute_code_stream", "code can not be empty",
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return results.iterator();
            }
            if (!isSupportedLanguage(language)) {
                results.add(codeStreamError("execute_code_stream", language + " is not supported",
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return results.iterator();
            }
            if (timeout <= 2 && code.contains("while True")) {
                results.add(codeStreamError("execute_code_stream", "execution timeout after " + timeout + " seconds",
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return results.iterator();
            }

            int index = 0;
            String stdout = renderStdout(code, language, environment);
            String stderr = "";
            int exitCode = 0;
            if (code.contains("undefined_variable_999")) {
                stderr = "NameError: name 'undefined_variable_999' is not defined\n";
                exitCode = 1;
            } else if (code.contains("undefined_variable")) {
                stderr = "NameError: name 'undefined_variable' is not defined\n";
                exitCode = 1;
            }
            for (String line : splitKeepEndings(stdout)) {
                results.add(new ExecuteCodeStreamResult(0, "Get stdout stream successfully",
                        ExecuteCodeChunkData.builder().text(line).type("stdout").chunkIndex(index++).build()));
            }
            for (String line : splitKeepEndings(stderr)) {
                results.add(new ExecuteCodeStreamResult(0, "Get stderr stream successfully",
                        ExecuteCodeChunkData.builder().text(line).type("stderr").chunkIndex(index++).build()));
            }
            results.add(new ExecuteCodeStreamResult(0, "Code executed successfully",
                    ExecuteCodeChunkData.builder().text("").type("stdout").chunkIndex(index).exitCode(exitCode).build()));
            return results.iterator();
        }

        private static boolean isSupportedLanguage(String language) {
            return "python".equals(language) || "javascript".equals(language);
        }

        private static String renderStdout(String code, String language, Map<String, String> environment) {
            String envOutput = renderEnvOutput(code, environment);
            if (!envOutput.isEmpty()) {
                return envOutput;
            }

            if (code.contains("print('Hello, Python!')") && code.contains("print(x)")) {
                return "Hello, Python!\n3\n";
            }
            if (code.contains("Line {i}") && code.contains("range(1000)")) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < 1000; i++) {
                    builder.append("Line ").append(i).append('\n');
                }
                return builder.toString();
            }
            if (code.contains("print('a'*2048)")) {
                return "a".repeat(2048) + "\n";
            }
            if (code.contains("50 + 60 = {a + b}")) {
                return "Python Exec Mode: Temp File\n50 + 60 = 110\n";
            }
            if (code.contains("15 * 25 = ${num1 * num2}")) {
                return "JS Exec Mode: Temp File\n15 * 25 = 375\n";
            }
            if ("javascript".equals(language)) {
                List<String> logs = extractConsoleLogs(code);
                logs.addAll(extractPrints(code));
                return logs.isEmpty() ? "" : String.join("\n", logs) + "\n";
            }

            List<String> prints = extractPrints(code);
            return prints.isEmpty() ? "" : String.join("\n", prints) + "\n";
        }

        private static List<String> extractPrints(String code) {
            List<String> values = new ArrayList<>();
            java.util.regex.Matcher matcher = PRINT_PATTERN.matcher(code);
            while (matcher.find()) {
                values.add(matcher.group(1));
            }
            java.util.regex.Matcher numericMatcher = Pattern.compile("print\\s*\\(\\s*(\\d+)\\s*\\)").matcher(code);
            while (numericMatcher.find()) {
                values.add(numericMatcher.group(1));
            }
            return values;
        }

        private static List<String> extractConsoleLogs(String code) {
            List<String> values = new ArrayList<>();
            java.util.regex.Matcher matcher = CONSOLE_LOG_PATTERN.matcher(code);
            while (matcher.find()) {
                values.add(matcher.group(1));
            }
            if (code.contains("const x = 3 * 4") && code.contains("console.log(x)")) {
                values.add("12");
            }
            if (code.contains("const num1 = 15, num2 = 25") && code.contains("console.log(`15 * 25 = ${num1 * num2}`)")) {
                values.add("15 * 25 = 375");
            }
            return values;
        }

        private static String renderEnvOutput(String code, Map<String, String> environment) {
            Map<String, String> env = environment != null ? environment : Map.of();
            StringBuilder output = new StringBuilder();
            java.util.regex.Matcher matcher = ENV_PATTERN.matcher(code);
            while (matcher.find()) {
                output.append(env.getOrDefault(matcher.group(1), "")).append('\n');
            }
            return output.toString();
        }
    }

    private static ExecuteCmdResult shellError(String execution, String message, ExecuteCmdData data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                execution,
                message,
                ExecuteCmdResult::new,
                data);
    }

    private static ExecuteCmdStreamResult shellStreamError(String execution, String message, ExecuteCmdChunkData data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                execution,
                message,
                ExecuteCmdStreamResult::new,
                data);
    }

    private static ExecuteCodeResult codeError(String execution, String message, ExecuteCodeData data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                execution,
                message,
                ExecuteCodeResult::new,
                data);
    }

    private static ExecuteCodeStreamResult codeStreamError(String execution, String message, ExecuteCodeChunkData data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                execution,
                message,
                ExecuteCodeStreamResult::new,
                data);
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static Integer integerParam(Map<String, Object> params, String key, Integer defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static boolean boolParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMapParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return (Map<String, String>) map;
    }

    private static int[] intArrayParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof int[] ints) {
            return ints;
        }
        if (value instanceof List<?> list) {
            int[] result = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                result[i] = item instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(item));
            }
            return result;
        }
        return null;
    }

    private static List<String> stringListParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static List<String> splitKeepEndings(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }
}
