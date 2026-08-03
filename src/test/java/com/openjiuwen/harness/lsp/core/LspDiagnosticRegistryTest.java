/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's LSP diagnostic manager and registry tests in
 * {@code tests/unit_tests/harness/tools/test_lsp_diagnostics.py}.
 */
class LspDiagnosticRegistryTest {

    private static final String TEST_SERVER_ID = "java-test-lsp";

    @TempDir
    private Path tempDir;

    private final Map<String, ServerDefinition> previousServerDefinitions = new LinkedHashMap<>();
    private final Set<String> registeredTestServers = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        LspDiagnosticRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        LspDiagnosticRegistry.reset();
        LSPServerManager.shutdown();
        restoreServerDefinitions();
    }

    @Test
    void parseRawDropsInvalidEntriesAndNormalizesOptionalFields() {
        List<LspDiagnosticItem> items = LspDiagnosticRegistry.parseRaw(List.of(
                "not-a-map",
                Map.of("message", "", "severity", 1),
                diagnostic("first", 1, 4, 2, "pyright", 1001),
                diagnostic("second", 0, 7, 9, "", null)
        ));

        assertEquals(2, items.size());
        assertEquals("first", items.get(0).getMessage());
        assertEquals(1, items.get(0).getSeverity());
        assertEquals("pyright", items.get(0).getSource());
        assertEquals(1001, items.get(0).getCode());
        assertNull(items.get(1).getSource());
        assertEquals(0, items.get(1).getSeverity());
    }

    @Test
    void registerReturnsEmptyForInvalidPayloadsAndTracksPendingCount() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();

        assertEquals("", registry.register("pyright", "file:///workspace/a.py", List.of("bad")));
        assertEquals(0, registry.getPendingCount());

        String batchId = registry.register("pyright", "file:///workspace/a.py", List.of(diagnostic("err", 1, 0, 0)));

        assertNotEquals("", batchId);
        assertEquals(1, registry.getPendingCount());
    }

    @Test
    void getAndClearDeduplicatesWithinAndAcrossRounds() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        Map<String, Object> duplicate = diagnostic("same", 1, 0, 0);

        registry.register("pyright", "file:///workspace/a.py", List.of(duplicate));
        registry.register("pyright", "file:///workspace/a.py", List.of(duplicate));

        List<LspDiagnosticFile> first = registry.getAndClear(10, 30);
        assertEquals(1, first.size());
        assertEquals(1, first.get(0).getDiagnostics().size());
        assertEquals(0, registry.getPendingCount());

        registry.register("pyright", "file:///workspace/a.py", List.of(duplicate));
        assertTrue(registry.getAndClear(10, 30).isEmpty());
    }

    @Test
    void getAndClearSortsBySeverityAndAppliesPerFileAndGlobalCaps() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();

        registry.register("pyright", "file:///workspace/a.py", List.of(
                diagnostic("hint", 4, 3, 0),
                diagnostic("warning", 2, 1, 0),
                diagnostic("error", 1, 0, 0),
                diagnostic("info", 3, 2, 0)
        ));
        registry.register("ruff", "file:///workspace/b.py", List.of(
                diagnostic("b1", 2, 0, 0),
                diagnostic("b2", 2, 1, 0)
        ));

        List<LspDiagnosticFile> files = registry.getAndClear(2, 3);

        assertEquals(2, files.size());
        assertEquals(List.of("error", "warning"), files.get(0).getDiagnostics().stream()
                .map(LspDiagnosticItem::getMessage)
                .toList());
        assertEquals(1, files.get(1).getDiagnostics().size());
    }

    @Test
    void getAndClearPreservesServerNameAndResolvesLocalPath() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        String uri = "file:///workspace/example.py";

        registry.register("my-lsp", uri, List.of(diagnostic("err", 1, 5, 7)));

        LspDiagnosticFile file = registry.getAndClear(10, 30).get(0);

        assertEquals(uri, file.getUri());
        assertEquals("my-lsp", file.getServerName());
        assertEquals(
                com.openjiuwen.harness.lsp.core.utils.FileUriUtils.fileUriToPath(uri),
                file.getLocalPath()
        );
        assertEquals("err|1|5:7|null", LspDiagnosticRegistry.diagKey(file.getDiagnostics().get(0)));
    }

    @Test
    void ensureDiagnosticHandlerRegistersPublishDiagnosticsHandler() {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance server = fakeServer("pyright");

        manager.ensureDiagnosticHandler(server);

        assertEquals(1, server.handlerRegistrationCount);
        assertTrue(server.handlers.containsKey("textDocument/publishDiagnostics"));
    }

    @Test
    void ensureDiagnosticHandlerStoresHandlerInstanceAfterRegistration() throws Exception {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance server = fakeServer("pyright");

        manager.ensureDiagnosticHandler(server);

        assertTrue(diagnosticHandlerInstances(manager).contains(server));
    }

    @Test
    void ensureDiagnosticHandlerIsIdempotentForSameServer() {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance server = fakeServer("pyright");

        manager.ensureDiagnosticHandler(server);
        manager.ensureDiagnosticHandler(server);

        assertEquals(1, server.handlerRegistrationCount);
    }

    @Test
    void ensureDiagnosticHandlerRegistersDifferentServerInstancesSeparately() {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance pyright = fakeServer("pyright");
        FakeLspServerInstance ruff = fakeServer("ruff");

        manager.ensureDiagnosticHandler(pyright);
        manager.ensureDiagnosticHandler(ruff);

        assertEquals(1, pyright.handlerRegistrationCount);
        assertEquals(1, ruff.handlerRegistrationCount);
    }

    @Test
    void ensureDiagnosticHandlerUsesServerIdAsServerName() {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance server = fakeServer("my-lsp");
        manager.ensureDiagnosticHandler(server);

        server.publishDiagnostics("file:///workspace/a.py", List.of(diagnostic("err", 1, 0, 0)));

        List<LspDiagnosticFile> result = LspDiagnosticRegistry.getInstance().getAndClear(10, 30);
        assertEquals("my-lsp", result.get(0).getServerName());
    }

    @Test
    void openFileRegistersDiagnosticHandler() throws Exception {
        Path file = writeFile("a.py", "# content");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.openFile(file.toString(), "python");

        assertTrue(diagnosticHandlerInstances(manager).contains(server));
    }

    @Test
    void openFileSendsDidOpenNotification() throws Exception {
        Path file = writeFile("a.py", "x = 1");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.openFile(file.toString(), "python");

        assertEquals(1, server.notifications.size());
        assertEquals("textDocument/didOpen", server.notifications.get(0).method());
    }

    @Test
    void openFileSetsVersionZero() throws Exception {
        Path file = writeFile("a.py", "");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.openFile(file.toString(), "python");

        Map<?, ?> textDocument = textDocument(server.notifications.get(0));
        assertEquals(0, textDocument.get("version"));
    }

    @Test
    void openFileNoServerReturnsGracefully() throws Exception {
        LSPServerManager manager = managerWithoutServers();

        assertDoesNotThrow(() -> manager.openFile(tempDir.resolve("unknown.xyz").toString(), "text"));
    }

    @Test
    void openFileRegistersHandlerOnceOnMultipleCalls() throws Exception {
        Path first = writeFile("a.py", "");
        Path second = writeFile("b.py", "");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.openFile(first.toString(), "python");
        manager.openFile(second.toString(), "python");

        assertEquals(1, server.handlerRegistrationCount);
    }

    @Test
    void changeFileRegistersDiagnosticHandler() throws Exception {
        Path file = writeFile("a.py", "new content");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", null);

        assertTrue(diagnosticHandlerInstances(manager).contains(server));
    }

    @Test
    void changeFileSendsDidChangeNotification() throws Exception {
        Path file = writeFile("a.py", "updated");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", null);

        assertEquals(1, server.notifications.size());
        assertEquals("textDocument/didChange", server.notifications.get(0).method());
    }

    @Test
    void changeFileIncrementsVersionFromZero() throws Exception {
        Path file = writeFile("a.py", "v1");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", null);

        Map<?, ?> textDocument = textDocument(server.notifications.get(0));
        assertEquals(1, textDocument.get("version"));
    }

    @Test
    void changeFileVersionIncrementsOnEachCall() throws Exception {
        Path file = writeFile("a.py", "text");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", null);
        manager.changeFile(file.toString(), "python", null);

        int firstVersion = (Integer) textDocument(server.notifications.get(0)).get("version");
        int secondVersion = (Integer) textDocument(server.notifications.get(1)).get("version");
        assertEquals(firstVersion + 1, secondVersion);
    }

    @Test
    void changeFileUsesExplicitContent() throws Exception {
        Path file = writeFile("a.py", "from disk");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", "explicit text");

        assertEquals("explicit text", contentChanges(server.notifications.get(0)).get(0).get("text"));
    }

    @Test
    void changeFileReadsDiskWhenContentIsNull() throws Exception {
        Path file = writeFile("a.py", "from disk");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", null);

        assertEquals("from disk", contentChanges(server.notifications.get(0)).get(0).get("text"));
    }

    @Test
    void changeFileSendsFullContentChangeWithoutRange() throws Exception {
        Path file = writeFile("a.py", "full");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(file.toString(), "python", null);

        List<Map<?, ?>> changes = contentChanges(server.notifications.get(0));
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).containsKey("text"));
        assertFalse(changes.get(0).containsKey("range"));
    }

    @Test
    void changeFileNoServerReturnsGracefully() throws Exception {
        LSPServerManager manager = managerWithoutServers();

        assertDoesNotThrow(() -> manager.changeFile(tempDir.resolve("unknown.xyz").toString(), "text", null));
    }

    @Test
    void changeFileRegistersHandlerOnceForSameServer() throws Exception {
        Path first = writeFile("a.py", "");
        Path second = writeFile("b.py", "");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);

        manager.changeFile(first.toString(), "python", null);
        manager.changeFile(second.toString(), "python", null);

        assertEquals(1, server.handlerRegistrationCount);
    }

    @Test
    void getPendingDiagnosticsReturnsEmptyWhenNothingPending() {
        assertTrue(LSPServerManager.getPendingDiagnostics(10, 30).isEmpty());
    }

    @Test
    void getPendingDiagnosticsReturnsDiagnosticsFromRegistry() {
        LspDiagnosticRegistry.getInstance().register(
                "pyright",
                "file:///workspace/a.py",
                List.of(diagnostic("err", 1, 0, 0))
        );

        List<LspDiagnosticFile> result = LSPServerManager.getPendingDiagnostics(10, 30);

        assertEquals(1, result.size());
        assertEquals("file:///workspace/a.py", result.get(0).getUri());
    }

    @Test
    void getPendingDiagnosticsClearsRegistryAfterRetrieval() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        registry.register("pyright", "file:///workspace/a.py", List.of(diagnostic("err", 1, 0, 0)));

        LSPServerManager.getPendingDiagnostics(10, 30);

        assertEquals(0, registry.getPendingCount());
    }

    @Test
    void getPendingDiagnosticsRespectsMaxPerFile() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        registry.register("pyright", "file:///workspace/a.py", diagnostics("e", 10));

        List<LspDiagnosticFile> result = LSPServerManager.getPendingDiagnostics(3, 100);

        assertEquals(3, result.get(0).getDiagnostics().size());
    }

    @Test
    void getPendingDiagnosticsRespectsMaxTotal() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        for (int index = 0; index < 5; index++) {
            registry.register("pyright", "file:///workspace/f" + index + ".py", diagnostics("e" + index + "-", 5));
        }

        List<LspDiagnosticFile> result = LSPServerManager.getPendingDiagnostics(5, 8);

        int total = result.stream().mapToInt(file -> file.getDiagnostics().size()).sum();
        assertTrue(total <= 8);
    }

    @Test
    void openFileHandlerRoutesNotificationsToRegistry() throws Exception {
        Path file = writeFile("main.py", "print('open')");
        FakeLspServerInstance server = fakeServer(TEST_SERVER_ID);
        LSPServerManager manager = managerWithFakeServer(tempDir, server);
        manager.openFile(file.toString(), "python");

        server.publishDiagnostics(FileUriUtils.pathToFileUri(file.toString()),
                List.of(diagnostic("Name 'x' undefined", 1, 5, 0)));

        List<LspDiagnosticFile> result = LSPServerManager.getPendingDiagnostics(10, 30);
        assertEquals(1, result.size());
        assertEquals(FileUriUtils.pathToFileUri(file.toString()), result.get(0).getUri());
        assertEquals(1, result.get(0).getDiagnostics().get(0).getSeverity());
    }

    @Test
    void changeFileDiagnosticsRouteToRegistry() throws Exception {
        Path file = writeFile("b.py", "print('change')");
        FakeLspServerInstance server = fakeServer("ruff");
        LSPServerManager manager = managerWithFakeServer(tempDir, server);
        manager.changeFile(file.toString(), "python", "print('changed')");

        server.publishDiagnostics(FileUriUtils.pathToFileUri(file.toString()),
                List.of(diagnostic("line too long", 2, 0, 0)));

        List<LspDiagnosticFile> result = LSPServerManager.getPendingDiagnostics(10, 30);
        assertEquals(1, result.size());
        assertEquals("ruff", result.get(0).getServerName());
        assertEquals("line too long", result.get(0).getDiagnostics().get(0).getMessage());
    }

    @Test
    void multipleServersContributeDiagnosticsToRegistry() {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance pyright = fakeServer("pyright");
        FakeLspServerInstance ruff = fakeServer("ruff");
        manager.ensureDiagnosticHandler(pyright);
        manager.ensureDiagnosticHandler(ruff);

        pyright.publishDiagnostics("file:///workspace/a.py", List.of(diagnostic("type error", 1, 0, 0)));
        ruff.publishDiagnostics("file:///workspace/b.py", List.of(diagnostic("style issue", 2, 5, 0)));

        List<String> uris = LSPServerManager.getPendingDiagnostics(10, 30).stream()
                .map(LspDiagnosticFile::getUri)
                .toList();
        assertTrue(uris.contains("file:///workspace/a.py"));
        assertTrue(uris.contains("file:///workspace/b.py"));
    }

    @Test
    void crossRoundDedupSuppressesRepeatedDiagnosticsAfterOpenAndChange() {
        LSPServerManager manager = new LSPServerManager();
        FakeLspServerInstance server = fakeServer("pyright");
        manager.ensureDiagnosticHandler(server);
        Map<String, Object> duplicate = diagnostic("same err", 1, 0, 0);

        server.publishDiagnostics("file:///workspace/a.py", List.of(duplicate));
        List<LspDiagnosticFile> first = LSPServerManager.getPendingDiagnostics(10, 30);
        server.publishDiagnostics("file:///workspace/a.py", List.of(duplicate));
        List<LspDiagnosticFile> second = LSPServerManager.getPendingDiagnostics(10, 30);

        assertEquals(1, first.size());
        assertTrue(second.isEmpty());
    }

    private static Map<String, Object> diagnostic(String message, int severity, int line, int character) {
        return diagnostic(message, severity, line, character, null, null);
    }

    private static Map<String, Object> diagnostic(
            String message,
            int severity,
            int line,
            int character,
            String source,
            Object code
    ) {
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("line", line);
        start.put("character", character);
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("line", line);
        end.put("character", character + 1);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", start);
        range.put("end", end);
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("message", message);
        diagnostic.put("severity", severity);
        diagnostic.put("range", range);
        if (source != null) {
            diagnostic.put("source", source);
        }
        if (code != null) {
            diagnostic.put("code", code);
        }
        return diagnostic;
    }

    private static List<Map<String, Object>> diagnostics(String prefix, int count) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(diagnostic(prefix + index, 2, index, 0));
        }
        return result;
    }

    private Path writeFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private FakeLspServerInstance fakeServer(String serverId) {
        return new FakeLspServerInstance(config(serverId, tempDir));
    }

    private LSPServerManager managerWithFakeServer(Path root, FakeLspServerInstance server) throws Exception {
        String normalizedRoot = root.toAbsolutePath().normalize().toString();
        ScopedLspServerConfig config = server.getConfig();
        LSPServerManager manager = new LSPServerManager();
        setField(manager, "workspaceRoot", normalizedRoot);

        Map<String, List<ScopedLspServerConfig>> configs = new LinkedHashMap<>();
        configs.put(config.getServerId(), List.of(config));
        setField(manager, "configs", configs);

        Map<String, List<String>> extensionMap = new LinkedHashMap<>();
        extensionMap.put(".py", List.of(config.getServerId()));
        setField(manager, "extensionMap", extensionMap);

        Map<ServerInstanceKey, LspServerInstance> instances = new LinkedHashMap<>();
        instances.put(new ServerInstanceKey(config.getServerId(), normalizedRoot), server);
        setField(manager, "instances", instances);

        registerServerDefinition(config.getServerId(), normalizedRoot);
        return manager;
    }

    private LSPServerManager managerWithoutServers() throws Exception {
        LSPServerManager manager = new LSPServerManager();
        setField(manager, "workspaceRoot", tempDir.toAbsolutePath().normalize().toString());
        setField(manager, "configs", new LinkedHashMap<String, List<ScopedLspServerConfig>>());
        setField(manager, "extensionMap", Map.of(".py", List.of("missing-test-server")));
        return manager;
    }

    private void registerServerDefinition(String serverId, String root) {
        registeredTestServers.add(serverId);
        previousServerDefinitions.putIfAbsent(serverId, BuiltinServerRegistry.BUILTIN_SERVERS.get(serverId));
        BuiltinServerRegistry.BUILTIN_SERVERS.put(
                serverId,
                new ServerDefinition(
                        serverId,
                        List.of(".py"),
                        "python",
                        100,
                        false,
                        ignored -> root,
                        ignored -> null
                )
        );
    }

    private void restoreServerDefinitions() {
        for (String serverId : registeredTestServers) {
            ServerDefinition previous = previousServerDefinitions.get(serverId);
            if (previous == null) {
                BuiltinServerRegistry.BUILTIN_SERVERS.remove(serverId);
            } else {
                BuiltinServerRegistry.BUILTIN_SERVERS.put(serverId, previous);
            }
        }
        registeredTestServers.clear();
        previousServerDefinitions.clear();
    }

    private static ScopedLspServerConfig config(String serverId, Path root) {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId(serverId);
        config.setCommand("java-test-lsp");
        config.setWorkspaceFolder(root.toAbsolutePath().normalize().toString());
        config.setExtensionToLanguage(Map.of(".py", "python"));
        return config;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = LSPServerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static Set<LspServerInstance> diagnosticHandlerInstances(LSPServerManager manager) throws Exception {
        Field field = LSPServerManager.class.getDeclaredField("diagnosticHandlerInstances");
        field.setAccessible(true);
        return (Set<LspServerInstance>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> textDocument(Notification notification) {
        return (Map<?, ?>) ((Map<?, ?>) notification.params()).get("textDocument");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> contentChanges(Notification notification) {
        return (List<Map<?, ?>>) ((Map<?, ?>) notification.params()).get("contentChanges");
    }

    private static final class FakeLspServerInstance extends LspServerInstance {
        private final List<Notification> notifications = new ArrayList<>();
        private final Map<String, List<Consumer<Object>>> handlers = new LinkedHashMap<>();
        private int handlerRegistrationCount;
        private boolean running = true;

        private FakeLspServerInstance(ScopedLspServerConfig config) {
            super(config, ignored -> {
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
            handlers.computeIfAbsent(method, ignored -> new ArrayList<>()).add(handler);
            handlerRegistrationCount++;
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
            List<Consumer<Object>> diagnosticHandlers = handlers.get("textDocument/publishDiagnostics");
            assertNotNull(diagnosticHandlers);
            assertFalse(diagnosticHandlers.isEmpty());
            diagnosticHandlers.get(0).accept(Map.of("uri", uri, "diagnostics", diagnostics));
        }
    }

    private record Notification(String method, Object params) {
    }
}
