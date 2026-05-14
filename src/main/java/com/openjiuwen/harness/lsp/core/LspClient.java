/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.lsp.core.utils.LspConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Minimal JSON-RPC client for a Java harness LSP server process.
 *
 * <p>Mirrors Python's {@code LSPClient} in {@code openjiuwen.harness.lsp.core.client}.
 * This port provides a minimal subprocess JSON-RPC runtime skeleton.
 */
public class LspClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_LENGTH = "Content-Length";

    private final ScopedLspServerConfig config;
    private final Process process;
    private final Consumer<Integer> onExit;
    private final Map<String, Object> capabilities = new LinkedHashMap<>();
    private final Map<String, List<Consumer<Object>>> notificationHandlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private final ExecutorService readerExecutor;
    private final OutputStream stdin;
    private final InputStream stdout;
    private volatile boolean initialized;
    private volatile boolean alive = true;
    private volatile boolean stopping;

    public LspClient(ScopedLspServerConfig config, Process process, Consumer<Integer> onExit) {
        this.config = config;
        this.process = process;
        this.onExit = onExit;
        this.stdin = process.getOutputStream();
        this.stdout = process.getInputStream();
        this.readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lsp-client-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<String, Object> initialize() {
        readerExecutor.submit(this::readLoop);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("processId", ProcessHandle.current().pid());
        params.put("rootUri", FileUriUtils.pathToFileUri(config.getWorkspaceFolder()));
        params.put("workspaceFolders", List.of(Map.of(
                "uri", FileUriUtils.pathToFileUri(config.getWorkspaceFolder()),
                "name", "workspace"
        )));
        params.put("capabilities", Map.of(
                "window", Map.of("workDoneProgress", true),
                "workspace", Map.of(
                        "applyEdit", true,
                        "workspaceEdit", Map.of("documentChanges", true),
                        "workspaceFolders", true,
                        "configuration", true
                ),
                "textDocument", Map.of(
                        "synchronization", Map.of(
                                "didOpen", true,
                                "didChange", Map.of("willSave", true, "willSaveWaitUntil", true, "save", true)
                        ),
                        "publishDiagnostics", Map.of("versionSupport", true)
                )
        ));
        if (config.getInitializationOptions() != null && !config.getInitializationOptions().isEmpty()) {
            params.put("initializationOptions", config.getInitializationOptions());
        }

        Object result = sendRequestInternal("initialize", params);
        Map<String, Object> resultMap = asMap(result);
        capabilities.clear();
        capabilities.putAll(asMap(resultMap.get("capabilities")));

        sendNotificationInternal("initialized", Map.of());
        sendNotificationInternal("workspace/didChangeConfiguration", Map.of(
                "settings", config.getInitializationOptions() != null ? config.getInitializationOptions() : Map.of()
        ));
        initialized = true;
        return new LinkedHashMap<>(capabilities);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isAlive() {
        return alive && process.isAlive();
    }

    public Map<String, Object> getCapabilities() {
        return new LinkedHashMap<>(capabilities);
    }

    public void addNotificationHandler(String method, Consumer<Object> handler) {
        if (method == null || method.isBlank() || handler == null) {
            return;
        }
        notificationHandlers.computeIfAbsent(method, ignored -> new ArrayList<>()).add(handler);
    }

    public Object sendRequest(String method, Object params) {
        if (!initialized) {
            throw new LspError(-32002, "client not initialized");
        }
        return sendRequestInternal(method, params);
    }

    public void sendNotification(String method, Object params) {
        if (!isAlive()) {
            return;
        }
        sendNotificationInternal(method, params);
    }

    public void stop() {
        stopping = true;
        try {
            if (initialized && isAlive()) {
                try {
                    sendRequestInternal("shutdown", Map.of());
                } catch (Exception ignored) {
                }
                try {
                    sendNotificationInternal("exit", Map.of());
                } catch (Exception ignored) {
                }
            }
        } finally {
            alive = false;
            initialized = false;
            pending.values().forEach(future -> future.completeExceptionally(new LspError(-32000, "client stopped")));
            pending.clear();
            try {
                stdin.close();
            } catch (IOException ignored) {
            }
            try {
                stdout.close();
            } catch (IOException ignored) {
            }
            process.destroy();
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            readerExecutor.shutdownNow();
            if (onExit != null) {
                int code;
                try {
                    code = process.exitValue();
                } catch (IllegalThreadStateException ignored) {
                    code = -1;
                }
                onExit.accept(code);
            }
        }
    }

    public ScopedLspServerConfig getConfig() {
        return config;
    }

    private void readLoop() {
        try {
            while (isAlive() && !stopping) {
                Map<String, Object> message = readMessage();
                if (message == null) {
                    break;
                }
                dispatch(message);
            }
        } catch (Exception e) {
            if (!stopping) {
                failPending(e);
            }
        } finally {
            alive = false;
            if (!stopping) {
                failPending(new LspError(-32099, "LSP reader loop stopped"));
            }
            if (onExit != null) {
                int code;
                try {
                    code = process.exitValue();
                } catch (IllegalThreadStateException ignored) {
                    code = -1;
                }
                onExit.accept(code);
            }
        }
    }

    private Object sendRequestInternal(String method, Object params) {
        String id = UUID.randomUUID().toString();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(id, future);
        writeMessage(Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params != null ? params : Map.of()
        ));
        try {
            return future.get(LspConstants.DEFAULT_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LspError(-32098, "request interrupted: " + method);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new LspError(-32097, "request timed out: " + method);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new LspError(-32096, cause != null ? cause.getMessage() : "request failed");
        }
    }

    private void sendNotificationInternal(String method, Object params) {
        writeMessage(Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", params != null ? params : Map.of()
        ));
    }

    private void writeMessage(Map<String, Object> message) {
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(message);
            String header = CONTENT_LENGTH + ": " + body.length + "\r\n\r\n";
            synchronized (stdin) {
                stdin.write(header.getBytes(StandardCharsets.US_ASCII));
                stdin.write(body);
                stdin.flush();
            }
        } catch (IOException e) {
            throw new LspError(-32095, "failed to write RPC message: " + e.getMessage());
        }
    }

    private Map<String, Object> readMessage() throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        if (line == null) {
            return null;
        }
        int length;
        try {
            length = Integer.parseInt(headers.getOrDefault(CONTENT_LENGTH, "0"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (length <= 0) {
            return null;
        }
        byte[] body = stdout.readNBytes(length);
        if (body.length != length) {
            return null;
        }
        return OBJECT_MAPPER.readValue(body, new TypeReference<>() { });
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = stdout.read();
            if (current < 0) {
                return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.US_ASCII);
            }
            if (previous == '\r' && current == '\n') {
                byte[] data = buffer.toByteArray();
                return new String(data, 0, Math.max(0, data.length - 1), StandardCharsets.US_ASCII);
            }
            buffer.write(current);
            previous = current;
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatch(Map<String, Object> message) {
        Object id = message.get("id");
        Object method = message.get("method");
        if (id != null && method == null) {
            CompletableFuture<Object> future = pending.remove(String.valueOf(id));
            if (future == null) {
                return;
            }
            if (message.containsKey("error")) {
                Map<String, Object> error = asMap(message.get("error"));
                future.completeExceptionally(new LspError(asInt(error.get("code"), -32094), String.valueOf(error.getOrDefault("message", "unknown error"))));
            } else {
                future.complete(message.get("result"));
            }
            return;
        }
        if (method != null) {
            for (Consumer<Object> handler : notificationHandlers.getOrDefault(String.valueOf(method), List.of())) {
                handler.accept(message.get("params"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void failPending(Exception exception) {
        pending.values().forEach(future -> future.completeExceptionally(exception));
        pending.clear();
    }
}
