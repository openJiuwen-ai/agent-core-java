/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Minimal LSP server manager for didOpen/didChange and diagnostics routing.
 * 
 * @since 0.1.7
 */
public class LSPServerManager {
    private final Map<String, Object> configs = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> instances = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> spawning = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, String> extensionMap = new LinkedHashMap<>();
    private String workspaceRoot;

    /**
     * java.util.HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<Integer> diagHandlerInstances = new java.util.HashSet<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Integer> docVersions = new LinkedHashMap<>();

    /**
     * LSPServerManager.
     * 
     * @since 0.1.7
     */
    public LSPServerManager() {
        this.workspaceRoot = Path.of("").toAbsolutePath().normalize().toString();
    }

    /**
     * ensureDiagnosticHandler.
     * 
     * @param server server
     * @since 0.1.7
     */
    public void ensureDiagnosticHandler(Object server) {
        int identity = System.identityHashCode(server);
        if (diagHandlerInstances.contains(identity)) {
            return;
        }
        diagHandlerInstances.add(identity);
        Consumer<Map<String, Object>> handler = payload -> {
            String uri = String.valueOf(payload.get("uri"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> diagnostics =
                (List<Map<String, Object>>) payload.getOrDefault("diagnostics", List.of());
            LspDiagnosticRegistry.getInstance().register(resolveServerId(server), uri, diagnostics);
        };
        invoke(server, "addNotificationHandler", new Class[]{String.class, Consumer.class},
                new Object[]{"textDocument/publishDiagnostics", handler});
    }

    /**
     * ensureDiagnosticHandlerCompat.
     * 
     * @param server server
     * @since 0.1.7
     */
    public void ensureDiagnosticHandlerCompat(Object server) {
        ensureDiagnosticHandler(server);
    }

    /**
     * getOrStartServer.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    public Object getOrStartServer(String filePath) {
        return instances.get(filePath);
    }

    /**
     * registerServer.
     * 
     * @param filePath filePath
     * @param server server
     * @since 0.1.7
     */
    public void registerServer(String filePath, Object server) {
        instances.put(filePath, server);
    }

    /**
     * hasServer.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    public boolean hasServer(String filePath) {
        return instances.containsKey(filePath);
    }

    /**
     * activeServerCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int activeServerCount() {
        return new HashSet<>(instances.values()).size();
    }

    /**
     * getDocumentVersion.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    public Integer getDocumentVersion(String filePath) {
        return docVersions.get(filePath);
    }

    /**
     * shutdownServer.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    public boolean shutdownServer(String filePath) {
        Object server = instances.remove(filePath);
        docVersions.remove(filePath);
        if (server == null) {
            return false;
        }
        if (!instances.containsValue(server)) {
            shutdownLifecycle(server);
            diagHandlerInstances.remove(System.identityHashCode(server));
        }
        return true;
    }

    /**
     * shutdownAll.
     * 
     * @since 0.1.7
     */
    public void shutdownAll() {
        RuntimeException firstFailure = null;
        Set<Object> servers = new HashSet<>(instances.values());
        for (Object server : servers) {
            try {
                shutdownLifecycle(server);
            } catch (RuntimeException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }
        instances.clear();
        spawning.clear();
        docVersions.clear();
        diagHandlerInstances.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * setWorkspaceRoot.
     * 
     * @param workspaceRoot workspaceRoot
     * @since 0.1.7
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * getWorkspaceRoot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * openFile.
     * 
     * @param filePath filePath
     * @param languageId languageId
     * @since 0.1.7
     */
    public void openFile(String filePath, String languageId) {
        Object server = getOrStartServer(filePath);
        if (server == null) {
            return;
        }
        ensureDiagnosticHandler(server);
        String text = readText(filePath);
        docVersions.put(filePath, 0);
        Map<String, Object> params = Map.of("textDocument",
                Map.of("uri", pathToFileUri(filePath), "languageId", languageId, "version", 0, "text", text));
        invoke(server, "sendNotification", new Class[]{String.class, Map.class},
                new Object[]{"textDocument/didOpen", params});
    }

    /**
     * changeFile.
     * 
     * @param filePath filePath
     * @param languageId languageId
     * @since 0.1.7
     */
    public void changeFile(String filePath, String languageId) {
        changeFile(filePath, languageId, null);
    }

    /**
     * changeFile.
     * 
     * @param filePath filePath
     * @param languageId languageId
     * @param content content
     * @since 0.1.7
     */
    public void changeFile(String filePath, String languageId, String content) {
        Object server = getOrStartServer(filePath);
        if (server == null) {
            return;
        }
        ensureDiagnosticHandler(server);
        String text = content != null ? content : readText(filePath);
        int nextVersion = docVersions.getOrDefault(filePath, 0) + 1;
        docVersions.put(filePath, nextVersion);
        Map<String, Object> params =
            Map.of("textDocument", Map.of("uri", pathToFileUri(filePath), "version", nextVersion), "contentChanges",
                    List.of(Map.of("text", text)));
        invoke(server, "sendNotification", new Class[]{String.class, Map.class},
                new Object[]{"textDocument/didChange", params});
    }

    /**
     * request.
     * 
     * @param filePath filePath
     * @param method method
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    public Object request(String filePath, String method, Map<String, Object> params) {
        Object server = getOrStartServer(filePath);
        if (server == null) {
            return null;
        }
        ensureDiagnosticHandler(server);
        return requestServer(server, method, params == null ? Map.of() : params);
    }

    /**
     * getPendingDiagnostics.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static List<LspDiagnostic> getPendingDiagnostics() {
        return LspDiagnosticRegistry.getInstance().getAndClear();
    }

    /**
     * getPendingDiagnostics.
     * 
     * @param maxPerFile maxPerFile
     * @param maxTotal maxTotal
     * @return the result
     * @since 0.1.7
     */
    public static List<LspDiagnostic> getPendingDiagnostics(int maxPerFile, int maxTotal) {
        List<LspDiagnostic> all = LspDiagnosticRegistry.getInstance().getAndClear();
        Map<String, Integer> perFile = new LinkedHashMap<>();
        List<LspDiagnostic> filtered = new ArrayList<>();
        for (LspDiagnostic diagnostic : all) {
            int count = perFile.getOrDefault(diagnostic.getUri(), 0);
            if (count >= maxPerFile || filtered.size() >= maxTotal) {
                continue;
            }
            filtered.add(diagnostic);
            perFile.put(diagnostic.getUri(), count + 1);
        }
        return filtered;
    }

    /**
     * resolveServerId.
     * 
     * @param server server
     * @return the result
     * @since 0.1.7
     */
    private static String resolveServerId(Object server) {
        try {
            Object config = invokeAndReturn(server, "getConfig");
            if (config != null) {
                Object serverId = invokeAndReturn(config, "getServerId");
                if (serverId != null) {
                    return String.valueOf(serverId);
                }
            }
        } catch (RuntimeException ignored) {
            // Server ID extraction via reflection may fail on various configs; fall back to "unknown".
        }
        return "unknown";
    }

    /**
     * readText.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    private static String readText(String filePath) {
        try {
            return java.nio.file.Files.readString(Path.of(filePath));
        } catch (java.io.IOException | SecurityException ex) {
            return "";
        }
    }

    /**
     * pathToFileUri.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    private static String pathToFileUri(String filePath) {
        return Path.of(filePath).toUri().toString();
    }

    /**
     * invoke.
     * 
     * @param target target
     * @param methodName methodName
     * @param paramTypes paramTypes
     * @param args args
     * @since 0.1.7
     */
    private static void invoke(Object target, String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (ReflectiveOperationException | SecurityException ex) {
            throw new IllegalStateException("failed to invoke method: " + methodName, ex);
        }
    }

    /**
     * invokeAndReturn.
     * 
     * @param target target
     * @param methodName methodName
     * @return the result
     * @since 0.1.7
     */
    private static Object invokeAndReturn(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }

    /**
     * requestServer.
     * 
     * @param server server
     * @param method method
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private static Object requestServer(Object server, String method, Map<String, Object> params) {
        Object result = invokeRequestMethod(server, "sendRequest", method, params);
        if (result != RequestMethodMissing.instance) {
            return result;
        }
        result = invokeRequestMethod(server, "request", method, params);
        if (result != RequestMethodMissing.instance) {
            return result;
        }
        result = invokeRequestMethod(server, "send_request", method, params);
        if (result != RequestMethodMissing.instance) {
            return result;
        }
        throw new IllegalStateException("LSP server does not expose a request method");
    }

    /**
     * invokeRequestMethod.
     * 
     * @param target target
     * @param methodName methodName
     * @param method method
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private static Object invokeRequestMethod(Object target, String methodName, String method,
            Map<String, Object> params) {
        try {
            Method reflect = target.getClass().getMethod(methodName, String.class, Map.class);
            reflect.setAccessible(true);
            return reflect.invoke(target, method, params);
        } catch (NoSuchMethodException ex) {
            return RequestMethodMissing.instance;
        } catch (ReflectiveOperationException | SecurityException ex) {
            throw new IllegalStateException("failed to request LSP method: " + methodName, ex);
        }
    }

    /**
     * shutdownLifecycle.
     * 
     * @param server server
     * @since 0.1.7
     */
    private static void shutdownLifecycle(Object server) {
        if (server == null) {
            return;
        }
        if (invokeNoArgIfPresent(server, "shutdown")) {
            invokeNoArgIfPresent(server, "exit");
            return;
        }
        if (invokeNoArgIfPresent(server, "close") || invokeNoArgIfPresent(server, "stop")
                || invokeNoArgIfPresent(server, "disconnect")) {
            return;
        }
        if (server instanceof java.io.Closeable closeable) {
            try {
                closeable.close();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("failed to close LSP server", ex);
            }
        }
    }

    /**
     * invokeNoArgIfPresent.
     * 
     * @param target target
     * @param methodName methodName
     * @return the result
     * @since 0.1.7
     */
    private static boolean invokeNoArgIfPresent(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        } catch (ReflectiveOperationException | SecurityException ex) {
            throw new IllegalStateException("failed to invoke lifecycle method: " + methodName, ex);
        }
    }

    private enum RequestMethodMissing {
        instance
    }
}
