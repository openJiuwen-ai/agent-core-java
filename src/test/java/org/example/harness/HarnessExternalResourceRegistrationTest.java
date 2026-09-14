/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package org.example.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class HarnessExternalResourceRegistrationTest {
    @TempDir
    Path tempDir;

    @Test
    void providerRegistrationShouldRoundTripResourcesOutsideApplicationPackage() throws Exception {
        HarnessConfigBuilder.registerToolProvider(ExternalProviderTool.class,
                new HarnessConfigBuilder.HarnessToolProvider() {
                    @Override
                    public String name() {
                        return "external-test-tool";
                    }

                    @Override
                    public Object create(Path workspaceRoot) {
                        return new ExternalProviderTool(workspaceRoot);
                    }
                });
        HarnessConfigBuilder.registerRailProvider(ExternalProviderRail.class,
                new HarnessConfigBuilder.HarnessRailProvider() {
                    @Override
                    public String name() {
                        return "external-test-rail";
                    }

                    @Override
                    public Object create() {
                        return new ExternalProviderRail();
                    }
                });

        String yaml = HarnessConfigBuilder.generateHarnessConfigYaml(
                AgentCard.builder().id("external-provider").name("External Provider").build(), "prompt",
                List.of(new ExternalProviderTool(tempDir)), List.of(new ExternalProviderRail()), "en", 3, 1.0);

        assertThat(yaml).contains("type: entry_point", "name: external-test-tool", "name: external-test-rail")
                .doesNotContain("module: org.example.harness");
        Path configPath = tempDir.resolve("external-provider.yaml");
        Files.writeString(configPath, yaml);
        try (DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath))) {
            assertThat(agent.getConfig().getTools()).singleElement().isInstanceOf(ExternalProviderTool.class);
            assertThat(agent.getConfig().getRails()).anyMatch(ExternalProviderRail.class::isInstance);
            ExternalProviderTool tool = (ExternalProviderTool) agent.getConfig().getTools().get(0);
            assertThat(tool.workspaceRoot()).isEqualTo(tempDir.toAbsolutePath().normalize());
        }
    }

    @Test
    void packageFactoryShouldAllowOnlyTheExactRegisteredClass() throws Exception {
        HarnessConfigBuilder.registerPackageToolFactory(ExternalPackageTool.class, ExternalPackageTool::new);
        HarnessConfigBuilder.registerPackageRailFactory(ExternalPackageRail.class, ExternalPackageRail::new);
        String yaml = HarnessConfigBuilder.generateHarnessConfigYaml(
                AgentCard.builder().id("external-package").name("External Package").build(), "prompt",
                List.of(new ExternalPackageTool(tempDir)), List.of(new ExternalPackageRail()), "en", 3, 1.0);

        assertThat(yaml).contains("type: package", "module: org.example.harness",
                "class: ExternalPackageTool", "class: ExternalPackageRail");
        Path configPath = tempDir.resolve("external-package.yaml");
        Files.writeString(configPath, yaml);
        try (DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(configPath))) {
            assertThat(agent.getConfig().getTools()).singleElement().isInstanceOf(ExternalPackageTool.class);
            assertThat(agent.getConfig().getRails()).anyMatch(ExternalPackageRail.class::isInstance);
        }

        Path rejectedPath = tempDir.resolve("unregistered-package.yaml");
        Files.writeString(rejectedPath, """
                schema_version: harness_config.v0.1
                name: Unregistered Package
                resources:
                  tools:
                    - type: package
                      module: org.example.harness
                      class: UnregisteredPackageTool
                """);
        assertThatThrownBy(() -> HarnessConfigBuilder.build(HarnessConfigLoader.load(rejectedPath)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Package tool factory is not registered")
                .hasMessageContaining("org.example.harness.UnregisteredPackageTool");
    }
}

record ExternalProviderTool(Path workspaceRoot) {
}

final class ExternalProviderRail {
}

record ExternalPackageTool(Path workspaceRoot) {
}

final class ExternalPackageRail {
}

final class UnregisteredPackageTool {
}
