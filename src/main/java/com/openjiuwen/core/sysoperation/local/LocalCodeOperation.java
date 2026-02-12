// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.code.BaseCodeOperation;
import com.openjiuwen.core.sysoperation.registry.Operation;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import com.openjiuwen.core.sysoperation.result.Language;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeData;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeResult;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeStreamResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Local code execution operation implementation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.local.code_operation.CodeOperation
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Operation(name = "code", mode = OperationMode.LOCAL, description = "local code operation")
public class LocalCodeOperation extends BaseCodeOperation {

    static {
        OperationRegistry.register(LocalCodeOperation.class, "code", OperationMode.LOCAL, "local code operation");
    }

    public LocalCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ExecuteCodeResult> executeCode(
            String code, Language language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            // Validate code
            if (code == null || code.trim().isEmpty()) {
                return ExecuteCodeResult.failure(
                    StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(),
                    formatCodeError("execute_code", "code can not be empty")
                );
            }

            // Validate language
            Language lang = language != null ? language : Language.PYTHON;
            List<String> cmd = buildCommand(lang, code);
            if (cmd == null) {
                return new ExecuteCodeResult(
                    StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(),
                    formatCodeError("execute_code", lang + " is not supported"),
                    ExecuteCodeData.builder()
                        .codeContent(code)
                        .language(lang.getValue())
                        .build()
                );
            }

            try {
                // Build process
                ProcessBuilder pb = new ProcessBuilder(cmd);
                Map<String, String> env = pb.environment();
                
                // Set PYTHONIOENCODING to ensure UTF-8 output on Windows
                env.put("PYTHONIOENCODING", "utf-8");
                
                if (environment != null) {
                    env.putAll(environment);
                }

                Process process = pb.start();

                // Read output with UTF-8 encoding
                StringBuilder stdout = new StringBuilder();
                StringBuilder stderr = new StringBuilder();

                Thread stdoutThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stdout.append(line).append("\n");
                        }
                    } catch (Exception ignored) {}
                });

                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stderr.append(line).append("\n");
                        }
                    } catch (Exception ignored) {}
                });

                stdoutThread.start();
                stderrThread.start();

                // Wait with timeout
                boolean finished = process.waitFor(timeout > 0 ? timeout : DEFAULT_TIMEOUT, TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    process.waitFor();
                    String errorMsg = "execution timeout after " + timeout + " seconds";
                    return new ExecuteCodeResult(
                        StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(),
                        formatCodeError("execute_code", errorMsg),
                        ExecuteCodeData.builder()
                            .codeContent(code)
                            .language(lang.getValue())
                            .exitCode(-1)
                            .stdout("")
                            .stderr(errorMsg)
                            .build()
                    );
                }

                stdoutThread.join(1000);
                stderrThread.join(1000);

                int exitCode = process.exitValue();
                String stdoutStr = stdout.toString();
                String stderrStr = stderr.toString();

                // Remove trailing newline
                if (stdoutStr.endsWith("\n")) {
                    stdoutStr = stdoutStr.substring(0, stdoutStr.length() - 1);
                }
                if (stderrStr.endsWith("\n")) {
                    stderrStr = stderrStr.substring(0, stderrStr.length() - 1);
                }

                ExecuteCodeData data = ExecuteCodeData.builder()
                    .codeContent(code)
                    .language(lang.getValue())
                    .exitCode(exitCode)
                    .stdout(stdoutStr)
                    .stderr(stderrStr)
                    .build();

                if (exitCode == 0) {
                    return new ExecuteCodeResult(
                        StatusCode.SUCCESS.getCode(),
                        "Code executed successfully",
                        data
                    );
                } else {
                    return new ExecuteCodeResult(
                        StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(),
                        formatCodeError("execute_code", 
                            "execution failed with exit code " + exitCode + ", stderr " + stderrStr),
                        data
                    );
                }

            } catch (Exception e) {
                String errorMsg = "unexpected error: " + e.getMessage();
                return new ExecuteCodeResult(
                    StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode(),
                    formatCodeError("execute_code", errorMsg),
                    ExecuteCodeData.builder()
                        .codeContent(code)
                        .language(lang.getValue())
                        .exitCode(-1)
                        .stdout("")
                        .stderr(errorMsg)
                        .build()
                );
            }
        });
    }

    @Override
    public Stream<ExecuteCodeStreamResult> executeCodeStream(
            String code, Language language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        // Simplified: delegate to non-streaming version
        throw new UnsupportedOperationException("executeCodeStream not implemented");
    }

    private List<String> buildCommand(Language language, String code) {
        List<String> cmd = new ArrayList<>();
        
        switch (language) {
            case PYTHON:
                // Try python3 first, then python
                String pythonCmd = findExecutable("python3", "python");
                if (pythonCmd == null) {
                    return null;
                }
                cmd.add(pythonCmd);
                cmd.add("-c");
                cmd.add(code);
                break;
            case JAVASCRIPT:
                cmd.add("node");
                cmd.add("-e");
                cmd.add(code);
                break;
            default:
                return null;
        }
        
        return cmd;
    }

    private String findExecutable(String... names) {
        for (String name : names) {
            try {
                ProcessBuilder pb = new ProcessBuilder(name, "--version");
                Process p = pb.start();
                p.waitFor(2, TimeUnit.SECONDS);
                if (p.exitValue() == 0) {
                    return name;
                }
            } catch (Exception ignored) {}
        }
        return names.length > 0 ? names[names.length - 1] : null;
    }

    private String formatCodeError(String operation, String message) {
        return String.format("[sys_operation][%s] execution error: %s", operation, message);
    }
}

