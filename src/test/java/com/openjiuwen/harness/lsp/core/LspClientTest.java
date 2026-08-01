/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

/**
 * Focused verification for Python's JSON-RPC LSP client behavior in
 * {@code openjiuwen/harness/lsp/core/client.py}.
 */
class LspClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void initializeSendsHandshakeAndDispatchesNotifications() throws Exception {
        TestProcess process = new TestProcess();
        ScopedLspServerConfig config = createConfig();
        LspClient client = new LspClient(config, process, ignored -> {
        });
        CountDownLatch notificationLatch = new CountDownLatch(1);
        AtomicReference<Object> notificationParams = new AtomicReference<>();
        client.addNotificationHandler("window/logMessage", params -> {
            notificationParams.set(params);
            notificationLatch.countDown();
        });

        var initializeFuture = client.initialize();
        Map<String, Object> initializeRequest = process.readClientMessage();
        assertEquals("initialize", initializeRequest.get("method"));
        Map<String, Object> params = castMap(initializeRequest.get("params"));
        assertEquals("workspace", castList(params.get("workspaceFolders")).get(0).get("name"));
        process.sendServerMessage(messageWithId(initializeRequest.get("id"), "result", Map.of(
                "capabilities", Map.of("hoverProvider", true))));

        Map<String, Object> initializedNotification = process.readClientMessage();
        assertEquals("initialized", initializedNotification.get("method"));

        Map<String, Object> configNotification = process.readClientMessage();
        assertEquals("workspace/didChangeConfiguration", configNotification.get("method"));

        Map<String, Object> initializeResult = initializeFuture.get(2, TimeUnit.SECONDS);
        assertEquals(Map.of("hoverProvider", true), initializeResult.get("capabilities"));
        assertTrue(client.isInitialized());
        assertEquals(Map.of("hoverProvider", true), client.getCapabilities());

        process.sendServerMessage(Map.of(
                "jsonrpc", "2.0",
                "method", "window/logMessage",
                "params", Map.of("type", 3, "message", "hello")));

        assertTrue(notificationLatch.await(2, TimeUnit.SECONDS));
        assertEquals(Map.of("type", 3, "message", "hello"), notificationParams.get());

