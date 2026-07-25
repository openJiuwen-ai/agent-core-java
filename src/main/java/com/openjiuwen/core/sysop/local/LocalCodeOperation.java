/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.BaseResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local code execution operation using ProcessBuilder.
 * <p>
 * Mirrors Python's {@code CodeOperation} in {@code local/code_operation.py}.
 * Supports Python and JavaScript execution.
 * 
 * @since 0.1.7
 */
@Operation(name = "code", mode = OperationMode.LOCAL, description = "local code operation")
public class LocalCodeOperation extends BaseCodeOperation {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int WINDOWS_CMD_LIMIT = 8000;
    private static final int UNIX_CMD_LIMIT = 100000;

    /**
     * LocalCodeOperation.
     * 
     * @param runConfig runConfig
     * @since 0.1.7
     */
    public LocalCodeOperation(Object runConfig) {
        super("code", OperationMode.LOCAL, "local code operation", runConfig);
    }

    /**
     * executeCode.
     * 
     * @param code code
     * @param language language
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        String methodName = "executeCode";
        long startTime = System.currentTimeMillis();

        Loggers.SYS_OPERATION.info("Start to execute code");

        if (code == null || code.isBlank()) {
            return buildCodeErrorResult("code can not be empty", null);
        }

        if (!isSupportedLanguage(language)) {
            return buildCodeErrorResult(language + " is not supported",
                    ExecuteCodeData.builder().codeContent(code).language(language).build());
        }

        String codeFilePath = null;
        try {
            int effectiveTimeout = normalizeTimeoutSeconds(timeout);
            boolean forceFile = shouldForceFileExecution(language, code, options);
            String[] cmdAndPath = buildSubprocessCmd(code, language, forceFile);
            String[] cmd = parseCmd(cmdAndPath[0]);
            codeFilePath = cmdAndPath[1];

            if (cmd == null) {
                return buildCodeErrorResult("subprocess cmd can not be none",
                        ExecuteCodeData.builder().codeContent(code).language(language).build());
            }

            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            applyLanguageEnv(language, env);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().putAll(env);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String encoding =
                options != null && options.containsKey("encoding") ? (String) options.get("encoding") : "utf-8";
            Charset charset = Charset.forName(encoding);
            ProcessHandler handler = new ProcessHandler(process, 1024, charset, effectiveTimeout);
            InvokeData invokeData = handler.invoke();

            if (invokeData.getException() instanceof InterruptedException) {
                String tmpPath = codeFilePath;
                codeFilePath = null;
                if (tmpPath != null) {
                    OperationUtils.deleteTmpFile(tmpPath);
                }
                return buildCodeErrorResult("execution timeout after " + effectiveTimeout + " seconds",
                        ExecuteCodeData.builder().codeContent(code).language(language)
                                .exitCode(invokeData.getExitCode()).stdout(invokeData.getStdout())
                                .stderr(invokeData.getStderr()).build());
            }

            ExecuteCodeResult result =
                ExecuteCodeResult.builder().code(StatusCode.SUCCESS.getCode()).message("Code executed successfully")
                        .data(ExecuteCodeData.builder().codeContent(code).language(language)
                                .exitCode(invokeData.getExitCode()).stdout(invokeData.getStdout())
                                .stderr(invokeData.getStderr()).build())
                        .build();

            long elapsed = System.currentTimeMillis() - startTime;
            Loggers.SYS_OPERATION.info("End to execute code, elapsed={}ms", elapsed);
            return result;
        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to execute code", e);
            String msg = interpreterNotFoundMessage(language, e);
            return buildCodeErrorResult(msg, ExecuteCodeData.builder().codeContent(code).language(language).build());
        } finally {
            if (codeFilePath != null) {
                OperationUtils.deleteTmpFile(codeFilePath);
            }
        }
    }

    /**
     * executeCodeStream.
     * 
     * @param code code
     * @param language language
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        String methodName = "executeCodeStream";
        long startTime = System.currentTimeMillis();
        List<ExecuteCodeStreamResult> results = new ArrayList<>();

        Loggers.SYS_OPERATION.info("Start to execute code streaming");

        int chunkIndex = 0;
        if (code == null || code.isBlank()) {
            results.add(buildCodeStreamErrorResult("code can not be empty",
                    ExecuteCodeChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
            return results.iterator();
        }

        if (!isSupportedLanguage(language)) {
            results.add(buildCodeStreamErrorResult(language + " is not supported",
                    ExecuteCodeChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
            return results.iterator();
        }

        int effectiveTimeout = normalizeTimeoutSeconds(timeout);
        boolean forceFile = shouldForceFileExecution(language, code, options);
        String[] cmdAndPath = buildSubprocessCmd(code, language, forceFile);
        String[] cmd = parseCmd(cmdAndPath[0]);
        String codeFilePath = cmdAndPath[1];

        if (cmd == null) {
            results.add(buildCodeStreamErrorResult("subprocess cmd can not be none",
                    ExecuteCodeChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
            if (codeFilePath != null) {
                OperationUtils.deleteTmpFile(codeFilePath);
            }
            return results.iterator();
        }

        try {
            Map<String, String> env = OperationUtils.prepareEnvironment(environment);
            applyLanguageEnv(language, env);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().putAll(env);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            int chunkSize =
                options != null && options.containsKey("chunk_size") ? (Integer) options.get("chunk_size") : 1024;
            String encoding =
                options != null && options.containsKey("encoding") ? (String) options.get("encoding") : "utf-8";
            Charset charset = Charset.forName(encoding);
            ProcessHandler handler = new ProcessHandler(process, chunkSize, charset, effectiveTimeout);
            Iterator<StreamEvent> eventIterator = handler.stream();
            final int[] idx = {chunkIndex};

            while (eventIterator.hasNext()) {
                StreamEvent event = eventIterator.next();
                ExecuteCodeStreamResult transformed = transformCodeStreamEvent(event, idx[0]);
                if (transformed != null) {
                    results.add(transformed);
                    idx[0]++;
                }
                if (event.getType() == StreamEventType.ERROR || event.getType() == StreamEventType.EXIT) {
                    break;
                }
            }

            return results.iterator();
        } catch (IOException | RuntimeException e) {
            Loggers.SYS_OPERATION.error("Failed to execute code streaming", e);
            String msg = interpreterNotFoundMessage(language, e);
            results.add(buildCodeStreamErrorResult(msg,
                    ExecuteCodeChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build()));
            return results.iterator();
        } finally {
            if (codeFilePath != null) {
                OperationUtils.deleteTmpFile(codeFilePath);
            }
        }
    }

    // --- Private helpers ---

    /**
     * isSupportedLanguage.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private boolean isSupportedLanguage(String language) {
        return "python".equals(language) || "javascript".equals(language);
    }

    /**
     * applyLanguageEnv.
     * 
     * @param language language
     * @param env env
     * @since 0.1.7
     */
    private void applyLanguageEnv(String language, Map<String, String> env) {
        if ("javascript".equals(language)) {
            env.put("NODE_DISABLE_COLORS", "1");
        } else if ("python".equals(language)) {
            env.put("PYTHONIOENCODING", "utf-8");
            env.put("PYTHONUTF8", "1");
        } else {
            // no-op
        }
    }

