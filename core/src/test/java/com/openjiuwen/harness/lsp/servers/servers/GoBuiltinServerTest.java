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

class GoBuiltinServerTest {

    @TempDir
    Path tempDir;

    @Test
    void goRootPrefersGoWorkWorkspace() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Files.createFile(workspace.resolve("go.work"));
        Path module = Files.createDirectories(workspace.resolve("module"));
        Files.createFile(module.resolve("go.mod"));
        Path file = Files.createFile(module.resolve("main.go"));

        String root = GoBuiltinServer.goRoot(file.toString());

        assertThat(root).isEqualTo(workspace.toString());
    }

    @Test
    void serverDefinitionMetadataMatchesPython() {
        ServerDefinition definition = GoBuiltinServer.server();

        assertThat(definition.getId()).isEqualTo("gopls");
        assertThat(definition.getExtensions()).containsExactly(".go");
        assertThat(definition.getLanguageId()).isEqualTo("go");
        assertThat(definition.getPriority()).isEqualTo(10);
    }

    @Test
    void registryLoadsGoBuiltinWhenEmpty() {
        Map<String, ServerDefinition> snapshot = new LinkedHashMap<>(BuiltinServerRegistry.BUILTIN_SERVERS);
        try {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(
                    new InitializeOptions(),
                    tempDir.toString()
            );

            assertThat(configs).extracting(ScopedLspServerConfig::getServerId).contains("gopls");
            ScopedLspServerConfig go = configs.stream()
                    .filter(config -> "gopls".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(go.getExtensionToLanguage()).containsEntry(".go", "go");
        } finally {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();
            BuiltinServerRegistry.BUILTIN_SERVERS.putAll(snapshot);
        }
    }
}
