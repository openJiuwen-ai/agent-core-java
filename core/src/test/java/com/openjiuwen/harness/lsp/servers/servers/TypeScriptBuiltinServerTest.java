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

class TypeScriptBuiltinServerTest {

    @TempDir
    Path tempDir;

    @Test
    void serverDefinitionFindsNodeProjectRoot() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createFile(project.resolve("package.json"));
        Path nested = Files.createDirectories(project.resolve("src").resolve("ui"));
        Path file = Files.createFile(nested.resolve("index.ts"));

        String root = TypeScriptBuiltinServer.server().getFindRoot().apply(file.toString());

        assertThat(root).isEqualTo(project.toString());
    }

    @Test
    void buildArgsAddsIgnoreNodeModulesWhenNoConfigExists() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));

        assertThat(TypeScriptBuiltinServer.buildArgs(project.toString()))
                .containsExactly("--stdio", "--ignore-node-modules");

        Files.createFile(project.resolve("tsconfig.json"));
        assertThat(TypeScriptBuiltinServer.buildArgs(project.toString()))
                .containsExactly("--stdio");
    }

    @Test
    void registryLoadsTypeScriptBuiltinWhenEmpty() {
        Map<String, ServerDefinition> snapshot = new LinkedHashMap<>(BuiltinServerRegistry.BUILTIN_SERVERS);
        try {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(
                    new InitializeOptions(),
                    tempDir.toString()
            );

            assertThat(configs).extracting(ScopedLspServerConfig::getServerId).contains("typescript");
            ScopedLspServerConfig typescript = configs.stream()
                    .filter(config -> "typescript".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(typescript.getExtensionToLanguage()).containsEntry(".ts", "typescript");
            assertThat(typescript.getExtensionToLanguage()).containsEntry(".jsx", "typescript");
        } finally {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();
            BuiltinServerRegistry.BUILTIN_SERVERS.putAll(snapshot);
        }
    }
}
