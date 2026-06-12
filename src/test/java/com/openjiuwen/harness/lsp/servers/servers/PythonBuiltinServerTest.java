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

class PythonBuiltinServerTest {

    @TempDir
    Path tempDir;

    @Test
    void serverDefinitionFindsPythonProjectRoot() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createFile(project.resolve("pyproject.toml"));
        Path nested = Files.createDirectories(project.resolve("pkg").resolve("sub"));
        Path file = Files.createFile(nested.resolve("module.py"));

        String root = PythonBuiltinServer.server().getFindRoot().apply(file.toString());

        assertThat(root).isEqualTo(project.toString());
    }

    @Test
    void buildInitializationOptionsPrefersProjectVenv() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path venvPython = Files.createDirectories(project.resolve(".venv").resolve("Scripts")).resolve("python.exe");
        Files.createFile(venvPython);

        Map<String, Object> options = PythonBuiltinServer.buildInitializationOptions(project.toString());

        assertThat(options).containsEntry("pythonPath", project.resolve(".venv").resolve("Scripts").resolve("python").toString().replace('\\', '/'));
    }

    @Test
    void extractQuotedPathReadsBatchSnippet() {
        assertThat(PythonBuiltinServer.extractQuotedPath("\"..\\node_modules\\pyright\\langserver.index.js\" --stdio"))
                .isEqualTo("..\\node_modules\\pyright\\langserver.index.js");
    }

    @Test
    void registryLoadsPythonBuiltinWhenEmpty() {
        Map<String, ServerDefinition> snapshot = new LinkedHashMap<>(BuiltinServerRegistry.BUILTIN_SERVERS);
        try {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();

            List<ScopedLspServerConfig> configs = BuiltinServerRegistry.buildConfigs(
                    new InitializeOptions(),
                    tempDir.toString()
            );

            assertThat(configs).extracting(ScopedLspServerConfig::getServerId).contains("pyright");
            ScopedLspServerConfig pyright = configs.stream()
                    .filter(config -> "pyright".equals(config.getServerId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(pyright.getExtensionToLanguage()).containsEntry(".py", "python");
            assertThat(pyright.getExtensionToLanguage()).containsEntry(".pyi", "python");
        } finally {
            BuiltinServerRegistry.BUILTIN_SERVERS.clear();
            BuiltinServerRegistry.BUILTIN_SERVERS.putAll(snapshot);
        }
    }
}
