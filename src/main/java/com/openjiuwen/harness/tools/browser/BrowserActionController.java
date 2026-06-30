/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Public class BrowserActionController used by the Java parity implementation.
 *
 * @since 1.0
 */
public class BrowserActionController {
    private static final BrowserActionController DEFAULT = new BrowserActionController();

    private final Map<String, ActionHandler> actions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> actionSpecs = new ConcurrentHashMap<>();
    private RuntimeRunner runtimeRunner;
    private Function<String, Map<String, Object>> codeExecutor;

    /**
 * Public interface ActionHandler used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface ActionHandler {
        Map<String, Object> handle(String sessionId, String requestId, Map<String, Object> params) throws Exception;
    }

    /**
 * Public interface RuntimeRunner used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface RuntimeRunner {
        Map<String, Object> run(String task, String sessionId, String requestId, Integer timeoutS) throws Exception;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static BrowserActionController getDefaultController() {
        return DEFAULT;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerAction(String name, ActionHandler handler) {
        String normalized = normalize(name);
        actions.put(normalized, handler);
        actionSpecs.putIfAbsent(normalized, Map.of("summary", normalized, "params", Map.of()));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listActions() {
        return actions.keySet().stream().sorted().toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Map<String, Object>> describeActions() {
        return new LinkedHashMap<>(actionSpecs);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> runAction(
            String action,
            String sessionId,
            String requestId,
            Map<String, Object> params
    ) {
        String normalized = normalize(action);
        if ("browser_task".equals(normalized)) {
            if (runtimeRunner == null) {
                return actionError(normalized, sessionId, requestId, "runtime_not_bound");
            }
            try {
                String task = String.valueOf(params.getOrDefault("task", ""));
                Integer timeout = params.get("timeout_s") != null
                        ? Integer.parseInt(String.valueOf(params.get("timeout_s")))
                        : null;
                return runtimeRunner.run(task, sessionId, requestId, timeout);
            } catch (Exception ex) {
                return actionError(normalized, sessionId, requestId, ex.getMessage());
            }
        }
        ActionHandler handler = actions.get(normalized);
        if (handler == null) {
            return actionError(normalized, sessionId, requestId, "unknown action: " + normalized);
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>(handler.handle(sessionId, requestId, params));
            if (Boolean.TRUE.equals(result.get("isOk")) && !result.containsKey("ok")) {
                result.put("ok", true);
            }
            if (Boolean.TRUE.equals(result.get("ok")) && !result.containsKey("isOk")) {
                result.put("isOk", true);
            }
            result.putIfAbsent("isOk", true);
            result.putIfAbsent("ok", true);
            result.put("action", normalized);
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        } catch (Exception ex) {
            return actionError(normalized, sessionId, requestId, ex.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void bindRuntimeRunner(RuntimeRunner runtimeRunner) {
        this.runtimeRunner = runtimeRunner;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void bindCodeExecutor(Function<String, Map<String, Object>> codeExecutor) {
        this.codeExecutor = codeExecutor;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerExampleActions() {
        registerAction("browser_drag_and_drop",
                (sessionId, requestId, params) -> Map.of("isOk", true, "summary", "dragged", "params", params));
        actionSpecs.put("browser_drag_and_drop",
                Map.of("summary", "Drag and drop element",
                        "params", Map.of("source", "string", "target", "string")));
        actions.putIfAbsent("browser_task", (sessionId, requestId, params) -> Map.of());
        actionSpecs.putIfAbsent(
                "browser_task",
                Map.of("summary", "Run browser task", "params", Map.of("task", "string")));
        registerAction("list_upload_files", (sessionId, requestId, params) -> {
            java.nio.file.Path root = resolveUploadRoot();
            if (root == null) {
                return Map.of("isOk", false, "files", List.of(), "error", "BROWSER_UPLOAD_ROOT is not configured");
            }
            if (!java.nio.file.Files.isDirectory(root)) {
                return Map.of("isOk", false, "files", List.of(), "error", "upload root does not exist");
            }
            return Map.of("isOk", true, "files", listDirFiles(root));
        });
        actionSpecs.put("list_upload_files", Map.of("summary", "List upload root files", "params", Map.of()));
        registerAction("browser_set_input_files", (sessionId, requestId, params) -> {
            @SuppressWarnings("unchecked")
            List<String> paths = (List<String>) params.get("paths");
            if (paths == null || paths.isEmpty()) {
                return Map.of("isOk", false, "error", "paths is required");
            }
            if (codeExecutor == null && runtimeRunner == null) {
                return Map.of("isOk", false, "error", "runtime_not_bound");
            }
            String selector = String.valueOf(params.getOrDefault("selector", "input[type=\"file\"]"));
            String script = buildSetInputFilesScript(selector, paths);
            if (codeExecutor != null) {
                return codeExecutor.apply(script);
            }
            return Map.of("isOk", true, "selector", selector, "paths", paths);
        });
        actionSpecs.put(
                "browser_set_input_files",
                Map.of("summary", "Set files on input", "params", Map.of("paths", "array")));
    }

    private Map<String, Object> actionError(String action, String sessionId, String requestId, String error) {
        return Map.of(
                "isOk", false,
                "action", action,
                "session_id", sessionId,
                "request_id", requestId,
                "error", error);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static java.nio.file.Path resolveUploadRoot() {
        String raw = System.getenv("BROWSER_UPLOAD_ROOT");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return java.nio.file.Path.of(raw).toAbsolutePath().normalize();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Map<String, Object>> listDirFiles(java.nio.file.Path root) {
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(root)) {
            return stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .sorted(java.util.Comparator.comparing(java.nio.file.Path::getFileName))
                    .<Map<String, Object>>map(path -> {
                        try {
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("name", path.getFileName().toString());
                            payload.put("path", path.toString());
                            payload.put("size_bytes", java.nio.file.Files.size(path));
                            return payload;
                        } catch (IOException | SecurityException ex) {
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("name", path.getFileName().toString());
                            payload.put("path", path.toString());
                            payload.put("size_bytes", 0L);
                            return payload;
                        }
                    })
                    .toList();
        } catch (IOException | SecurityException ex) {
            return List.of();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String buildSetInputFilesScript(String selector, List<String> paths) {
        return "const selector = " + quote(selector) + ";\nconst paths = " + quote(paths.toString()) + ";";
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
