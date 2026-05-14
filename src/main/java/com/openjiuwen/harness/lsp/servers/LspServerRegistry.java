/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.core.ScopedLspServerConfig;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal builtin registry for Java harness LSP configs.
 *
 * <p>Mirrors Python's builtin registry flow in
 * {@code openjiuwen.harness.lsp.servers.registry}.
 */
public final class LspServerRegistry {

    private static final Map<String, ScopedLspServerConfig> BUILTIN_SERVERS = new LinkedHashMap<>();
    private static final Map<String, LspServerDefinition> DEFINITIONS = new LinkedHashMap<>();

    private LspServerRegistry() {
    }

    public static void register(String serverId, ScopedLspServerConfig config) {
        if (serverId == null || serverId.isBlank() || config == null) {
            return;
        }
        BUILTIN_SERVERS.put(serverId, config);
    }

    public static Map<String, ScopedLspServerConfig> list() {
        return new LinkedHashMap<>(BUILTIN_SERVERS);
    }

    public static Map<String, LspServerDefinition> definitions() {
        return new LinkedHashMap<>(DEFINITIONS);
    }

    public static List<LspServerDefinition> matchByExtension(String extension) {
        List<LspServerDefinition> matches = new ArrayList<>();
        for (LspServerDefinition definition : DEFINITIONS.values()) {
            if (definition.getExtensions().contains(extension)) {
                matches.add(definition);
            }
        }
        matches.sort(Comparator.comparingInt(LspServerDefinition::getPriority));
        return matches;
    }

    public static synchronized Map<String, ScopedLspServerConfig> bootstrapDefaults(String workspaceFolder) {
        if (BUILTIN_SERVERS.isEmpty()) {
            register("python", create("python", workspaceFolder, Map.of("py", "python")));
            define(new LspServerDefinition(
                    "python",
                    List.of(".py"),
                    "python",
                    10,
                    false,
                    LspRootResolvers.nearestRoot(List.of("pyproject.toml", "setup.py", "requirements.txt", ".git"), List.of(), workspaceFolder)
            ));
            register("java", create("java", workspaceFolder, Map.of("java", "java")));
            define(new LspServerDefinition(
                    "java",
                    List.of(".java"),
                    "java",
                    10,
                    false,
                    LspRootResolvers.nearestRoot(List.of("pom.xml", "build.gradle", "build.gradle.kts", ".project"), List.of(".git"), workspaceFolder)
            ));
            register("typescript", create("typescript", workspaceFolder, Map.of(
                    "ts", "typescript",
                    "tsx", "typescriptreact",
                    "js", "javascript",
                    "jsx", "javascriptreact"
            )));
            define(new LspServerDefinition(
                    "typescript",
                    List.of(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs"),
                    "typescript",
                    10,
                    false,
                    LspRootResolvers.nearestRoot(List.of("package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock"), List.of("deno.json", "deno.jsonc"), workspaceFolder)
            ));
            register("go", create("go", workspaceFolder, Map.of("go", "go")));
            define(new LspServerDefinition(
                    "go",
                    List.of(".go"),
                    "go",
                    10,
                    false,
                    LspRootResolvers.nearestRoot(List.of("go.mod", "go.work", ".git"), List.of(), workspaceFolder)
            ));
            register("rust", create("rust", workspaceFolder, Map.of("rs", "rust")));
            define(new LspServerDefinition(
                    "rust",
                    List.of(".rs"),
                    "rust",
                    10,
                    false,
                    LspRootResolvers.nearestRoot(List.of("Cargo.toml", "rust-project.json", ".git"), List.of(), workspaceFolder)
            ));
        }
        return list();
    }

    private static void define(LspServerDefinition definition) {
        if (definition != null && definition.getId() != null && !definition.getId().isBlank()) {
            DEFINITIONS.put(definition.getId(), definition);
        }
    }

    private static ScopedLspServerConfig create(String serverId, String workspaceFolder, Map<String, String> extensionMap) {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId(serverId);
        config.setCommand("builtin-text-index");
        config.setWorkspaceFolder(workspaceFolder);
        config.setExtensionToLanguage(extensionMap);
        return config;
    }
}
