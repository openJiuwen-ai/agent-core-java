package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.servers.LspServerRegistry;
import com.openjiuwen.harness.lsp.query.LspDiagnosticFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's diagnostic wiring expectations in
 * {@code tests.unit_tests.harness.tools.test_lsp_diagnostics} for didOpen/didChange flows.
 */
class LspServerManagerDocumentSyncTest {

    @AfterEach
    void tearDown() throws Exception {
        LspDiagnosticRegistry.reset();
        Field instanceField = LspServerManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        LspServerManager instance = (LspServerManager) instanceField.get(null);
        if (instance != null) {
            instance.stopAllRuntimeServers();
        }
        instanceField.set(null, null);
    }

    @Test
    void openFileRegistersHandlerAndSendsDidOpen(@TempDir Path tempDir) throws Exception {
        Path source = preparePythonWorkspace(tempDir, "a.py", "x = 1\n");
        StubServerInstance server = new StubServerInstance("python", Map.of("py", "python"));
        LspServerManager manager = configureManager(tempDir, server);

        manager.openFile(source.toString(), "python");

        assertTrue(manager.hasDiagnosticHandler("python"));
        assertEquals(1, server.handlerRegistrationCount());
        assertEquals(1, server.notifications.size());
        assertEquals("textDocument/didOpen", server.notifications.get(0).method());
        @SuppressWarnings("unchecked")
        Map<String, Object> textDocument = (Map<String, Object>) server.notifications.get(0).params().get("textDocument");
        assertEquals(0, textDocument.get("version"));
        assertEquals("python", textDocument.get("languageId"));
        assertEquals("x = 1\n", textDocument.get("text"));
        assertTrue(manager.isFileOpen(manager.toFileUri(source.toString())));
    }

    @Test
    void changeFileIncrementsVersionAndUsesExplicitContent(@TempDir Path tempDir) throws Exception {
        Path source = preparePythonWorkspace(tempDir, "a.py", "x = 1\n");
        StubServerInstance server = new StubServerInstance("python", Map.of("py", "python"));
        LspServerManager manager = configureManager(tempDir, server);

        manager.changeFile(source.toString(), "python", "explicit text");
        manager.changeFile(source.toString(), "python", "second text");

        assertEquals(1, server.handlerRegistrationCount());
        assertEquals(2, server.notifications.size());
        assertEquals("textDocument/didChange", server.notifications.get(0).method());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstDocument = (Map<String, Object>) server.notifications.get(0).params().get("textDocument");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstChanges = (List<Map<String, Object>>) server.notifications.get(0).params().get("contentChanges");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondDocument = (Map<String, Object>) server.notifications.get(1).params().get("textDocument");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondChanges = (List<Map<String, Object>>) server.notifications.get(1).params().get("contentChanges");
        assertEquals(1, firstDocument.get("version"));
        assertEquals(2, secondDocument.get("version"));
        assertEquals("explicit text", firstChanges.get(0).get("text"));
        assertEquals("second text", secondChanges.get(0).get("text"));
        assertFalse(firstChanges.get(0).containsKey("range"));
    }

    @Test
    void changeFileReadsDiskWhenContentMissing(@TempDir Path tempDir) throws Exception {
        Path source = preparePythonWorkspace(tempDir, "a.py", "from disk\n");
        StubServerInstance server = new StubServerInstance("python", Map.of("py", "python"));
        LspServerManager manager = configureManager(tempDir, server);

        manager.changeFile(source.toString(), "python", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) server.notifications.get(0).params().get("contentChanges");
        assertEquals("from disk\n", changes.get(0).get("text"));
    }

    @Test
    void publishDiagnosticsHandlerRoutesToRegistry(@TempDir Path tempDir) throws Exception {
        Path source = preparePythonWorkspace(tempDir, "a.py", "x = 1\n");
        StubServerInstance server = new StubServerInstance("python", Map.of("py", "python"));
        LspServerManager manager = configureManager(tempDir, server);

        manager.openFile(source.toString(), "python");
        server.firePublishDiagnostics(Map.of(
                "uri", manager.toFileUri(source.toString()),
                "diagnostics", List.of(Map.of(
                        "message", "undefined name",
                        "severity", 1,
                        "range", Map.of(
                                "start", Map.of("line", 0, "character", 0),
                                "end", Map.of("line", 0, "character", 1)
                        )
                ))
        ));

        List<LspDiagnosticFile> result = manager.getPendingDiagnostics(10, 30);
        assertEquals(1, result.size());
        assertEquals(manager.toFileUri(source.toString()), result.get(0).getFileUri());
        assertEquals("python", result.get(0).getServerName());
        assertEquals("undefined name", result.get(0).getDiagnostics().get(0).getMessage());
    }

    private Path preparePythonWorkspace(Path tempDir, String fileName, String content) throws Exception {
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname='demo'\n");
        Path source = tempDir.resolve(fileName);
        Files.writeString(source, content);
        return source;
    }

    private LspServerManager configureManager(Path workspace, StubServerInstance stubInstance) throws Exception {
        LspServerManager manager = LspServerManager.initialize();
        LspServerRegistry.bootstrapDefaults(workspace.toString());

        Field workspaceRootField = LspServerManager.class.getDeclaredField("workspaceRoot");
        workspaceRootField.setAccessible(true);
        workspaceRootField.set(manager, workspace.toString());

        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId("python");
        config.setWorkspaceFolder(workspace.toString());
        config.setExtensionToLanguage(Map.of("py", "python"));
        manager.register(config);
        stubInstance.getConfig().setWorkspaceFolder(workspace.toString());

        Field runtimeInstancesField = LspServerManager.class.getDeclaredField("runtimeInstances");
        runtimeInstancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<LspServerInstanceKey, LspServerInstance> runtimeInstances =
                (Map<LspServerInstanceKey, LspServerInstance>) runtimeInstancesField.get(manager);
        runtimeInstances.clear();
        runtimeInstances.put(new LspServerInstanceKey("python", workspace.toString()), stubInstance);
        return manager;
    }

    private static final class StubServerInstance extends LspServerInstance {

        private final ScopedLspServerConfig config;
        private final List<NotificationCall> notifications = new ArrayList<>();
        private final Map<String, List<Consumer<Object>>> handlers = new java.util.LinkedHashMap<>();

        private StubServerInstance(String serverId, Map<String, String> extensionToLanguage) {
            super(new ScopedLspServerConfig(), null);
            this.config = new ScopedLspServerConfig();
            this.config.setServerId(serverId);
            this.config.setExtensionToLanguage(extensionToLanguage);
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void addNotificationHandler(String method, Consumer<Object> handler) {
            handlers.computeIfAbsent(method, ignored -> new ArrayList<>()).add(handler);
        }

        @Override
        public void sendNotification(String method, Object params) {
            notifications.add(new NotificationCall(method, castMap(params)));
        }

        @Override
        public ScopedLspServerConfig getConfig() {
            return config;
        }

        @Override
        public Map<String, Object> start() {
            return Map.of();
        }

        @Override
        public void stop() {
            // no-op test stub
        }

        private int handlerRegistrationCount() {
            return handlers.getOrDefault("textDocument/publishDiagnostics", List.of()).size();
        }

        private void firePublishDiagnostics(Map<String, Object> params) {
            for (Consumer<Object> handler : handlers.getOrDefault("textDocument/publishDiagnostics", List.of())) {
                handler.accept(params);
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> castMap(Object value) {
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
    }

    private record NotificationCall(String method, Map<String, Object> params) {
    }
}
