/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the runtime extension loader.
 * <p>
 * Mirrors Python's runtime loader behavior in
 * {@code openjiuwen/auto_harness/infra/runtime_extension_loader.py}.
 */
class RuntimeExtensionLoaderTest {

    @TempDir
    private Path tempDir;

    @Test
    void loadPackageRailsToolsAndExistingSkillDirs() throws Exception {
        Path runtimeRoot = Files.createDirectories(tempDir.resolve("runtime"));
        Path existingSkillDir = Files.createDirectories(runtimeRoot.resolve("skills").resolve("active"));
        Path configPath = writeConfig("""
                resources:
                  rails:
                    - type: package
                      module: openjiuwen.extensions.harness.demo.rails
                      class: com.openjiuwen.auto_harness.infra.RuntimeExtensionLoaderTest$SampleRail
                    - type: builtin
                      module: openjiuwen.extensions.harness.demo.rails
                      class: com.openjiuwen.auto_harness.infra.RuntimeExtensionLoaderTest$IgnoredRail
                    - type: package
                      module: openjiuwen.extensions.harness.demo.rails.missing
                  tools:
                    - type: package
                      module: openjiuwen.extensions.harness.demo.tools
                      class: com.openjiuwen.auto_harness.infra.RuntimeExtensionLoaderTest$SampleTool
                    - type: function
                      module: openjiuwen.extensions.harness.demo.tools
                      class: com.openjiuwen.auto_harness.infra.RuntimeExtensionLoaderTest$IgnoredTool
                    - type: package
                      class: com.openjiuwen.auto_harness.infra.RuntimeExtensionLoaderTest$IgnoredTool
                  skills:
                    dirs:
                      - skills/active
                      - skills/missing
                """);
        RuntimeExtensionArtifact artifact = artifact(runtimeRoot, configPath);

        List<Class<?>> rails = RuntimeExtensionLoader.loadRuntimeRails(artifact, "session-1");
        List<Class<?>> tools = RuntimeExtensionLoader.loadRuntimeTools(artifact, "session-1");
        List<String> skillDirs = RuntimeExtensionLoader.loadRuntimeSkillDirs(artifact);

        assertThat(rails).containsExactly(SampleRail.class);
        assertThat(tools).containsExactly(SampleTool.class);
        assertThat(skillDirs).containsExactly(existingSkillDir.toAbsolutePath().normalize().toString());
    }

    @Test
    void loadRuntimeToolsRejectsModuleOutsideExtensionPrefix() throws Exception {
        Path runtimeRoot = Files.createDirectories(tempDir.resolve("runtime"));
        Path configPath = writeConfig("""
                resources:
                  tools:
                    - type: package
                      module: openjiuwen.extensions.harness.other.tools
                      class: com.openjiuwen.auto_harness.infra.RuntimeExtensionLoaderTest$SampleTool
                """);
        RuntimeExtensionArtifact artifact = artifact(runtimeRoot, configPath);

        assertThatThrownBy(() -> RuntimeExtensionLoader.loadRuntimeTools(artifact, "session-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Runtime module does not belong to runtime extension 'demo'");
    }

    @Test
    void loadRuntimeSkillDirsReturnsEmptyWhenResourcesAreAbsent() throws Exception {
        Path runtimeRoot = Files.createDirectories(tempDir.resolve("runtime"));
        Path configPath = writeConfig("""
                language: en
                """);
        RuntimeExtensionArtifact artifact = artifact(runtimeRoot, configPath);

        assertThat(RuntimeExtensionLoader.loadRuntimeSkillDirs(artifact)).isEmpty();
    }

    private Path writeConfig(String config) throws Exception {
        Path configPath = tempDir.resolve("harness_config.yaml");
        Files.writeString(configPath, config);
        return configPath;
    }

    private RuntimeExtensionArtifact artifact(Path runtimeRoot, Path configPath) {
        return RuntimeExtensionArtifact.builder()
                .extensionName("demo")
                .runtimePath(runtimeRoot.toString())
                .configPath(configPath.toString())
                .build();
    }

    /**
     * Mirrors Python's runtime class object returned from
     * {@code openjiuwen/auto_harness/infra/runtime_extension_loader.py}.
     */
    static final class SampleRail {
    }

    /**
     * Mirrors Python's runtime class object returned from
     * {@code openjiuwen/auto_harness/infra/runtime_extension_loader.py}.
     */
    static final class SampleTool {
    }

    /**
     * Mirrors Python's skipped non-package rail entry in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_loader.py}.
     */
    static final class IgnoredRail {
    }

    /**
     * Mirrors Python's skipped non-package tool entry in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_loader.py}.
     */
    static final class IgnoredTool {
    }
}
