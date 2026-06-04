/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

import com.openjiuwen.harness.lsp.core.LspDiagnosticRegistry;
import com.openjiuwen.harness.lsp.core.LspServerInstance;
import com.openjiuwen.harness.lsp.core.LspServerInstanceKey;
import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.lsp.core.ScopedLspServerConfig;
import com.openjiuwen.harness.lsp.query.LspDiagnosticFile;
import com.openjiuwen.harness.lsp.servers.LspServerRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests: LSPServerManager diagnostic wiring.
 *
 * <p>Mirrors Python's {@code test_lsp_diagnostics.py} in
 * {@code tests.unit_tests.harness.tools}.
 */
class TestLspDiagnostics {

    @BeforeEach
    void setUp() {
        LspDiagnosticRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        LspDiagnosticRegistry.reset();
    }

    @Nested
    class TestEnsureDiagnosticHandler {

        @Test
        void testRegistersPublishDiagnosticsHandler() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("python");

            manager.ensureDiagnosticHandler(server);

            assertEquals(1, server.addedHandlers.size());
            assertTrue(server.addedHandlers.containsKey("textDocument/publishDiagnostics"));
        }

        @Test
        void testHandlerIdStoredAfterRegistration() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("python");

            manager.ensureDiagnosticHandler(server);

