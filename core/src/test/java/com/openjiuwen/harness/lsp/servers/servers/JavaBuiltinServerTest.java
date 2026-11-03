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

class JavaBuiltinServerTest {

    @TempDir
    Path tempDir;

    @Test
    void serverDefinitionFindsMavenOrGradleProjectRoot() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createFile(project.resolve("pom.xml"));
        Path nested = Files.createDirectories(project.resolve("src").resolve("main").resolve("java"));
        Path file = Files.createFile(nested.resolve("Example.java"));

        String root = JavaBuiltinServer.server().getFindRoot().apply(file.toString());

        assertThat(root).isEqualTo(project.toString());
    }

    @Test
    void serverDefinitionMetadataMatchesPython() {
        ServerDefinition definition = JavaBuiltinServer.server();

        assertThat(definition.getId()).isEqualTo("jdtls");
        assertThat(definition.getExtensions()).containsExactly(".java");
        assertThat(definition.getLanguageId()).isEqualTo("java");
        assertThat(definition.getPriority()).isEqualTo(10);
    }

    @Test
    void registryLoadsJavaBuiltinWhenEmpty() {
        Map<String, ServerDefinition> snapshot = new LinkedHashMap<>(BuiltinServerRegistry.BUILTIN_SERVERS);
        try {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(
                    new InitializeOptions(),
                    tempDir.toString()
            );

            assertThat(configs).extracting(ScopedLspServerConfig::getServerId).contains("jdtls");
            ScopedLspServerConfig java = configs.stream()
                    .filter(config -> "jdtls".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(java.getExtensionToLanguage()).containsEntry(".java", "java");
        } finally {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();
            BuiltinServerRegistry.BUILTIN_SERVERS.putAll(snapshot);
        }
    }
}