    /**
     * getDefaultCmdLimit.
     * 
     * @return the result
     * @since 0.1.7
     */
    private int getDefaultCmdLimit() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? WINDOWS_CMD_LIMIT
                : UNIX_CMD_LIMIT;
    }

    /**
     * normalizeTimeoutSeconds.
     * 
     * @param timeout timeout
     * @return the result
     * @since 0.1.7
     */
    private int normalizeTimeoutSeconds(int timeout) {
        return timeout > 0 ? timeout : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * shouldForceFileExecution.
     * 
     * @param language language
     * @param code code
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private boolean shouldForceFileExecution(String language, String code, Map<String, Object> options) {
        if (options != null && Boolean.TRUE.equals(options.get("force_file"))) {
            return true;
        }
        return "python".equals(language) && isWindows();
    }

    /**
     * isWindows.
     * 
     * @return the result
     * @since 0.1.7
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Build subprocess command. Returns [cmdString, tempFilePath].
     * cmdString may be null if language unsupported or file creation fails.
     * tempFilePath is null if CLI mode is used.
     * 
     * @param code code
     * @param language language
     * @param forceFile forceFile
     * @return the result
     * @since 0.1.7
     */
    private String[] buildSubprocessCmd(String code, String language, boolean forceFile) {
        if (!isSupportedLanguage(language)) {
            return new String[]{null, null};
        }

        if (!forceFile && code.length() <= getDefaultCmdLimit()) {
            // CLI mode
            String cmdStr = buildCliCmd(language, code);
            return new String[]{cmdStr, null};
        }

        // File mode
        String suffix = "python".equals(language) ? ".py" : ".js";
        String tempPath = OperationUtils.createTmpFile(code, suffix);
        if (tempPath == null) {
            return new String[]{null, null};
        }
        String cmdStr = buildFileCmd(language, tempPath);
        return new String[]{cmdStr, tempPath};
    }

    /**
     * buildCliCmd.
     *
     * @param language language
     * @param code code
     * @return the result
     * @since 0.1.7
     */
    private String buildCliCmd(String language, String code) {
        if ("python".equals(language)) {
            return pythonCommand() + "\0-u\0-c\0" + code;
        } else {
            return "node\0-e\0" + code;
        }
    }

    /**
     * buildFileCmd.
     * 
     * @param language language
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    private String buildFileCmd(String language, String filePath) {
        if ("python".equals(language)) {
            return pythonCommand() + "\0-u\0" + filePath;
        } else {
            return "node\0" + filePath;
        }
    }

    /**
     * Resolves the Python executable: {@code PYTHON_EXECUTABLE} override, else first candidate on PATH
     * ({@code python3}/{@code python}, plus {@code python.exe} on Windows), else a platform default name.
     *
     * @return command name or absolute path suitable for ProcessBuilder
     * @since 0.1.7
     */
    private String pythonCommand() {
        String override = System.getenv("PYTHON_EXECUTABLE");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String[] candidates = isWindows()
                ? new String[] {"python", "python3", "python.exe"}
                : new String[] {"python3", "python"};
        for (String candidate : candidates) {
            if (isExecutableOnPath(candidate)) {
                return candidate;
            }
        }
        return isWindows() ? "python" : "python3";
    }

    /**
     * Builds a user-facing message when launching an interpreter fails because it is missing from PATH.
     *
     * @param language language key (e.g. {@code python})
     * @param e launch failure
     * @return guidance string for PATH / {@code PYTHON_EXECUTABLE}, or a generic unexpected-error message
     * @since 0.1.14
     */
    private String interpreterNotFoundMessage(String language, Exception e) {
        if (e instanceof IOException && e.getMessage() != null && e.getMessage().contains("Cannot run program")) {
            if ("python".equals(language)) {
                return "Python interpreter not found on PATH (tried: python3, python"
                        + (isWindows() ? ", python.exe" : "")
                        + "); set PYTHON_EXECUTABLE or install and add it to PATH.";
            }
            return language + " interpreter not found, please install and add it to your "
                    + "system environment variable PATH.";
        }
        return "unexpected error: " + e.getMessage();
    }

    /**
     * isExecutableOnPath.
     *
     * @param candidate executable name
     * @return true when the executable exists on PATH
     * @since 0.1.7
     */
    private boolean isExecutableOnPath(String candidate) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            java.io.File file = new java.io.File(dir, candidate);
            if (file.isFile() && (isWindows() || file.canExecute())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse null-separated command string back to array
     * 
     * @param cmdStr cmdStr
     * @return the result
     * @since 0.1.7
     */
    private String[] parseCmd(String cmdStr) {
        if (cmdStr == null) {
            return new String[0];
        }
        return cmdStr.split("\0");
    }

    /**
     * buildCodeErrorResult.
     * 
     * @param errorMsg errorMsg
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCodeResult buildCodeErrorResult(String errorMsg, ExecuteCodeData data) {
        if (data != null && (data.getExitCode() == null || data.getExitCode() == 0)) {
            data.setExitCode(-1);
        }
        return BaseResult.buildOperationErrorResult(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR, "execute_code",
                errorMsg, ExecuteCodeResult::new, data);
    }

    /**
     * buildCodeStreamErrorResult.
     * 
     * @param errorMsg errorMsg
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCodeStreamResult buildCodeStreamErrorResult(String errorMsg, ExecuteCodeChunkData data) {
        if (data != null && (data.getExitCode() == null || data.getExitCode() == 0)) {
            data.setExitCode(-1);
        }
        return BaseResult.buildOperationErrorResult(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                "execute_code_stream", errorMsg, ExecuteCodeStreamResult::new, data);
    }

    /**
     * transformCodeStreamEvent.
     * 
     * @param event event
     * @param chunkIndex chunkIndex
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCodeStreamResult transformCodeStreamEvent(StreamEvent event, int chunkIndex) {
        return switch (event.getType()) {
            case STDOUT, STDERR -> {
                ExecuteCodeChunkData chunkData = ExecuteCodeChunkData.builder().text(event.getDataAsString())
                        .type(event.getType().getValue()).chunkIndex(chunkIndex).build();
                yield ExecuteCodeStreamResult.builder().code(StatusCode.SUCCESS.getCode())
                        .message("Get " + chunkData.getType() + " stream successfully").data(chunkData).build();
            }
            case ERROR -> buildCodeStreamErrorResult("execution receive error: " + event.getDataAsString(),
                    ExecuteCodeChunkData.builder().chunkIndex(chunkIndex).exitCode(-1).build());
            case EXIT -> {
                Integer exitCode = event.getDataAsInt();
                if (exitCode == null) {
                    exitCode = -1;
                }
                ExecuteCodeChunkData chunkData =
                    ExecuteCodeChunkData.builder().chunkIndex(chunkIndex).exitCode(exitCode).build();
                yield ExecuteCodeStreamResult.builder().code(StatusCode.SUCCESS.getCode())
                        .message("Code executed successfully").data(chunkData).build();
            }
        };
    }
}
