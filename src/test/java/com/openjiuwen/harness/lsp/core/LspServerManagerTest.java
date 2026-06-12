/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class LspServerManagerTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanup() {
        LspServerManager.shutdown();
        LspDiagnosticRegistry.reset();
    }

    @Test
    void serverInstanceKeyUsesServerAndRootIdentity() {
        ServerInstanceKey first = new ServerInstanceKey("python", tempDir.toString());
        ServerInstanceKey same = new ServerInstanceKey("python", tempDir.toString());
        ServerInstanceKey different = new ServerInstanceKey("python", tempDir.resolve("other").toString());

        assertThat(first).isEqualTo(same);
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    void pathBelongsToRootUsesResolvedPathContainment() {
        Path file = tempDir.resolve("src/Main.py");

        assertThat(LspServerManager.pathBelongsToRoot(file.toString(), tempDir.toString())).isTrue();
        assertThat(LspServerManager.pathBelongsToRoot(tempDir.resolveSibling("outside.py").toString(), tempDir.toString()))
                .isFalse();
    }

    @Test
    void getOrStartServerReturnsHealthyCachedInstance() throws Exception {
        Path file = tempDir.resolve("sample.py");
        Files.writeString(file, "print('ok')");
        LspServerManager manager = managerWithFakePythonServer(tempDir, new FakeLspServerInstance(config(tempDir)));

        LspServerInstance instance = manager.getOrStartServer(file.toString());

        assertThat(instance).isInstanceOf(FakeLspServerInstance.class);
        assertThat(instance.isRunning()).isTrue();
    }

    @Test
    void openAndChangeFileSendFullDocumentNotifications() throws Exception {
        Path file = tempDir.resolve("sample.py");
        Files.writeString(file, "print('open')");
        FakeLspServerInstance server = new FakeLspServerInstance(config(tempDir));
        LspServerManager manager = managerWithFakePythonServer(tempDir, server);
        String uri = FileUriUtils.pathToFileUri(file.toString());

        manager.openFile(file.toString(), "python");
        manager.changeFile(file.toString(), "python", "print('changed')");

        assertThat(manager.isFileOpen(uri)).isTrue();
        assertThat(server.notifications).hasSize(2);
        assertThat(server.notifications.get(0).method()).isEqualTo("textDocument/didOpen");
        assertThat(server.notifications.get(1).method()).isEqualTo("textDocument/didChange");
        assertThat(server.notifications.get(0).params().toString()).contains("version=0").contains("print('open')");
        assertThat(server.notifications.get(1).params().toString()).contains("version=1").contains("print('changed')");
        assertThat(server.handlerRegistrationCount).isEqualTo(1);
    }

    @Test
    void diagnosticsHandlerRegistersPendingDiagnostics() throws Exception {
        Path file = tempDir.resolve("sample.py");
        Files.writeString(file, "print('open')");
        FakeLspServerInstance server = new FakeLspServerInstance(config(tempDir));
        LspServerManager manager = managerWithFakePythonServer(tempDir, server);
        String uri = FileUriUtils.pathToFileUri(file.toString());

        manager.openFile(file.toString(), "python");
        server.publishDiagnostics(uri, List.of(Map.of(
                "message", "syntax error",
                "severity", 1,
                "range", Map.of("start", Map.of("line", 0, "character", 0))
        )));

        List<LspDiagnosticFile> diagnostics = LspServerManager.getPendingDiagnostics(10, 30);
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).getUri()).isEqualTo(uri);
        assertThat(diagnostics.get(0).getDiagnostics().get(0).getMessage()).isEqualTo("syntax error");
    }

    private static LspServerManager managerWithFakePythonServer(Path root, FakeLspServerInstance server)
            throws Exception {
        String normalizedRoot = root.toAbsolutePath().normalize().toString();
        ScopedLspServerConfig config = server.getConfig();
        LspServerManager manager = new LspServerManager();
        setField(manager, "workspaceRoot", normalizedRoot);

        Map<String, List<ScopedLspServerConfig>> configs = new LinkedHashMap<>();
        configs.put("python", List.of(config));
        setField(manager, "configs", configs);

        Map<String, List<String>> extensionMap = new LinkedHashMap<>();
        extensionMap.put(".py", List.of("python"));
        setField(manager, "extensionMap", extensionMap);

        Map<ServerInstanceKey, LspServerInstance> instances = new LinkedHashMap<>();
        instances.put(new ServerInstanceKey("python", normalizedRoot), server);
        setField(manager, "instances", instances);

        BuiltinServerRegistry.BUILTIN_SERVERS.put(
                "python",
                new ServerDefinition(
                        "python",
                        List.of(".py"),
                        "python",
                        100,
                        false,
                        ignored -> normalizedRoot,
                        ignored -> null
                )
        );
        return manager;
    }

    private static ScopedLspServerConfig config(Path root) {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId("python");
        config.setCommand("python-lsp");
        config.setWorkspaceFolder(root.toAbsolutePath().normalize().toString());
        config.setExtensionToLanguage(Map.of(".py", "python"));
        return config;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = LspServerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeLspServerInstance extends LspServerInstance {
        private final List<Notification> notifications = new ArrayList<>();
        private Consumer<Object> diagnosticsHandler;
        private int handlerRegistrationCount;
        private boolean running = true;

        private FakeLspServerInstance(ScopedLspServerConfig config) {
            super(config, error -> {
            });
        }

        @Override
        public Map<String, Object> start() {
            running = true;
            return Map.of();
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean isHealthy() {
            return running;
        }

        @Override
        public LspServerState getState() {
            return running ? LspServerState.RUNNING : LspServerState.STOPPED;
        }

        @Override
        public void addNotificationHandler(String method, Consumer<Object> handler) {
            if ("textDocument/publishDiagnostics".equals(method)) {
                diagnosticsHandler = handler;
                handlerRegistrationCount++;
            }
        }

        @Override
        public void sendNotification(String method, Object params) {
            notifications.add(new Notification(method, params));
        }

        @Override
        public Object sendRequest(String method, Object params) {
            return Map.of("method", method, "params", params);
        }

        private void publishDiagnostics(String uri, List<?> diagnostics) {
            diagnosticsHandler.accept(Map.of("uri", uri, "diagnostics", diagnostics));
        }
    }

    private record Notification(String method, Object params) {
    }
}
