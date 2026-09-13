
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.lsp.core.LSPServerManager;
import com.openjiuwen.harness.lsp.core.LspDiagnostic;
import com.openjiuwen.harness.lsp.core.LspDiagnosticRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class HarnessLspManagerCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void diagnosticHandlerShouldRegisterAndRoutePayload() {
        LspDiagnosticRegistry.reset();
        LSPServerManager manager = new LSPServerManager();
        FakeServer server = new FakeServer("pyright");

        manager.ensureDiagnosticHandler(server);
        manager.ensureDiagnosticHandler(server);

        assertThat(server.handlers).containsKey("textDocument/publishDiagnostics");
        assertThat(server.handlerRegistrations).isEqualTo(1);

        server.handlers.get("textDocument/publishDiagnostics").accept(Map.of("uri", "file:///workspace/a.py",
                "diagnostics", List.of(Map.of("message", "err", "severity", 1))));

        List<LspDiagnostic> pending = LspDiagnosticRegistry.getInstance().getAndClear();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getServerName()).isEqualTo("pyright");
    }

    @Test
    void openFileAndChangeFileShouldSendNotificationsAndTrackVersions() throws Exception {
        Path file = tempDir.resolve("a.py");
        Files.writeString(file, "print('a')");
        LSPServerManager manager = new LSPServerManager();
        FakeServer server = new FakeServer("pyright");
        manager.registerServer(file.toString(), server);

        manager.openFile(file.toString(), "python");
        manager.changeFile(file.toString(), "python", "print('b')");
        manager.changeFile(file.toString(), "python");

        assertThat(server.notifications).hasSize(3);
        assertThat(server.notifications.get(0).method).isEqualTo("textDocument/didOpen");
        assertThat(((Map<?, ?>) server.notifications.get(0).params.get("textDocument")).get("version")).isEqualTo(0);
        assertThat(server.notifications.get(1).method).isEqualTo("textDocument/didChange");
        assertThat(((Map<?, ?>) server.notifications.get(1).params.get("textDocument")).get("version")).isEqualTo(1);
        assertThat(
                ((Map<?, ?>) ((List<?>) server.notifications.get(1).params.get("contentChanges")).get(0)).get("text"))
                .isEqualTo("print('b')");
        assertThat(((Map<?, ?>) server.notifications.get(2).params.get("textDocument")).get("version")).isEqualTo(2);
        assertThat(manager.getDocumentVersion(file.toString())).isEqualTo(2);
    }

    @Test
    void shutdownAllShouldCloseServersAndClearLifecycleState() throws Exception {
        Path file = tempDir.resolve("a.py");
        Files.writeString(file, "print('a')");
        LSPServerManager manager = new LSPServerManager();
        FakeServer server = new FakeServer("pyright");
        manager.registerServer(file.toString(), server);
        manager.openFile(file.toString(), "python");

        assertThat(manager.activeServerCount()).isEqualTo(1);
        assertThat(manager.hasServer(file.toString())).isTrue();

        manager.shutdownAll();

        assertThat(server.shutdownCalled).isTrue();
        assertThat(server.exitCalled).isTrue();
        assertThat(manager.activeServerCount()).isZero();
        assertThat(manager.hasServer(file.toString())).isFalse();
        assertThat(manager.getDocumentVersion(file.toString())).isNull();
    }

    @Test
    void getPendingDiagnosticsShouldSupportLimits() {
        LspDiagnosticRegistry.reset();
        LspDiagnosticRegistry.getInstance().register("pyright", "file:///a.py",
                List.of(Map.of("message", "e1"), Map.of("message", "e2"), Map.of("message", "e3")));
        LspDiagnosticRegistry.getInstance().register("ruff", "file:///b.py",
                List.of(Map.of("message", "e4"), Map.of("message", "e5")));

        List<LspDiagnostic> limited = LSPServerManager.getPendingDiagnostics(2, 3);

        assertThat(limited).hasSize(3);
        assertThat(limited.stream().filter(item -> item.getUri().equals("file:///a.py")).count()).isEqualTo(2);
    }

    static final class FakeServer {
        final FakeConfig config;
        final java.util.Map<String, Consumer<Map<String, Object>>> handlers = new java.util.LinkedHashMap<>();
        final java.util.List<Notification> notifications = new ArrayList<>();
        final java.util.List<Notification> requests = new ArrayList<>();
        Object requestResult;
        int handlerRegistrations = 0;
        boolean shutdownCalled;
        boolean exitCalled;

        FakeServer(String serverId) {
            this.config = new FakeConfig(serverId);
        }

        public FakeConfig getConfig() {
            return config;
        }

        public void addNotificationHandler(String method, Consumer<Map<String, Object>> handler) {
            handlerRegistrations += 1;
            handlers.put(method, handler);
        }

        public void sendNotification(String method, Map<String, Object> params) {
            notifications.add(new Notification(method, params));
        }

        public Object sendRequest(String method, Map<String, Object> params) {
            requests.add(new Notification(method, params));
            return requestResult;
        }

        public void shutdown() {
            shutdownCalled = true;
        }

        public void exit() {
            exitCalled = true;
        }
    }

    record FakeConfig(String serverId) {
        public String getServerId() {
            return serverId;
        }
    }

    record Notification(String method, Map<String, Object> params) {
    }
}
