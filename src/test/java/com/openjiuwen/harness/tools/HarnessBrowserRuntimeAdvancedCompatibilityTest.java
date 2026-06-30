package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser.BrowserActionController;
import com.openjiuwen.harness.tools.browser.BrowserProfile;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserService;
import com.openjiuwen.harness.tools.browser.ManagedBrowserDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessBrowserRuntimeAdvancedCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void managedBrowserShouldReuseReadyEndpointAndSkipExternalStop() throws Exception {
        BrowserProfile profile = BrowserProfile.builder()
                .name("test-profile")
                .driverType("managed")
                .cdpUrl("http://127.0.0.1:9333")
                .userDataDir(".")
                .debugPort(9333)
                .host("127.0.0.1")
                .build();
        ManagedBrowserDriver driver = new ManagedBrowserDriver(profile) {
            @Override
            public boolean isEndpointReady() {
                return true;
            }
        };

        String endpoint = driver.start();
        driver.stop();

        assertThat(endpoint).isEqualTo("http://127.0.0.1:9333");
        assertThat(driver.isProcessOwned()).isFalse();
    }

    @Test
    void uploadBuiltinsShouldListFilesAndBuildScript() throws Exception {
        Path root = tempDir.resolve("uploads");
        Files.createDirectories(root);
        Files.writeString(root.resolve("report.xlsx"), "data");

        List<Map<String, Object>> files = BrowserActionController.listDirFiles(root);
        String script = BrowserActionController.buildSetInputFilesScript("#upload", List.of("/data/a.pdf", "/data/b.csv"));

        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("name", "report.xlsx");
        assertThat(script).contains("#upload").contains("/data/a.pdf").contains("/data/b.csv");
    }

    @Test
    void uploadBuiltinShouldUseCodeExecutorWhenBound() {
        BrowserActionController controller = new BrowserActionController();
        controller.registerExampleActions();
        AtomicBoolean invoked = new AtomicBoolean(false);
        controller.bindCodeExecutor(script -> {
            invoked.set(true);
            return Map.of("ok", true, "selector", "input[type=\"file\"]", "paths", List.of("/tmp/x.pdf"));
        });

        Map<String, Object> result = controller.runAction("browser_set_input_files", "s", "r", Map.of("paths", List.of("/tmp/x.pdf")));

        assertThat(invoked.get()).isTrue();
        assertThat(result).containsEntry("ok", true);
    }

    @Test
    void heartbeatShouldMarkHealthyAndDetectManagedDriverFailure() {
        BrowserService service = new BrowserService(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                McpServerConfig.builder().serverId("test").serverName("test").serverPath("stdio://playwright").clientType("stdio").build(),
                BrowserRunGuardrails.builder().build()
        );

        service.setConnectionHealthy(true);
        service.checkConnection();
        assertThat(service.isConnectionHealthy()).isTrue();

        service.setManagedDriver(new ManagedBrowserDriver(BrowserProfile.builder().cdpUrl("http://127.0.0.1:9333").build()) {
            @Override
            public boolean isEndpointReady() {
                return false;
            }
        });

        try {
            service.checkConnection();
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage().toLowerCase()).contains("cdp");
        }
    }
}
