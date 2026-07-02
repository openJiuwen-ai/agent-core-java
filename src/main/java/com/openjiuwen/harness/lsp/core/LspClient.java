/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.core.common.VirtualThreadSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * JSON-RPC communication layer for a single LSP server subprocess.
 *
 * <p>Mirrors Python's {@code LSPClient} in
 * {@code openjiuwen/harness/lsp/core/client.py}.</p>
 */
public class LspClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final LoggerProtocol LOGGER = Loggers.TOOL;

    private final ScopedLspServerConfig config;
    private final Process process;
    private final Consumer<Integer> onExitCallback;
    private final ExecutorService ioExecutor;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Object>>> notificationHandlers = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final AtomicBoolean crashReported = new AtomicBoolean(false);

    private volatile Map<String, Object> capabilities;
    private volatile boolean isInitialized;
    private volatile boolean isStopping;
    private volatile Future<?> readerTask;
    private volatile Future<?> stderrTask;

    public LspClient(ScopedLspServerConfig config, Process process, Consumer<Integer> onExit) {
        this.config = Objects.requireNonNull(config, "config");
        this.process = Objects.requireNonNull(process, "process");
        this.onExitCallback = onExit;
        this.ioExecutor = VirtualThreadSupport.newThreadPerTaskExecutor("lsp-client-io");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("lsp-client-timer"));
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public CompletableFuture<Map<String, Object>> initialize() {
        readerTask = ioExecutor.submit(this::readLoop);
        Map<String, Object> params = buildInitializeParams();
        return rpcRequest("initialize", params)
                .thenCompose(resultObject -> {
                    Map<String, Object> result = asMap(resultObject);
                    capabilities = asMap(result.getOrDefault("capabilities", Map.of()));
                    return rpcNotification("initialized", Map.of())
                            .thenCompose(ignored -> rpcNotification(
                                    "workspace/didChangeConfiguration",
                                    Map.of("settings", config.getInitializationOptions() == null
                                            ? Map.of()
                                            : config.getInitializationOptions())))
                            .thenCompose(ignored -> delay(100))
                            .thenApply(ignored -> {
                                isInitialized = true;
                                return result;
                            });
                });
    }

    public void addNotificationHandler(String method, Consumer<Object> handler) {
        notificationHandlers.computeIfAbsent(method, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public CompletableFuture<Object> sendRequest(String method, Object params) {
        if (!isInitialized) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client not initialized"));
        }
        return rpcRequest(method, params);
    }

    public CompletableFuture<Void> sendNotification(String method, Object params) {
        if (!isInitialized) {
            return CompletableFuture.completedFuture(null);
        }
        return rpcNotification(method, params);
    }

    public CompletableFuture<Void> stop() {
        isStopping = true;
        CompletableFuture<Void> shutdownFuture = rpcRequest("shutdown", Map.of())
                .orTimeout(2, TimeUnit.SECONDS)
                .handle((ignored, error) -> {
                    if (error != null) {
                        LOGGER.debug("LSP shutdown request failed (ignored during stop): %s", error.getMessage());
                    }
                    return null;
                })
                .thenCompose(ignored -> rpcNotification("exit", Map.of())
                        .exceptionally(error -> {
                            LOGGER.debug("LSP exit notification failed (ignored during stop): %s", error.getMessage());
                            return null;
                        }));

        return shutdownFuture.handle((ignored, error) -> null).thenRun(this::cleanupAfterStop);
    }

    private void cleanupAfterStop() {
        cancelFuture(readerTask);
        cancelFuture(stderrTask);
        closeQuietly(process.getOutputStream(), "stdin");
        closeQuietly(process.getInputStream(), "stdout");
        closeQuietly(process.getErrorStream(), "stderr");
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        completePending(new IllegalStateException("LSP client stopped"));
        pending.clear();
        isInitialized = false;
        capabilities = null;
        ioExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    private void readLoop() {
        LOGGER.debug("[readLoop] Starting read loop");
        InputStream reader = process.getInputStream();
        InputStream stderrReader = process.getErrorStream();
        stderrTask = ioExecutor.submit(() -> consumeStderrForever(stderrReader));
        try {
            while (true) {
                Map<String, String> headers = new LinkedHashMap<>();
                while (true) {
                    String line = readAsciiLine(reader);
                    if (line == null) {
                        return;
                    }
                    if (line.isEmpty()) {
                        break;
                    }
                    int separator = line.indexOf(": ");
                    if (separator >= 0) {
                        String key = line.substring(0, separator).trim();
                        String value = line.substring(separator + 2).trim();
                        headers.put(key, value);
                    }
                }

                int contentLength;
                try {
                    contentLength = Integer.parseInt(headers.getOrDefault(CONTENT_LENGTH, ""));
                } catch (NumberFormatException ignored) {
                    continue;
                }

                if (contentLength <= 0) {
                    continue;
                }

                byte[] body = readExact(reader, contentLength);
                String bodyText = new String(body, StandardCharsets.UTF_8).replaceFirst("^[\\r\\n]+", "");
                if (bodyText.isBlank()) {
                    continue;
                }

                Map<String, Object> message;
                try {
                    message = OBJECT_MAPPER.readValue(bodyText, MAP_TYPE);
                } catch (IOException ignored) {
                    continue;
                }
                dispatch(message);
            }
        } catch (Exception error) {
            LOGGER.error("[readLoop] crashed: %s", error.getMessage());
            onCrash(null);
        } finally {
            cancelFuture(stderrTask);
            stderrTask = null;
        }
    }

    private void dispatch(Map<String, Object> message) {
        Object msgId = message.get("id");
        Object methodObject = message.get("method");
        String method = methodObject instanceof String ? (String) methodObject : null;

        if (msgId != null && method != null) {
            String msgIdString = String.valueOf(msgId);
            PendingRequest entry = pending.get(msgIdString);
            if (entry != null) {
                completePendingEntry(msgIdString, entry, message);
            } else {
                ioExecutor.submit(() -> handleServerRequest(method, msgId, message.get("params")));
            }
            return;
        }

        if (msgId == null && method != null) {
            List<Consumer<Object>> handlers = notificationHandlers.get(method);
            if (handlers != null) {
                Object params = message.get("params");
                for (Consumer<Object> handler : handlers) {
                    try {
                        handler.accept(params);
                    } catch (Exception error) {
                        LOGGER.debug("[dispatch] notification handler error for %s: %s", method, error.getMessage());
                    }
                }
            } else if (method.startsWith("window/") || method.startsWith("telemetry/")) {
                LOGGER.debug("[dispatch] server notification: %s", method);
            }
            return;
        }

        if (msgId != null) {
            String msgIdString = String.valueOf(msgId);
            PendingRequest entry = pending.get(msgIdString);
            if (entry != null && !entry.future.isDone()) {
                completePendingEntry(msgIdString, entry, message);
            }
        }
    }

    private void completePendingEntry(String msgId, PendingRequest entry, Map<String, Object> message) {
        if (entry.future.isDone()) {
            return;
        }
        Object error = message.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            int code = asInt(errorMap.get("code"), -1);
            Object errorMessage = errorMap.containsKey("message") ? errorMap.get("message") : "Unknown error";
            String messageText = String.valueOf(errorMessage);
            entry.future.completeExceptionally(new LspError(code, messageText));
        } else {
            entry.future.complete(message.get("result"));
        }
        pending.remove(msgId);
        entry.timeoutFuture.cancel(false);
    }

    private void consumeStderrForever(InputStream stderrReader) {
        byte[] buffer = new byte[4096];
        try {
            while (stderrReader.read(buffer) >= 0) {
                // Intentionally discard stderr output while keeping the pipe drained.
            }
        } catch (IOException error) {
            LOGGER.debug("[stderrConsumer] Error: %s", error.getMessage());
        }
    }

    private void handleServerRequest(String method, Object msgId, Object params) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", msgId);
        switch (method) {
            case "workspace/configuration":
            case "workspace/workspaceFolders":
                response.put("result", List.of());
                break;
            case "client/registerCapability":
            case "client/unregisterCapability":
                response.put("result", null);
                break;
            default:
                LOGGER.debug("[handleServerRequest] unhandled server request: %s (id=%s)", method, msgId);
                response.put("result", null);
                break;
        }
        sendResponse(response);
    }

    private void sendResponse(Map<String, Object> response) {
        try {
            writeMessage(response);
            LOGGER.debug("[sendResponse] sent and drained for id=%s", response.get("id"));
        } catch (IOException error) {
            LOGGER.debug("[sendResponse] Failed: %s", error.getMessage());
        }
    }

    private CompletableFuture<Object> rpcRequest(String method, Object params) {
        String msgId = UUID.randomUUID().toString();
        CompletableFuture<Object> future = new CompletableFuture<>();
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            PendingRequest removed = pending.remove(msgId);
            if (removed != null && !removed.future.isDone()) {
                removed.future.completeExceptionally(
                        new LspError(-1, "Request timeout after "
                                + LspConstants.DEFAULT_REQUEST_TIMEOUT_MS
                                + "ms: " + method));
            }
        }, LspConstants.DEFAULT_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        pending.put(msgId, new PendingRequest(future, method, timeoutFuture));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", msgId);
        message.put("method", method);
        message.put("params", params);

        try {
            writeMessage(message);
        } catch (IOException error) {
            pending.remove(msgId);
            timeoutFuture.cancel(false);
            future.completeExceptionally(error);
        }

        return future;
    }

    private CompletableFuture<Void> rpcNotification(String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);

        try {
            writeMessage(message);
            return CompletableFuture.completedFuture(null);
        } catch (IOException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private void onCrash(Integer code) {
        if (!crashReported.compareAndSet(false, true)) {
            return;
        }
        isInitialized = false;
        completePending(new IllegalStateException("LSP server crashed with code " + code));
        pending.clear();
        if (onExitCallback != null) {
            onExitCallback.accept(code);
        }
    }

    private void completePending(RuntimeException error) {
        for (PendingRequest request : pending.values()) {
            if (!request.future.isDone()) {
                request.future.completeExceptionally(error);
            }
            request.timeoutFuture.cancel(false);
        }
    }

    private void writeMessage(Map<String, Object> message) throws IOException {
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(message);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        synchronized (writeLock) {
            OutputStream outputStream = process.getOutputStream();
            outputStream.write(header);
            outputStream.write(body);
            outputStream.flush();
        }
    }

    private Map<String, Object> buildInitializeParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("processId", ProcessHandle.current().pid());
        params.put("rootUri", FileUriUtils.pathToFileUri(config.getWorkspaceFolder()));
        params.put("workspaceFolders", List.of(
                Map.of(
                        "uri", FileUriUtils.pathToFileUri(config.getWorkspaceFolder()),
                        "name", "workspace")));

        Map<String, Object> synchronization = new LinkedHashMap<>();
        synchronization.put("didOpen", true);
        synchronization.put("didChange", Map.of("willSave", true, "willSaveWaitUntil", true, "save", true));

        Map<String, Object> textDocument = new LinkedHashMap<>();
        textDocument.put("synchronization", synchronization);
        textDocument.put("publishDiagnostics", Map.of("versionSupport", true));

        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("applyEdit", true);
        workspace.put("workspaceEdit", Map.of("documentChanges", true));
        workspace.put("workspaceFolders", true);
        workspace.put("configuration", true);

        Map<String, Object> clientCapabilities = new LinkedHashMap<>();
        clientCapabilities.put("window", Map.of("workDoneProgress", true));
        clientCapabilities.put("workspace", workspace);
        clientCapabilities.put("textDocument", textDocument);
        params.put("capabilities", clientCapabilities);

        if (config.getInitializationOptions() != null) {
            params.put("initializationOptions", config.getInitializationOptions());
        }
        return params;
    }

    private CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), millis, TimeUnit.MILLISECONDS);
        return future;
    }

    private static String readAsciiLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int next = inputStream.read();
            if (next < 0) {
                if (buffer.size() == 0) {
                    return null;
                }
                break;
            }
            if (next == '\n') {
                break;
            }
            buffer.write(next);
        }
        byte[] bytes = buffer.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    private static byte[] readExact(InputStream inputStream, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = inputStream.read(result, offset, length - offset);
            if (read < 0) {
                throw new IOException("Unexpected EOF while reading JSON-RPC body");
            }
            offset += read;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void closeQuietly(AutoCloseable closeable, String label) {
        try {
            closeable.close();
        } catch (Exception error) {
            LOGGER.debug("Failed to close %s (ignored): %s", label, error.getMessage());
        }
    }

    private void cancelFuture(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private record PendingRequest(
            CompletableFuture<Object> future,
            String method,
            ScheduledFuture<?> timeoutFuture) {
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private int index;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
