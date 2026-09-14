/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.core.utils.LspConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal single-server lifecycle manager for the Java harness LSP runtime.
 * <p>
 * Mirrors Python's {@code LSPServerInstance} in
 * {@code openjiuwen/harness/lsp/core/instance.py}.
 * </p>
 */
public class LspServerInstance {

    private final ScopedLspServerConfig config;
    private final Consumer<Exception> onError;
    private LspServerState state = LspServerState.STOPPED;
    private int crashCount;
    private Exception lastError;
    private LspClient client;

    public LspServerInstance(ScopedLspServerConfig config, Consumer<Exception> onError) {
        this.config = config;
        this.onError = onError;
    }

    public String getName() {
        return config.getServerId();
    }

    public ScopedLspServerConfig getConfig() {
        return config;
    }

    public LspServerState getState() {
        return state;
    }

    public boolean isRunning() {
        return state == LspServerState.RUNNING;
    }

    public int getCrashCount() {
        return crashCount;
    }

    public Exception getLastError() {
        return lastError;
    }

    public Map<String, Object> start() {
        if (state == LspServerState.STARTING || state == LspServerState.RUNNING) {
            return client != null ? client.getCapabilities() : Map.of();
        }
        if (state == LspServerState.ERROR && crashCount >= LspConstants.MAX_CRASH_RECOVERY_ATTEMPTS) {
            throw new RuntimeException(
                    "Server '" + config.getServerId()
                            + "' exceeded max crash recovery attempts; last error: "
                            + lastError
            );
        }
        try {
            state = LspServerState.STARTING;
            Process process = startProcess();
            client = new LspClient(config, process, this::handleExit);
            Map<String, Object> capabilities = client.initialize().join();
            state = LspServerState.RUNNING;
            crashCount = 0;
            lastError = null;
            return capabilities;
        } catch (Exception exception) {
            state = LspServerState.ERROR;
            lastError = exception;
            crashCount++;
            if (client != null) {
                try {
                    client.stop().join();
                } catch (Exception ignored) {
                    // Ignore cleanup failures while surfacing the original start error.
                }
                client = null;
            }
            if (onError != null) {
                onError.accept(exception);
            }
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(exception);
        }
    }

    public void stop() {
        state = LspServerState.STOPPING;
        if (client != null) {
            client.stop().join();
            client = null;
        }
        state = LspServerState.STOPPED;
    }

    public boolean isHealthy() {
        return state == LspServerState.RUNNING && client != null && client.isAlive();
    }

    public Object sendRequest(String method, Object params) {
        if (client == null) {
            throw new LspError(-32001, "server not started");
        }
        return client.sendRequest(method, params).join();
    }

    public void sendNotification(String method, Object params) {
        if (client == null) {
            return;
        }
        client.sendNotification(method, params).join();
    }

    public void addNotificationHandler(String method, java.util.function.Consumer<Object> handler) {
        if (client != null) {
            client.addNotificationHandler(method, handler);
        }
    }

    public Map<String, Object> getCapabilities() {
        return client != null ? client.getCapabilities() : Map.of();
    }

    private Process startProcess() {
        try {
            List<String> command = new ArrayList<>();
            if (config.getCommand() == null || config.getCommand().isBlank()) {
                throw new IOException("LSP command is empty for server: " + config.getServerId());
            }
            command.add(config.getCommand());
            if (config.getArgs() != null && !config.getArgs().isEmpty()) {
                command.addAll(config.getArgs());
            }
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (config.getWorkspaceFolder() != null && !config.getWorkspaceFolder().isBlank()) {
                processBuilder.directory(new File(config.getWorkspaceFolder()));
            }
            if (config.getEnv() != null && !config.getEnv().isEmpty()) {
                processBuilder.environment().putAll(config.getEnv());
            }
            return processBuilder.start();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to start LSP server '" + config.getServerId() + "'", exception);
        }
    }

    private void handleExit(Integer code) {
        if (state == LspServerState.STOPPING || state == LspServerState.STOPPED) {
            return;
        }
        state = LspServerState.ERROR;
        crashCount++;
        lastError = new RuntimeException("Server '" + config.getServerId() + "' exited with code " + code);
        if (onError != null) {
            onError.accept(lastError);
        }
    }
}
