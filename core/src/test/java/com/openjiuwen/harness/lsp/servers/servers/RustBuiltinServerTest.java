/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import com.openjiuwen.harness.lsp.InitializeOptions;
import com.openjiuwen.harness.lsp.core.ScopedLspServerConfig;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RustBuiltinServerTest {

    @TempDir
    Path tempDir;

    @Test
    void rustRootPromotesToWorkspaceCargoToml() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Files.writeString(workspace.resolve("Cargo.toml"), "[workspace]\n");
        Path crate = Files.createDirectories(workspace.resolve("crates").resolve("demo").resolve("src"));
        Files.writeString(crate.getParent().resolve("Cargo.toml"), "[package]\nname = \"demo\"\n");
        Path file = Files.createFile(crate.resolve("lib.rs"));

        String root = RustBuiltinServer.rustRoot(file.toString());

        assertThat(root).isEqualTo(workspace.toString());
    }

    @Test
    void serverDefinitionMetadataMatchesPython() {
        ServerDefinition definition = RustBuiltinServer.server();

        assertThat(definition.getId()).isEqualTo("rust");
        assertThat(definition.getExtensions()).containsExactly(".rs");
        assertThat(definition.getLanguageId()).isEqualTo("rust");
        assertThat(definition.getPriority()).isEqualTo(10);
    }

    @Test
    void registryLoadsRustBuiltinWhenEmpty() {
        Map<String, ServerDefinition> snapshot = new LinkedHashMap<>(BuiltinServerRegistry.BUILTIN_SERVERS);
        try {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(
                    new InitializeOptions(),
                    tempDir.toString()
            );

            assertThat(configs).extracting(ScopedLspServerConfig::getServerId).contains("rust");
            ScopedLspServerConfig rust = configs.stream()
                    .filter(config -> "rust".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(rust.getExtensionToLanguage()).containsEntry(".rs", "rust");
        } finally {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();
            BuiltinServerRegistry.BUILTIN_SERVERS.putAll(snapshot);
        }
    }
}
