/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Minimal local code execution tool.
 * 
 * @since 0.1.7
 */
public class CodeTool {
    /**
     * invoke.
     * 
     * @param code code
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(String code, String language) {
        if (code == null) {
            return ToolOutput.builder().success(false).error("code is required").build();
        }
        String normalized = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (!List.of("python", "python3", "bash", "sh").contains(normalized)) {
            return ToolOutput.builder().success(false).error("unsupported language: " + language).build();
        }

        List<String> command = switch (normalized) {
            case "python", "python3" -> List.of("python3", "-c", code);
            case "bash" -> List.of("bash", "-lc", code);
            default -> List.of("sh", "-c", code);
        };

        try {
            ExecutorService processIoExecutor = OpenJiuwenExecutors.newFixedThreadPool("harness-code-process-io", 2,
                    true);
            try {
                Process process = new ProcessBuilder(command).start();
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                        () -> read(process.getInputStream()), processIoExecutor);
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                        () -> read(process.getErrorStream()), processIoExecutor);
                int exitCode = process.onExit().join().exitValue();
                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join();
                boolean isExecutionSuccessful = exitCode == 0;
                return ToolOutput.builder().success(isExecutionSuccessful)
                        .data(Map.of("exit_code", exitCode, "stdout", stdout, "stderr", stderr))
                        .error(isExecutionSuccessful
                                ? null
                                : (stderr.isBlank() ? "process exited with code " + exitCode : stderr))
                        .build();
            } finally {
                processIoExecutor.shutdownNow();
            }
        } catch (IOException | SecurityException | CompletionException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * read.
     * 
     * @param stream stream
     * @return the result
     * @since 0.1.7
     */
    private static String read(InputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            stream.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