            assertTrue(manager.hasDiagnosticHandler("python"));
        }

        @Test
        void testIdempotentSecondCallDoesNotReRegister() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("python");

            manager.ensureDiagnosticHandler(server);
            manager.ensureDiagnosticHandler(server);

            assertEquals(1, server.addHandlerCallCount);
        }

        @Test
        void testDifferentServerInstancesEachGetHandler() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance python = fakeServer("python");
            FakeLspServerInstance ruff = fakeServer("ruff");

            manager.ensureDiagnosticHandler(python);
            manager.ensureDiagnosticHandler(ruff);

            assertEquals(1, python.addHandlerCallCount);
            assertEquals(1, ruff.addHandlerCallCount);
        }

        @Test
        void testHandlerUsesServerIdAsServerName() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("my-lsp");
            manager.ensureDiagnosticHandler(server);

            server.fireDiagnostics("file:///workspace/a.py", rawDiagnostics("err", 1, 0, 0));

            List<LspDiagnosticFile> result = manager.getPendingDiagnostics(10, 30);
            assertEquals("my-lsp", result.get(0).getServerName());
        }
    }

    @Nested
    class TestOpenFile {

        @Test
        void testOpenFileRegistersDiagnosticHandler(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "# content");

            manager.openFile(file.toString(), "python");

            assertTrue(manager.hasDiagnosticHandler("python"));
            assertEquals(1, server.addHandlerCallCount);
        }

        @Test
        void testOpenFileSendsDidOpenNotification(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "x = 1");

            manager.openFile(file.toString(), "python");

            assertEquals("textDocument/didOpen", server.notifications.get(0).method());
        }

        @Test
        void testOpenFileSetsVersionZero(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "");

            manager.openFile(file.toString(), "python");

            Map<String, Object> textDocument = nestedMap(server.notifications.get(0).params(), "textDocument");
            assertEquals(0, textDocument.get("version"));
        }

        @Test
        void testOpenFileNoServerReturnsGracefully() {
            LspServerManager manager = new LspServerManager();

            assertDoesNotThrow(() -> manager.openFile("/workspace/unknown.xyz", "text"));
        }

        @Test
        void testOpenFileHandlerRegisteredOnceOnMultipleCalls(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path first = pythonFile(tempDir, "a.py", "");
            Path second = pythonFile(tempDir, "b.py", "");

            manager.openFile(first.toString(), "python");
            manager.openFile(second.toString(), "python");

            assertEquals(1, server.addHandlerCallCount);
        }
    }

    @Nested
    class TestChangeFile {

        @Test
        void testChangeFileRegistersDiagnosticHandler(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "new content");

            manager.changeFile(file.toString(), "python", null);

            assertTrue(manager.hasDiagnosticHandler("python"));
        }

        @Test
        void testChangeFileSendsDidChangeNotification(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "updated");

            manager.changeFile(file.toString(), "python", null);

            assertEquals("textDocument/didChange", server.notifications.get(0).method());
        }

        @Test
        void testChangeFileIncrementsVersion(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "v1");

            manager.changeFile(file.toString(), "python", null);

            Map<String, Object> textDocument = nestedMap(server.notifications.get(0).params(), "textDocument");
            assertEquals(1, textDocument.get("version"));
        }

        @Test
        void testChangeFileVersionIncrementsOnEachCall(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "text");

            manager.changeFile(file.toString(), "python", null);
            manager.changeFile(file.toString(), "python", null);

            int firstVersion = (Integer) nestedMap(server.notifications.get(0).params(), "textDocument").get("version");
            int secondVersion = (Integer) nestedMap(server.notifications.get(1).params(), "textDocument").get("version");
            assertEquals(firstVersion + 1, secondVersion);
        }

        @Test
        void testChangeFileUsesExplicitContent(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "disk text");

            manager.changeFile(file.toString(), "python", "explicit text");

            List<?> changes = (List<?>) server.notifications.get(0).params().get("contentChanges");
            Map<?, ?> change = (Map<?, ?>) changes.get(0);
            assertEquals("explicit text", change.get("text"));
        }

        @Test
        void testChangeFileReadsDiskWhenContentNone(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "from disk");

            manager.changeFile(file.toString(), "python", null);

            List<?> changes = (List<?>) server.notifications.get(0).params().get("contentChanges");
            Map<?, ?> change = (Map<?, ?>) changes.get(0);
            assertEquals("from disk", change.get("text"));
        }

        @Test
        void testChangeFileSendsFullContentChange(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path file = pythonFile(tempDir, "a.py", "full");

            manager.changeFile(file.toString(), "python", null);

            List<?> changes = (List<?>) server.notifications.get(0).params().get("contentChanges");
            Map<?, ?> change = (Map<?, ?>) changes.get(0);
            assertTrue(change.containsKey("text"));
            assertFalse(change.containsKey("range"));
        }

        @Test
        void testChangeFileNoServerReturnsGracefully() {
            LspServerManager manager = new LspServerManager();

            assertDoesNotThrow(() -> manager.changeFile("/workspace/unknown.xyz", "text", null));
        }

        @Test
        void testChangeFileHandlerRegisteredOnceForSameServer(@TempDir Path tempDir) throws Exception {
            FakeLspServerInstance server = fakeServer("python");
            LspServerManager manager = managerWithFakePythonServer(tempDir, server);
            Path first = pythonFile(tempDir, "a.py", "");
            Path second = pythonFile(tempDir, "b.py", "");

            manager.changeFile(first.toString(), "python", null);
            manager.changeFile(second.toString(), "python", null);

            assertEquals(1, server.addHandlerCallCount);
        }
    }

    @Nested
    class TestGetPendingDiagnostics {

        @Test
        void testReturnsEmptyWhenNothingPending() {
            assertTrue(new LspServerManager().getPendingDiagnostics(10, 30).isEmpty());
        }

        @Test
        void testReturnsDiagnosticsFromRegistry() {
            LspDiagnosticRegistry.getInstance().register(
                    "python",
                    "file:///workspace/a.py",
                    rawDiagnostics("err", 1, 0, 0));

            List<LspDiagnosticFile> result = new LspServerManager().getPendingDiagnostics(10, 30);

            assertEquals(1, result.size());
            assertTrue(result.get(0).getFileUri().endsWith("/workspace/a.py"));
        }

        @Test
        void testClearsRegistryAfterRetrieval() {
            LspDiagnosticRegistry.getInstance().register(
                    "python",
                    "file:///workspace/a.py",
                    rawDiagnostics("err", 1, 0, 0));

            LspServerManager manager = new LspServerManager();
            manager.getPendingDiagnostics(10, 30);

            assertTrue(manager.getPendingDiagnostics(10, 30).isEmpty());
        }

        @Test
        void testRespectsMaxPerFile() {
            LspDiagnosticRegistry.getInstance().register(
                    "python",
                    "file:///workspace/a.py",
                    numberedDiagnostics(10));

            List<LspDiagnosticFile> result = new LspServerManager().getPendingDiagnostics(3, 100);

            assertEquals(3, result.get(0).getDiagnostics().size());
        }

        @Test
        void testRespectsMaxTotal() {
            for (int i = 0; i < 5; i++) {
                LspDiagnosticRegistry.getInstance().register(
                        "python",
                        "file:///workspace/f" + i + ".py",
                        numberedDiagnostics(5));
            }

            List<LspDiagnosticFile> result = new LspServerManager().getPendingDiagnostics(5, 8);
            int total = result.stream().mapToInt(file -> file.getDiagnostics().size()).sum();
            assertTrue(total <= 8);
        }
    }

    @Nested
    class TestEndToEnd {

        @Test
        void testOpenFileHandlerRoutesToRegistry() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("python");
            manager.ensureDiagnosticHandler(server);

            server.fireDiagnostics("file:///workspace/main.py", rawDiagnostics("Name 'x' undefined", 1, 5, 0));

            List<LspDiagnosticFile> result = manager.getPendingDiagnostics(10, 30);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getFileUri().endsWith("/workspace/main.py"));
            assertEquals("error", result.get(0).getDiagnostics().get(0).getSeverity());
        }

        @Test
        void testChangeFileDiagnosticsRoutedToRegistry() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("ruff");
            manager.ensureDiagnosticHandler(server);

            server.fireDiagnostics("file:///workspace/b.py", rawDiagnostics("line too long", 2, 0, 0));

            List<LspDiagnosticFile> result = manager.getPendingDiagnostics(10, 30);
            assertEquals("ruff", result.get(0).getServerName());
            assertEquals("line too long", result.get(0).getDiagnostics().get(0).getMessage());
        }

        @Test
        void testMultipleServersContributeToRegistry() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance python = fakeServer("python");
            FakeLspServerInstance ruff = fakeServer("ruff");
            manager.ensureDiagnosticHandler(python);
            manager.ensureDiagnosticHandler(ruff);

            python.fireDiagnostics("file:///workspace/a.py", rawDiagnostics("type error", 1, 0, 0));
            ruff.fireDiagnostics("file:///workspace/b.py", rawDiagnostics("style issue", 2, 5, 0));

            List<String> uris = manager.getPendingDiagnostics(10, 30).stream()
                    .map(LspDiagnosticFile::getFileUri)
                    .toList();
            assertTrue(uris.stream().anyMatch(uri -> uri.endsWith("/workspace/a.py")));
            assertTrue(uris.stream().anyMatch(uri -> uri.endsWith("/workspace/b.py")));
        }

        @Test
        void testCrossRoundDedupBetweenOpenAndChange() {
            LspServerManager manager = new LspServerManager();
            FakeLspServerInstance server = fakeServer("python");
            manager.ensureDiagnosticHandler(server);
            List<Map<String, Object>> diag = rawDiagnostics("same err", 1, 0, 0);

            server.fireDiagnostics("file:///workspace/a.py", diag);
            assertEquals(1, manager.getPendingDiagnostics(10, 30).size());

            server.fireDiagnostics("file:///workspace/a.py", diag);
            assertTrue(manager.getPendingDiagnostics(10, 30).isEmpty());
        }
    }

    private static FakeLspServerInstance fakeServer(String serverId) {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId(serverId);
        config.setCommand("fake");
        config.setExtensionToLanguage(Map.of("py", "python"));
        return new FakeLspServerInstance(config);
    }

    private static LspServerManager managerWithFakePythonServer(Path root, FakeLspServerInstance server)
            throws Exception {
        Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
        LspServerRegistry.bootstrapDefaults(root.toString());
        LspServerManager manager = new LspServerManager();
        setWorkspaceRoot(manager, root);
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId("python");
        config.setCommand("fake");
        config.setWorkspaceFolder(root.toString());
        config.setExtensionToLanguage(Map.of("py", "python"));
        manager.register(config);

        Field field = LspServerManager.class.getDeclaredField("runtimeInstances");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<LspServerInstanceKey, LspServerInstance> instances =
                (Map<LspServerInstanceKey, LspServerInstance>) field.get(manager);
        instances.put(new LspServerInstanceKey("python", root.toAbsolutePath().normalize().toString()), server);
        return manager;
    }

    private static void setWorkspaceRoot(LspServerManager manager, Path root) throws Exception {
        Field field = LspServerManager.class.getDeclaredField("workspaceRoot");
        field.setAccessible(true);
        field.set(manager, root.toAbsolutePath().normalize().toString());
    }

    private static Path pythonFile(Path root, String name, String text) throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    private static List<Map<String, Object>> rawDiagnostics(String message, int severity, int line, int character) {
        Map<String, Object> start = Map.of("line", line, "character", character);
        Map<String, Object> end = Map.of("line", line, "character", character + 1);
        Map<String, Object> range = Map.of("start", start, "end", end);
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("message", message);
        diagnostic.put("severity", severity);
        diagnostic.put("range", range);
        return List.of(diagnostic);
    }

    private static List<Map<String, Object>> numberedDiagnostics(int count) {
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            diagnostics.add(rawDiagnostics("e" + i, 2, i, 0).get(0));
        }
        return diagnostics;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private record NotificationCall(String method, Map<String, Object> params) {
    }

    private static final class FakeLspServerInstance extends LspServerInstance {
        private final Map<String, Consumer<Object>> addedHandlers = new LinkedHashMap<>();
        private final List<NotificationCall> notifications = new ArrayList<>();
        private int addHandlerCallCount;

        private FakeLspServerInstance(ScopedLspServerConfig config) {
            super(config, ignored -> {
            });
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public void sendNotification(String method, Object params) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = params instanceof Map<?, ?>
                    ? (Map<String, Object>) params
                    : Map.of();
            notifications.add(new NotificationCall(method, map));
        }

        @Override
        public void addNotificationHandler(String method, Consumer<Object> handler) {
            addHandlerCallCount++;
            addedHandlers.put(method, handler);
        }

        private void fireDiagnostics(String uri, List<Map<String, Object>> diagnostics) {
            addedHandlers.get("textDocument/publishDiagnostics").accept(Map.of(
                    "uri", uri,
                    "diagnostics", diagnostics));
        }
    }
}