        process.respondToShutdownAndReadExit(client);
        assertFalse(client.isInitialized());
    }

    @Test
    void sendRequestSurfacesServerErrors() throws Exception {
        InitializedClient initializedClient = bootInitializedClient();
        LspClient client = initializedClient.client();
        TestProcess process = initializedClient.process();

        var future = client.sendRequest("textDocument/hover", Map.of("line", 1));
        Map<String, Object> request = process.readClientMessage();
        assertEquals("textDocument/hover", request.get("method"));
        process.sendServerMessage(messageWithId(request.get("id"), "error", Map.of("code", -32001, "message", "boom")));

        CompletionException error = assertInstanceOf(
                CompletionException.class,
                org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class, future::join));
        LspError lspError = assertInstanceOf(LspError.class, error.getCause());
        assertEquals(-32001, lspError.getCode());
        assertEquals("boom", lspError.getErrorMessage());

        process.respondToShutdownAndReadExit(client);
    }

    @Disabled("remote env do not support node")
    @Test
    void stopResolvesPendingRequests() throws Exception {
        InitializedClient initializedClient = bootInitializedClient();
        LspClient client = initializedClient.client();
        TestProcess process = initializedClient.process();

        var pending = client.sendRequest("workspace/symbol", Map.of("query", "abc"));
        Map<String, Object> request = process.readClientMessage();
        assertEquals("workspace/symbol", request.get("method"));

        CountDownLatch stopStarted = new CountDownLatch(1);
        Thread stopThread = new Thread(() -> {
            stopStarted.countDown();
            client.stop().join();
        }, "lsp-client-stop-test");
        stopThread.start();
        assertTrue(stopStarted.await(1, TimeUnit.SECONDS));

        Map<String, Object> shutdownRequest = process.readClientMessage();
        assertEquals("shutdown", shutdownRequest.get("method"));
        process.sendServerMessage(messageWithId(shutdownRequest.get("id"), "result", null));
        Map<String, Object> exitNotification = process.readClientMessage();
        assertEquals("exit", exitNotification.get("method"));

        stopThread.join(TimeUnit.SECONDS.toMillis(2));
        CompletionException error = assertInstanceOf(
                CompletionException.class,
                org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class, pending::join));
        assertEquals("LSP client stopped", error.getCause().getMessage());
    }

    private InitializedClient bootInitializedClient() throws Exception {
        TestProcess process = new TestProcess();
        LspClient client = new LspClient(createConfig(), process, ignored -> {
        });
        var initializeFuture = client.initialize();
        Map<String, Object> initializeRequest = process.readClientMessage();
        process.sendServerMessage(messageWithId(initializeRequest.get("id"), "result", Map.of(
                "capabilities", Map.of("completionProvider", true))));
        process.readClientMessage();
        process.readClientMessage();
        initializeFuture.get(2, TimeUnit.SECONDS);
        return new InitializedClient(client, process);
    }

    private ScopedLspServerConfig createConfig() {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId("pyright");
        config.setCommand("pyright-langserver");
        config.setWorkspaceFolder("D:/repo");
        config.setInitializationOptions(Map.of("python", Map.of("analysis", Map.of("typeCheckingMode", "basic"))));
        return config;
    }

    private static Map<String, Object> messageWithId(Object id, String payloadKey, Object payloadValue) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put(payloadKey, payloadValue);
        return message;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private record InitializedClient(LspClient client, TestProcess process) {
    }

    private static final class TestProcess extends Process {
        private final PipedOutputStream clientStdin = new PipedOutputStream();
        private final PipedInputStream serverInput;
        private final PipedOutputStream serverStdout = new PipedOutputStream();
        private final PipedInputStream clientStdout;
        private final PipedOutputStream serverStderr = new PipedOutputStream();
        private final PipedInputStream clientStderr;
        private volatile boolean alive = true;
        private volatile int exitCode = 0;

        private TestProcess() throws IOException {
            this.serverInput = new PipedInputStream(clientStdin);
            this.clientStdout = new PipedInputStream(serverStdout);
            this.clientStderr = new PipedInputStream(serverStderr);
        }

        @Override
        public OutputStream getOutputStream() {
            return clientStdin;
        }

        @Override
        public InputStream getInputStream() {
            return clientStdout;
        }

        @Override
        public InputStream getErrorStream() {
            return clientStderr;
        }

        @Override
        public int waitFor() {
            alive = false;
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            alive = false;
            try {
                clientStdin.close();
            } catch (IOException ignored) {
            }
            try {
                serverStdout.close();
            } catch (IOException ignored) {
            }
            try {
                serverStderr.close();
            } catch (IOException ignored) {
            }
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        private Map<String, Object> readClientMessage() throws IOException {
            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                String line = readAsciiLine(serverInput);
                if (line == null) {
                    throw new IOException("Unexpected EOF reading client message");
                }
                if (line.isEmpty()) {
                    break;
                }
                int separator = line.indexOf(": ");
                if (separator >= 0) {
                    headers.put(line.substring(0, separator).trim(), line.substring(separator + 2).trim());
                }
            }
            int length = Integer.parseInt(headers.get("Content-Length"));
            byte[] body = serverInput.readNBytes(length);
            return OBJECT_MAPPER.readValue(body, MAP_TYPE);
        }

        private void sendServerMessage(Map<String, Object> message) throws IOException {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(message);
            byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            serverStdout.write(header);
            serverStdout.write(body);
            serverStdout.flush();
        }

        private void respondToShutdownAndReadExit(LspClient client) throws Exception {
            var stopFuture = client.stop();
            Map<String, Object> shutdownRequest = readClientMessage();
            assertEquals("shutdown", shutdownRequest.get("method"));
            sendServerMessage(messageWithId(shutdownRequest.get("id"), "result", null));
            Map<String, Object> exitNotification = readClientMessage();
            assertEquals("exit", exitNotification.get("method"));
            stopFuture.get(2, TimeUnit.SECONDS);
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
    }
}
