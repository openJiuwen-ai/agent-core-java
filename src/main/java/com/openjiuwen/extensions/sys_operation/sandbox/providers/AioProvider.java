/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async HTTP sandbox provider implementation.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.sys_operation.sandbox.providers.aio}.
 *
 * Provides async file system and shell operations via HTTP gateway.
 */
public class AioProvider implements BaseShellProvider, BaseFSProvider, BaseCodeProvider {

    private final String endpointUrl;
    private final int timeoutSeconds;

    /**
     * Create AioProvider with endpoint URL.
     *
     * @param endpointUrl HTTP gateway endpoint URL.
     */
    public AioProvider(String endpointUrl) {
        this(endpointUrl, 30);
    }

    /**
     * Create AioProvider with endpoint URL and timeout.
     *
     * @param endpointUrl HTTP gateway endpoint URL.
     * @param timeoutSeconds Request timeout in seconds.
     */
    public AioProvider(String endpointUrl, int timeoutSeconds) {
        this.endpointUrl = endpointUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Execute a shell command.
     *
     * @param command Command to execute.
     * @param workingDir Working directory.
     * @return CompletableFuture with execution result.
     */
    @Override
    public CompletableFuture<ExecuteResult> executeCommand(String command, String workingDir) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("command", command);
        payload.put("working_dir", workingDir);

        return executeRequest("/shell/exec", payload)
            .thenApply(response -> parseExecuteResult(response));
    }

    /**
     * Read file content.
     *
     * @param filePath File path to read.
     * @return CompletableFuture with file content.
     */
    @Override
    public CompletableFuture<String> readFile(String filePath) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("file_path", filePath);

        return executeRequest("/fs/read", payload)
            .thenApply(response -> (String) response.get("content"));
    }

    /**
     * Write file content.
     *
     * @param filePath File path to write.
     * @param content Content to write.
     * @return CompletableFuture for async operation.
     */
    @Override
    public CompletableFuture<Void> writeFile(String filePath, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("file_path", filePath);
        payload.put("content", Base64.getEncoder().encodeToString(content.getBytes()));

        return executeRequest("/fs/write", payload)
            .thenApply(response -> null);
    }

    /**
     * List files in directory.
     *
     * @param dirPath Directory path.
     * @return CompletableFuture with file list.
     */
    @Override
    public CompletableFuture<List<String>> listFiles(String dirPath) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("dir_path", dirPath);

        return executeRequest("/fs/list", payload)
            .thenApply(response -> (List<String>) response.get("files"));
    }

    /**
     * Execute code in sandbox.
     *
     * @param code Code to execute.
     * @param language Programming language.
     * @return CompletableFuture with execution result.
     */
    @Override
    public CompletableFuture<ExecuteResult> executeCode(String code, String language) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", Base64.getEncoder().encodeToString(code.getBytes()));
        payload.put("language", language);

        return executeRequest("/code/exec", payload)
            .thenApply(response -> parseExecuteResult(response));
    }

    /**
     * Execute HTTP request to sandbox gateway.
     *
     * @param path API path.
     * @param payload Request payload.
     * @return CompletableFuture with response.
     */
    private CompletableFuture<Map<String, Object>> executeRequest(String path, Map<String, Object> payload) {
        // Placeholder for HTTP client implementation
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    /**
     * Parse execute result from response.
     *
     * @param response HTTP response.
     * @return Execute result.
     */
    private ExecuteResult parseExecuteResult(Map<String, Object> response) {
        String stdout = (String) response.getOrDefault("stdout", "");
        String stderr = (String) response.getOrDefault("stderr", "");
        int exitCode = (int) response.getOrDefault("exit_code", 0);
        return new ExecuteResult(stdout, stderr, exitCode);
    }

    /** Execution result container. */
    public static class ExecuteResult {
        private final String stdout;
        private final String stderr;
        private final int exitCode;

        public ExecuteResult(String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }

        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public int getExitCode() { return exitCode; }
        public boolean isSuccess() { return exitCode == 0; }
    }
}