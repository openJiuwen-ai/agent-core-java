package com.openjiuwen.harness.security;

import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionExampleSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBuildExamplePermissionsAndHost() {
        Map<String, Object> permissions = PermissionExampleSupport.examplePermissionsDict();
        ToolPermissionHost host = PermissionExampleSupport.examplePermissionHost(tempDir, tempDir.resolve("permissions.yaml"));

        assertThat(permissions).containsEntry("enabled", true);
        assertThat(((Map<?, ?>) permissions.get("tools")).get("read_file")).isEqualTo("ask");
        assertThat(host.getWorkspaceDirResolver().get()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(host.getPermissionYamlPath()).isEqualTo(tempDir.resolve("permissions.yaml"));
    }

    @Test
    void shouldBuildEngineAndRail() {
        PermissionEngine engine = PermissionExampleSupport.buildEngine(tempDir);
        PermissionInterruptRail rail = PermissionExampleSupport.buildRail(tempDir);

        assertThat(engine.checkPermission("read_file", Map.of("path", "a.txt")).getPermission()).isEqualTo(PermissionLevel.ASK);
        assertThat(rail).isNotNull();
    }
}
