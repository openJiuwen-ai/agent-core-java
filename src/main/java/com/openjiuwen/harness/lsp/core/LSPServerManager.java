/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.InitializeOptions;
import com.openjiuwen.harness.lsp.InitializeResult;
import com.openjiuwen.harness.lsp.LspStatus;
import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Global singleton LSP server manager with lazy-loading server startup.
 * <p>
 * Mirrors Python's {@code LSPServerManager} in
 * {@code openjiuwen/harness/lsp/core/manager.py}.
 * </p>
 */
public class LspServerManager {

    private static LspServerManager instance;

    private String workspaceRoot = "";
    private Map<String, List<ScopedLspServerConfig>> configs = new LinkedHashMap<>();
    private Map<ServerInstanceKey, LspServerInstance> instances = new LinkedHashMap<>();
    private Map<ServerInstanceKey, Thread> spawning = new LinkedHashMap<>();
    private Map<String, List<String>> extensionMap = new LinkedHashMap<>();
    private final Set<LspServerInstance> diagnosticHandlerInstances =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Integer> documentVersions = new LinkedHashMap<>();

    public static synchronized InitializeResult initialize() {
        return initialize(null);
    }

    public static synchronized InitializeResult initialize(InitializeOptions options) {
        InitializeOptions opts = options == null ? new InitializeOptions() : options;
        if (instance != null) {
            return initializeResult(true, instance.getStatus().size(), 0.0d);
        }

        String cwd = resolveCwd(opts.getCwd());
        List<ScopedLspServerConfig> builtConfigs = BuiltinServerRegistry.buildConfigs(opts, cwd);
        if (builtConfigs.isEmpty()) {
            instance = null;
            return initializeResult(true, 0, 0.0d);
        }

        LspServerManager manager = new LspServerManager();
        manager.workspaceRoot = cwd;
        manager.configs = groupConfigs(builtConfigs);
        manager.extensionMap = buildExtensionMap(builtConfigs);
        instance = manager;
        return initializeResult(true, builtConfigs.size(), 0.0d);
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            instance.stopAll();
            instance = null;
        }
    }

    public static synchronized LspServerManager getInstance() {
        return instance;
    }

    public static synchronized LspStatus getGlobalStatus() {
        LspStatus status = new LspStatus();
        status.setInitialized(instance != null);
        status.setServers(instance == null ? List.of() : instance.getStatus());
        return status;
    }

    public void stopAll() {
        for (Thread task : new ArrayList<>(spawning.values())) {
            task.interrupt();
        }
        spawning.clear();
        for (LspServerInstance serverInstance : new ArrayList<>(instances.values())) {
            try {
                serverInstance.stop();
            } catch (RuntimeException ignored) {
                // Continue stopping the remaining server instances.
            }
        }
        instances.clear();
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public LspServerInstance getOrStartServer(String filePath) {
        String extension = extensionOf(filePath);
        List<String> serverIds = extensionMap.getOrDefault(extension, List.of());

        for (String serverId : serverIds) {
            ServerDefinition serverDefinition = BuiltinServerRegistry.BUILTIN_SERVERS.get(serverId);
            if (serverDefinition == null) {
                continue;
            }
            String root = serverDefinition.getFindRoot().apply(filePath);
            if (root == null || root.isBlank()) {
                continue;
            }

            for (ScopedLspServerConfig config : configs.getOrDefault(serverId, List.of())) {
                ServerInstanceKey key = new ServerInstanceKey(serverId, root);
                LspServerInstance existing = instances.get(key);
                if (existing != null) {
                    if (existing.isRunning()) {
                        if (existing.isHealthy()) {
                            return existing;
                        }
                        instances.remove(key);
                    } else if (existing.getState() == LspServerState.ERROR) {
                        instances.remove(key);
                    } else if (spawning.containsKey(key)) {
                        Thread thread = spawning.get(key);
                        joinQuietly(thread);
                        LspServerInstance spawned = instances.get(key);
                        if (spawned != null && spawned.isRunning()) {
                            return spawned;
                        }
                    }
                }

                LspServerInstance started = startServer(key, config, root);
                if (started != null && started.isRunning()) {
                    return started;
                }
            }
        }
        return null;
    }

    public LspServerInstance startServer(ServerInstanceKey key, ScopedLspServerConfig config, String root) {
        LspServerInstance existing = instances.get(key);
        if (existing != null && existing.isRunning()) {
            return existing;
        }
        if (spawning.containsKey(key)) {
            joinQuietly(spawning.get(key));
            return instances.get(key);
        }

        ScopedLspServerConfig activeConfig = root != null && !Objects.equals(root, config.getWorkspaceFolder())
                ? cloneForRoot(config, root)
                : config;
        LspServerInstance serverInstance = new LspServerInstance(
                activeConfig,
                error -> logServerError(activeConfig.getServerId(), error)
        );
        instances.put(key, serverInstance);
        try {
            serverInstance.start();
        } catch (RuntimeException exception) {
            logServerError(activeConfig.getServerId(), exception);
            instances.remove(key);
            return null;
        }
        return serverInstance.isRunning() ? serverInstance : null;
    }

    public static boolean pathBelongsToRoot(String filePath, String root) {
        try {
            Path absoluteFile = Path.of(filePath).toAbsolutePath().normalize();
            Path absoluteRoot = Path.of(root).toAbsolutePath().normalize();
            absoluteRoot.relativize(absoluteFile);
            return absoluteFile.startsWith(absoluteRoot);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static void logServerError(String serverId, Exception error) {
        System.getLogger(LspServerManager.class.getName()).log(
                System.Logger.Level.WARNING,
                "[LSP] Server '" + serverId + "' failed: " + error
        );
    }

    public void ensureDiagnosticHandler(LspServerInstance server) {
        if (server == null || diagnosticHandlerInstances.contains(server)) {
            return;
        }
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        String serverName = server.getConfig().getServerId();
        server.addNotificationHandler("textDocument/publishDiagnostics", params -> {
            if (!(params instanceof Map<?, ?> map)) {
                return;
            }
            Object uriValue = map.get("uri");
            Object diagnosticsValue = map.get("diagnostics");
            if (uriValue == null || !(diagnosticsValue instanceof List<?> diagnostics)) {
                return;
            }
            registry.register(serverName, String.valueOf(uriValue), diagnostics);
        });
        diagnosticHandlerInstances.add(server);
    }

    public static List<LspDiagnosticFile> getPendingDiagnostics(int maxPerFile, int maxTotal) {
        return LspDiagnosticRegistry.getInstance().getAndClear(maxPerFile, maxTotal);
    }

    public boolean isFileOpen(String uri) {
        return documentVersions.containsKey(uri);
    }

    public void openFile(String filePath, String languageId) {
        LspServerInstance server = getOrStartServer(filePath);
        if (server == null) {
            return;
        }
        ensureDiagnosticHandler(server);
        String text;
        try {
            text = Path.of(filePath).toAbsolutePath().normalize().toFile().isFile()
                    ? java.nio.file.Files.readString(Path.of(filePath), StandardCharsets.UTF_8)
                    : "";
        } catch (IOException exception) {
            text = "";
        }

        String uri = FileUriUtils.pathToFileUri(filePath);
        documentVersions.put(uri, 0);
        server.sendNotification(
                "textDocument/didOpen",
                Map.of(
                        "textDocument",
                        Map.of(
                                "uri", uri,
                                "languageId", languageId,
                                "version", 0,
                                "text", text
                        )
                )
        );
    }

    public void changeFile(String filePath, String languageId, String content) {
        LspServerInstance server = getOrStartServer(filePath);
        if (server == null) {
            return;
        }
        ensureDiagnosticHandler(server);
        String resolvedContent = content;
        if (resolvedContent == null) {
            try {
                resolvedContent = java.nio.file.Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                resolvedContent = "";
            }
        }

        String uri = FileUriUtils.pathToFileUri(filePath);
        int version = documentVersions.getOrDefault(uri, 0) + 1;
        documentVersions.put(uri, version);
        server.sendNotification(
                "textDocument/didChange",
                Map.of(
                        "textDocument",
                        Map.of("uri", uri, "version", version),
                        "contentChanges",
                        List.of(Map.of("text", resolvedContent))
                )
        );
    }

    public Object sendRequest(String filePath, String method, Map<String, Object> params) {
        LspServerInstance server = getOrStartServer(filePath);
        if (server == null) {
            throw new RuntimeException("No LSP server for file: " + filePath);
        }
        return server.sendRequest(method, params == null ? Map.of() : params);
    }

    public List<LspServerStatus> getStatus() {
        List<LspServerStatus> statuses = new ArrayList<>();
        for (LspServerInstance serverInstance : instances.values()) {
            LspServerStatus status = new LspServerStatus();
            status.setServerId(serverInstance.getConfig().getServerId());
            status.setName(serverInstance.getConfig().getServerId());
            status.setRunning(serverInstance.isRunning());
            status.setState(serverInstance.getState());
            status.setRoot(serverInstance.getConfig().getWorkspaceFolder());
            status.setCrashCount(serverInstance.getCrashCount());
            status.setLastError(serverInstance.getLastError() == null ? null : serverInstance.getLastError().toString());
            statuses.add(status);
        }
        return statuses;
    }

    private static InitializeResult initializeResult(boolean success, int serversLoaded, double durationMs) {
        InitializeResult result = new InitializeResult();
        result.setSuccess(success);
        result.setServersLoaded(serversLoaded);
        result.setDurationMs(durationMs);
        return result;
    }

    private static String resolveCwd(String cwd) {
        try {
            String value = cwd == null || cwd.isBlank() ? System.getProperty("user.dir") : cwd;
            return Path.of(value).toAbsolutePath().normalize().toString();
        } catch (RuntimeException exception) {
            return System.getProperty("user.dir");
        }
    }

    private static Map<String, List<ScopedLspServerConfig>> groupConfigs(List<ScopedLspServerConfig> builtConfigs) {
        Map<String, List<ScopedLspServerConfig>> grouped = new LinkedHashMap<>();
        for (ScopedLspServerConfig config : builtConfigs) {
            grouped.computeIfAbsent(config.getServerId(), ignored -> new ArrayList<>()).add(config);
        }
        return grouped;
    }

    private static Map<String, List<String>> buildExtensionMap(List<ScopedLspServerConfig> builtConfigs) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (ScopedLspServerConfig config : builtConfigs) {
            for (String extension : config.getExtensionToLanguage().keySet()) {
                String normalized = extension == null ? "" : extension.toLowerCase();
                List<String> serverIds = result.computeIfAbsent(normalized, ignored -> new ArrayList<>());
                if (!serverIds.contains(config.getServerId())) {
                    serverIds.add(config.getServerId());
                }
            }
        }
        return result;
    }

    private static ScopedLspServerConfig cloneForRoot(ScopedLspServerConfig config, String root) {
        ScopedLspServerConfig activeConfig = new ScopedLspServerConfig();
        activeConfig.setServerId(config.getServerId());
        activeConfig.setCommand(config.getCommand());
        activeConfig.setArgs(config.getArgs());
        activeConfig.setEnv(config.getEnv());
        activeConfig.setWorkspaceFolder(root);
        activeConfig.setInitializationOptions(config.getInitializationOptions());
        activeConfig.setStartupTimeout(config.getStartupTimeout());
        activeConfig.setExtensionToLanguage(config.getExtensionToLanguage());
        return activeConfig;
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extensionOf(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        String fileName = Path.of(filePath).getFileName().toString();
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index).toLowerCase() : "";
    }
}
