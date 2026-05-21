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
 * JiuwenBox sandbox provider implementation.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox}.
 *
 * Provides file system and shell operations via JiuwenBox HTTP gateway.
 */
public class JiuwenBoxProvider implements BaseShellProvider, BaseFSProvider, BaseCodeProvider {

    private final String endpointUrl;
    private final String apiKey;
    private final int timeoutSeconds;

    /**
     * Create JiuwenBoxProvider with endpoint URL.
     *
     * @param endpointUrl JiuwenBox gateway endpoint URL.
     * @param apiKey API key for authentication.
     */
    public JiuwenBoxProvider(String endpointUrl, String apiKey) {
        this(endpointUrl, apiKey, 30);
    }

    /**
     * Create JiuwenBoxProvider with endpoint URL and timeout.
     *
     * @param endpointUrl JiuwenBox gateway endpoint URL.
     * @param apiKey API key for authentication.
     * @param timeoutSeconds Request timeout in seconds.
     */
    public JiuwenBoxProvider(String endpointUrl, String apiKey, int timeoutSeconds) {
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
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
    public CompletableFuture<AioProvider.ExecuteResult> executeCommand(String command, String workingDir) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cmd", command);
        payload.put("cwd", workingDir);

        return executeRequest("/api/v1/shell/exec", payload)
            .thenApply(this::parseExecuteResult);
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
        payload.put("path", filePath);

        return executeRequest("/api/v1/fs/read", payload)
            .thenApply(response -> {
                String encoded = (String) response.get("content");
                if (encoded != null) {
                    return Base64.getDecoder().decode(encoded).toString();
                }
                return "";
            });
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
        payload.put("path", filePath);
        payload.put("content", Base64.getEncoder().encodeToString(content.getBytes()));
        payload.put("mode", "overwrite");

        return executeRequest("/api/v1/fs/write", payload)
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
        payload.put("path", dirPath);
        payload.put("recursive", false);

        return executeRequest("/api/v1/fs/list", payload)
            .thenApply(response -> {
                Object files = response.get("files");
                if (files instanceof List) {
                    return (List<String>) files;
                }
                return List.of();
            });
    }

    /**
     * Execute code in sandbox.
     *
     * @param code Code to execute.
     * @param language Programming language.
     * @return CompletableFuture with execution result.
     */
    @Override
    public CompletableFuture<AioProvider.ExecuteResult> executeCode(String code, String language) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", Base64.getEncoder().encodeToString(code.getBytes()));
        payload.put("lang", language);
        payload.put("timeout", timeoutSeconds);

        return executeRequest("/api/v1/code/run", payload)
            .thenApply(this::parseExecuteResult);
    }

    /**
     * Execute HTTP request to JiuwenBox gateway.
     *
     * @param path API path.
     * @param payload Request payload.
     * @return CompletableFuture with response.
     */
    private CompletableFuture<Map<String, Object>> executeRequest(String path, Map<String, Object> payload) {
        // Placeholder for HTTP client implementation with authentication
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    /**
     * Parse execute result from response.
     *
     * @param response HTTP response.
     * @return Execute result.
     */
    private AioProvider.ExecuteResult parseExecuteResult(Map<String, Object> response) {
        String stdout = (String) response.getOrDefault("stdout", "");
        String stderr = (String) response.getOrDefault("stderr", "");
        int exitCode = (Integer) response.getOrDefault("exit_code", 0);
        return new AioProvider.ExecuteResult(stdout, stderr, exitCode);
    }
}