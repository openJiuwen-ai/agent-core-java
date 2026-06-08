/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.CustomServerConfig;
import com.openjiuwen.harness.lsp.InitializeOptions;
import com.openjiuwen.harness.lsp.core.ScopedLspServerConfig;
import com.openjiuwen.harness.lsp.core.SpawnHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinServerRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void nearestRootChecksStartingDirectoryBeforeStopOrExclude() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createFile(project.resolve("go.mod"));
        Files.createDirectory(project.resolve(".git"));
        Path file = Files.createFile(project.resolve("main.go"));

        String root = BuiltinServerRegistry.nearestRoot(
                List.of("go.mod"),
                List.of(".git"),
                project.toString()
        ).apply(file.toString());

        assertThat(root).isEqualTo(project.toString());
    }

    @Test
    void nearestRootStopsWhenExcludedAncestorIsReached() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createDirectory(project.resolve(".git"));
        Path nested = Files.createDirectories(project.resolve("src").resolve("pkg"));
        Path file = Files.createFile(nested.resolve("main.ts"));

        String root = BuiltinServerRegistry.nearestRoot(
                List.of("package.json"),
                List.of(".git"),
                tempDir.toString()
        ).apply(file.toString());

        assertThat(root).isNull();
    }

    @Test
    void buildConfigsAsyncCreatesPlaceholderWhenSpawnIsMissing() {
        Map<String, ServerDefinition> snapshot = snapshotRegistry();
        try {
            ServerDefinition definition = new ServerDefinition();
            definition.setId("python");
            definition.setExtensions(List.of(".py"));
            definition.setLanguageId("python");
            definition.setSpawn(path -> null);
            BuiltinServerRegistry.BUILTIN_SERVERS.put(definition.getId(), definition);

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry
                    .buildConfigsAsync(new InitializeOptions(), tempDir.toString())
                    .join();

            assertThat(configs).hasSize(1);
            ScopedLspServerConfig config = configs.get(0);
            assertThat(config.getServerId()).isEqualTo("python");
            assertThat(config.getCommand()).isEmpty();
            assertThat(config.getArgs()).isEmpty();
            assertThat(config.getWorkspaceFolder()).isEqualTo(tempDir.toString());
            assertThat(config.getExtensionToLanguage()).containsEntry(".py", "python");
        } finally {
            restoreRegistry(snapshot);
        }
    }

    @Test
    void buildConfigsAppliesCustomOverridesAndAddsCustomServers() {
        Map<String, ServerDefinition> snapshot = snapshotRegistry();
        try {
            ServerDefinition builtin = new ServerDefinition();
            builtin.setId("python");
            builtin.setExtensions(List.of(".py"));
            builtin.setLanguageId("python");
            builtin.setSpawn(path -> {
                SpawnHandle handle = new SpawnHandle();
                handle.setCommand("pyright-langserver");
                handle.setArgs(List.of("--stdio"));
                handle.setInitializationOptions(Map.of("builtin", true));
                return handle;
            });
            BuiltinServerRegistry.BUILTIN_SERVERS.put(builtin.getId(), builtin);

            CustomServerConfig override = new CustomServerConfig();
            override.setCommand("custom-pyright");
            override.setArgs(List.of("--tcp"));
            override.setInitializationOptions(Map.of("custom", true));

            CustomServerConfig customServer = new CustomServerConfig();
            customServer.setCommand("ruff");
            customServer.setArgs(List.of("server"));
            customServer.setEnv(Map.of("RUFF_TRACE", "1"));
            customServer.setExtensions(List.of(".pyi"));
            customServer.setLanguageId("python");

            InitializeOptions options = new InitializeOptions();
            Map<String, CustomServerConfig> customServers = new LinkedHashMap<>();
            customServers.put("python", override);
            customServers.put("ruff", customServer);
            options.setCustomServers(customServers);

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(options, tempDir.toString());

            assertThat(configs).hasSize(2);
            ScopedLspServerConfig python = configs.stream()
                    .filter(config -> "python".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(python.getCommand()).isEqualTo("custom-pyright");
            assertThat(python.getArgs()).containsExactly("--tcp");
            assertThat(python.getInitializationOptions()).containsEntry("custom", true);

            ScopedLspServerConfig ruff = configs.stream()
                    .filter(config -> "ruff".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(ruff.getCommand()).isEqualTo("ruff");
            assertThat(ruff.getArgs()).containsExactly("server");
            assertThat(ruff.getEnv()).containsEntry("RUFF_TRACE", "1");
            assertThat(ruff.getExtensionToLanguage()).containsEntry(".pyi", "python");
        } finally {
            restoreRegistry(snapshot);
        }
    }

    @Test
    void buildConfigsRemovesDisabledBuiltin() {
        Map<String, ServerDefinition> snapshot = snapshotRegistry();
        try {
            ServerDefinition builtin = new ServerDefinition();
            builtin.setId("typescript");
            builtin.setExtensions(List.of(".ts"));
            builtin.setLanguageId("typescript");
            builtin.setSpawn(path -> new SpawnHandle());
            BuiltinServerRegistry.BUILTIN_SERVERS.put(builtin.getId(), builtin);

            CustomServerConfig disabled = new CustomServerConfig();
            disabled.setDisabled(true);
            InitializeOptions options = new InitializeOptions();
            options.setCustomServers(Map.of("typescript", disabled));

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(options, tempDir.toString());

            assertThat(configs).isEmpty();
        } finally {
            restoreRegistry(snapshot);
        }
    }

    private static Map<String, ServerDefinition> snapshotRegistry() {
        return new LinkedHashMap<>(BuiltinServerRegistry.BUILTIN_SERVERS);
    }

    private static void restoreRegistry(Map<String, ServerDefinition> snapshot) {
        BuiltinServerRegistry.BUILTIN_SERVERS.clear();
        BuiltinServerRegistry.BUILTIN_SERVERS.putAll(snapshot);
    }
}
