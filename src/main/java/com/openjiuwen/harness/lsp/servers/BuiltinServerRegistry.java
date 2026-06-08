/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.CustomServerConfig;
import com.openjiuwen.harness.lsp.InitializeOptions;
import com.openjiuwen.harness.lsp.core.ScopedLspServerConfig;
import com.openjiuwen.harness.lsp.core.SpawnHandle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Mirrors Python's module globals and helpers in
 * {@code openjiuwen/harness/lsp/servers/registry.py}.
 */
public final class BuiltinServerRegistry {

    public static final Map<String, ServerDefinition> BUILTIN_SERVERS = new LinkedHashMap<>();

    private BuiltinServerRegistry() {
    }

    public static Function<String, String> nearestRoot(List<String> includePatterns) {
        return nearestRoot(includePatterns, null, null);
    }

    public static Function<String, String> nearestRoot(
            List<String> includePatterns,
            List<String> excludePatterns,
            String stopDir
    ) {
        List<String> includes = includePatterns == null ? List.of() : List.copyOf(includePatterns);
        List<String> excludes = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
        return filePath -> findNearestRoot(filePath, includes, excludes, stopDir);
    }

    public static CompletableFuture<List<ScopedLspServerConfig>> buildConfigsAsync(
            InitializeOptions options,
            String cwd
    ) {
        return CompletableFuture.supplyAsync(() -> buildConfigsInternal(options, cwd));
    }

    public static List<ScopedLspServerConfig> buildConfigs(
            InitializeOptions options,
            String cwd
    ) {
        return buildConfigsAsync(options, cwd).join();
    }

    private static List<ScopedLspServerConfig> buildConfigsInternal(
            InitializeOptions options,
            String cwd
    ) {
        InitializeOptions effectiveOptions = options == null ? new InitializeOptions() : options;
        List<ScopedLspServerConfig> configs = new ArrayList<>();

        for (Map.Entry<String, ServerDefinition> entry : BUILTIN_SERVERS.entrySet()) {
            String serverId = entry.getKey();
            ServerDefinition serverDefinition = entry.getValue();
            SpawnHandle spawnHandle = serverDefinition.getSpawn() == null ? null : serverDefinition.getSpawn().apply(cwd);
            configs.add(buildConfig(serverId, serverDefinition, spawnHandle, cwd));
        }

        Map<String, CustomServerConfig> customServers = effectiveOptions.getCustomServers();
        if (customServers != null) {
            for (Map.Entry<String, CustomServerConfig> entry : customServers.entrySet()) {
                String serverId = entry.getKey();
                CustomServerConfig custom = entry.getValue();
                if (custom == null) {
                    continue;
                }

                if (custom.isDisabled()) {
                    configs.removeIf(config -> Objects.equals(config.getServerId(), serverId));
                    continue;
                }

                ScopedLspServerConfig existing = configs.stream()
                        .filter(config -> Objects.equals(config.getServerId(), serverId))
                        .findFirst()
                        .orElse(null);
                if (existing != null) {
                    if (custom.getCommand() != null) {
                        existing.setCommand(custom.getCommand());
                    }
                    if (custom.getArgs() != null) {
                        existing.setArgs(custom.getArgs());
                    }
                    if (custom.getInitializationOptions() != null) {
                        existing.setInitializationOptions(custom.getInitializationOptions());
                    }
                    continue;
                }

                ScopedLspServerConfig config = new ScopedLspServerConfig();
                config.setServerId(serverId);
                config.setCommand(custom.getCommand() == null ? "" : custom.getCommand());
                config.setArgs(custom.getArgs() == null ? List.of() : custom.getArgs());
                config.setEnv(custom.getEnv() == null ? Map.of() : custom.getEnv());
                config.setWorkspaceFolder(cwd);
                config.setInitializationOptions(custom.getInitializationOptions());
                config.setExtensionToLanguage(buildExtensionMap(custom.getExtensions(), custom.getLanguageId(), serverId));
                configs.add(config);
            }
        }

        return configs;
    }

    private static ScopedLspServerConfig buildConfig(
            String serverId,
            ServerDefinition serverDefinition,
            SpawnHandle spawnHandle,
            String cwd
    ) {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId(serverId);
        config.setWorkspaceFolder(cwd);
        config.setExtensionToLanguage(buildExtensionMap(serverDefinition.getExtensions(), serverDefinition.getLanguageId(), null));

        if (spawnHandle == null) {
            config.setCommand("");
            config.setArgs(List.of());
            config.setEnv(Map.of());
            config.setInitializationOptions(null);
            config.setStartupTimeout(45_000);
            return config;
        }

        config.setCommand(spawnHandle.getCommand());
        config.setArgs(spawnHandle.getArgs());
        config.setEnv(spawnHandle.getEnv());
        config.setInitializationOptions(spawnHandle.getInitializationOptions());
        config.setStartupTimeout(spawnHandle.getStartupTimeout());
        return config;
    }

    private static Map<String, String> buildExtensionMap(
            List<String> extensions,
            String languageId,
            String defaultLanguageId
    ) {
        Map<String, String> extensionMap = new LinkedHashMap<>();
        if (extensions == null) {
            return extensionMap;
        }
        String resolvedLanguageId = languageId == null ? defaultLanguageId : languageId;
        for (String extension : extensions) {
            extensionMap.put(extension, resolvedLanguageId);
        }
        return extensionMap;
    }

    private static String findNearestRoot(
            String filePath,
            List<String> includePatterns,
            List<String> excludePatterns,
            String stopDir
    ) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        Path startDir;
        try {
            startDir = Path.of(filePath).toAbsolutePath().normalize().getParent();
        } catch (RuntimeException ex) {
            return null;
        }
        if (startDir == null) {
            return null;
        }

        Path stop = stopDir == null || stopDir.isBlank()
                ? Path.of("").toAbsolutePath().normalize()
                : Path.of(stopDir).toAbsolutePath().normalize();

        if (containsAny(startDir, includePatterns)) {
            return startDir.toString();
        }

        Path current = startDir.getParent();
        while (current != null) {
            if (containsAny(current, includePatterns)) {
                return current.toString();
            }
            if (current.equals(stop)) {
                return null;
            }
            if (containsAny(current, excludePatterns)) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean containsAny(Path directory, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && directory.resolve(pattern).toFile().exists()) {
                return true;
            }
        }
        return false;
    }
}
