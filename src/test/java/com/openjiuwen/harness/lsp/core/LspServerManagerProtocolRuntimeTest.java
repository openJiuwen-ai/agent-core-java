package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.query.LspLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's protocol-backed LSP tool integration expectations in
 * {@code tests.unit_tests.harness.tools.test_lsp_tool} for definition/reference calls.
 */
class LspServerManagerProtocolRuntimeTest {

    @AfterEach
    void tearDown() throws Exception {
        Field instanceField = LspServerManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        LspServerManager instance = (LspServerManager) instanceField.get(null);
        if (instance != null) {
            instance.stopAllRuntimeServers();
        }
        instanceField.set(null, null);
    }

    @Test
    void gotoDefinitionPrefersProtocolResponse(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("demo.py");
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname='demo'\n");
        Files.writeString(source, "def foo():\n    return 1\nbar = foo()\n");

        LspServerManager manager = configureManager(tempDir, new StubServerInstance(
                protocolDefinition(source),
                List.of()
        ));

        LspLocation location = manager.gotoDefinition(source.toString(), 3, 7);

        assertNotNull(location);
        assertEquals(source.toString(), location.getFilePath());
        assertEquals(1, location.getLine());
        assertEquals(5, location.getCharacter());
    }

    @Test
    void findReferencesPrefersProtocolResponse(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("demo.py");
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname='demo'\n");
        Files.writeString(source, "def foo():\n    return 1\nbar = foo()\nbaz = foo()\n");

        LspServerManager manager = configureManager(tempDir, new StubServerInstance(
                protocolDefinition(source),
                List.of(protocolReference(source, 0, 4), protocolReference(source, 2, 6), protocolReference(source, 3, 6))
        ));

        List<LspLocation> locations = manager.findReferences(source.toString(), 3, 7, true);

        assertEquals(3, locations.size());
        assertEquals(1, locations.get(0).getLine());
        assertEquals(3, locations.get(1).getLine());
        assertEquals(4, locations.get(2).getLine());
    }

    @Test
    void gotoDefinitionFallsBackWhenProtocolResponseMissing(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("demo.py");
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname='demo'\n");
        Files.writeString(source, "def foo():\n    return 1\nbar = foo()\n");

        LspServerManager manager = configureManager(tempDir, new StubServerInstance(null, List.of()));

        LspLocation location = manager.gotoDefinition(source.toString(), 3, 7);

        assertNotNull(location);
        assertEquals(1, location.getLine());
        assertEquals(5, location.getCharacter());
    }

    @Test
    void findReferencesFallsBackWhenProtocolResponseMissing(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("demo.py");
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname='demo'\n");
        Files.writeString(source, "def foo():\n    return 1\nbar = foo()\nbaz = foo()\n");

        LspServerManager manager = configureManager(tempDir, new StubServerInstance(null, null));

        List<LspLocation> locations = manager.findReferences(source.toString(), 3, 7, true);

        assertTrue(locations.size() >= 3);
        assertEquals(source.toString(), locations.get(0).getFilePath());
    }

    private LspServerManager configureManager(Path workspace, StubServerInstance stubInstance) throws Exception {
        LspServerManager manager = LspServerManager.initialize();

        Field workspaceRootField = LspServerManager.class.getDeclaredField("workspaceRoot");
        workspaceRootField.setAccessible(true);
        workspaceRootField.set(manager, workspace.toString());

        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId("pyright");
        config.setServerId("python");
        config.setWorkspaceFolder(workspace.toString());
        config.setExtensionToLanguage(Map.of("py", "python"));
        manager.register(config);

        Field runtimeInstancesField = LspServerManager.class.getDeclaredField("runtimeInstances");
        runtimeInstancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<LspServerInstanceKey, LspServerInstance> runtimeInstances =
                (Map<LspServerInstanceKey, LspServerInstance>) runtimeInstancesField.get(manager);
        runtimeInstances.clear();
        runtimeInstances.put(new LspServerInstanceKey("python", workspace.toString()), stubInstance);

        return manager;
    }

    private Map<String, Object> protocolDefinition(Path file) {
        return protocolReference(file, 0, 4);
    }

    private Map<String, Object> protocolReference(Path file, int zeroBasedLine, int zeroBasedCharacter) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", Map.of("line", zeroBasedLine, "character", zeroBasedCharacter));
        range.put("end", Map.of("line", zeroBasedLine, "character", zeroBasedCharacter + 3));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uri", "file:///" + file.toString().replace('\\', '/'));
        payload.put("range", range);
        return payload;
    }

    private static final class StubServerInstance extends LspServerInstance {

        private final Object definitionResponse;
        private final Object referencesResponse;

        private StubServerInstance(Object definitionResponse, Object referencesResponse) {
            super(new ScopedLspServerConfig(), null);
            this.definitionResponse = definitionResponse;
            this.referencesResponse = referencesResponse;
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
        public Object sendRequest(String method, Object params) {
            return switch (method) {
                case "textDocument/definition" -> definitionResponse;
                case "textDocument/references" -> referencesResponse;
                default -> null;
            };
        }

        @Override
        public Map<String, Object> start() {
            return Map.of();
        }

        @Override
        public void stop() {
            // no-op test stub
        }
    }
}
