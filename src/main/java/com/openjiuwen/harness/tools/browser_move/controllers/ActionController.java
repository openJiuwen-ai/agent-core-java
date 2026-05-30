/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.tools.browser_move.utils.EnvUtils;
import com.openjiuwen.harness.tools.browser_move.utils.ParsingUtils;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Runtime-native custom action controller for Playwright runtime.
 *
 * <p>Exposes a lightweight action registry consumed by the MCP wrapper.
 *
 * <p>Mirrors Python's {@code ActionController} in
 * {@code openjiuwen.harness.tools.browser_move.controllers.action}.
 */
public class ActionController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(ActionController.class);

    /** JSON mapper used for JavaScript payload generation. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Recursive browser action names. */
    private static final Set<String> RECURSIVE_BROWSER_ACTIONS = Set.of("browser_task", "run_browser_task");

    /** Action registry. */
    private final Map<String, ActionHandler> actions = new ConcurrentHashMap<>();

    /** Action specs registry. */
    private final Map<String, ActionSpec> actionSpecs = new ConcurrentHashMap<>();

    /** Lock for action execution. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Browser worker action context flag. */
    private boolean inBrowserWorkerAction = false;

    @Override
    public void bindRuntime(Object runtime) {
        if (runtime == null || findRuntimeRunnerMethod(runtime) == null) {
            throw new IllegalArgumentException("runtime must expose runBrowserTask(...) method");
        }
        this.runtime = runtime;
        this.runtimeRunner = runtime;
        LOG.info("[ActionController] bind_runtime");
    }

    @Override
    public void bindRuntimeRunner(Object runner) {
        this.runtimeRunner = runner;
        LOG.info("[ActionController] bind_runtime_runner");
    }

    @Override
    public void clearRuntimeRunner() {
        this.runtimeRunner = null;
        LOG.info("[ActionController] clear_runtime_runner");
    }

    @Override
    public void bindCodeExecutor(Object executor) {
        this.codeExecutor = executor;
        LOG.info("[ActionController] bind_code_executor");
    }

    @Override
    public void clearCodeExecutor() {
        this.codeExecutor = null;
        LOG.info("[ActionController] clear_code_executor");
    }

    /**
     * Register an action handler.
     * <p>
     * Mirrors Python's {@code register_action} method.
     */
    public void registerAction(String name, ActionHandler handler, boolean overwrite) {
        String actionName = normalizeActionName(name);
        if (actionName == null || actionName.isEmpty()) {
            throw new IllegalArgumentException("action name must be non-empty");
        }
        if (!overwrite && actions.containsKey(actionName)) {
            throw new IllegalArgumentException("action already exists: " + actionName);
        }
        actions.put(actionName, handler);
        LOG.debug("[ActionController] registered action: {}", actionName);
    }

    /**
     * Register action spec with metadata.
     * <p>
     * Mirrors Python's {@code register_action_spec} method.
     */
    public void registerActionSpec(String name, String summary, String whenToUse, Map<String, String> params) {
        String actionName = normalizeActionName(name);
        if (actionName == null || actionName.isEmpty()) {
            throw new IllegalArgumentException("action name must be non-empty");
        }
        actionSpecs.put(actionName, new ActionSpec(summary, whenToUse, params));
        LOG.debug("[ActionController] registered action spec: {}", actionName);
    }

    /**
     * List all registered actions.
     * <p>
     * Mirrors Python's {@code list_actions} method.
     */
    public List<String> listActions() {
        List<String> result = new ArrayList<>(actions.keySet());
        Collections.sort(result);
        return result;
    }

    /**
     * Describe all registered actions.
     * <p>
     * Mirrors Python's {@code describe_actions} method.
     */
    @Override
    public Map<String, Map<String, Object>> describeActions() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        List<String> names = listActions();
        for (String name : names) {
            ActionSpec spec = actionSpecs.get(name);
            result.put(name, spec != null ? actionSpecToMap(spec) : actionSpecToMap(null));
        }
        return result;
    }
    
    private Map<String, Object> actionSpecToMap(ActionSpec spec) {
        Map<String, Object> map = new HashMap<>();
        map.put("summary", spec != null ? spec.getSummary() : "");
        map.put("when_to_use", spec != null ? spec.getWhenToUse() : "");
        map.put("params", spec != null ? spec.getParams() : Map.of());
        return map;
    }

    /**
     * Get action handler by name.
     */
    public ActionHandler getAction(String name) {
        String actionName = normalizeActionName(name);
        return actions.get(actionName);
    }

    /**
     * Execute an action by name.
     * <p>
     * Mirrors Python's {@code run_action} method.
     */
    public CompletableFuture<ActionResult> executeAction(String name, String sessionId, String requestId, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String actionName = normalizeActionName(name);
            String sid = sessionId != null ? sessionId.trim() : "";
            String rid = requestId != null ? requestId.trim() : "";

            LOG.info("[ActionController] execute_action start action={} session_id={} request_id={}",
                actionName, sid.isEmpty() ? "-" : sid, rid.isEmpty() ? "-" : rid);

            // Block recursive browser actions
            if (inBrowserWorkerAction && RECURSIVE_BROWSER_ACTIONS.contains(actionName)) {
                String error = "recursive_browser_task_blocked: browser workers must not invoke browser_task";
                LOG.warn("[ActionController] blocked recursive action: {}", actionName);
                return ActionResult.error(actionName, sid, rid, error);
            }

            ActionHandler handler = actions.get(actionName);
            if (handler == null) {
                LOG.warn("[ActionController] unknown action: {}", actionName);
                return ActionResult.error(actionName, sid, rid, "unknown action: " + actionName);
            }

            try {
                lock.lock();
                Map<String, Object> result = handler.handle(sid, rid, params != null ? params : Map.of());

                // Ensure standard response fields
                Map<String, Object> response = new LinkedHashMap<>(result);
                response.putIfAbsent("ok", true);
                response.putIfAbsent("action", actionName);
                response.putIfAbsent("session_id", sid);
                response.putIfAbsent("request_id", rid);
                response.putIfAbsent("error", null);

                boolean ok = Boolean.TRUE.equals(response.get("ok"));
                LOG.info("[ActionController] execute_action end action={} ok={}", actionName, ok);
                return ActionResult.fromResponse(actionName, sid, rid, response);
            } catch (Exception e) {
                LOG.error("[ActionController] execute_action error action={}", actionName, e);
                return ActionResult.error(actionName, sid, rid, e.getMessage());
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Execute action (legacy API).
     */
    public CompletableFuture<ActionResult> executeAction(String name, Map<String, Object> params) {
        return executeAction(name, "", "", params);
    }

    /**
     * Register built-in actions.
     * <p>
     * Mirrors Python's {@code register_builtin_actions} method.
     */
    public void registerBuiltinActions() {
        // ping action
        registerAction("ping", (sid, rid, params) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("pong", true);
            result.put("session_id", sid);
            result.put("request_id", rid);
            return result;
        }, true);
        registerActionSpec("ping", "Health check ping", "Test if controller is responsive", Map.of());

        // echo action
        registerAction("echo", (sid, rid, params) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("text", params.getOrDefault("text", ""));
            result.put("session_id", sid);
            result.put("request_id", rid);
            return result;
        }, true);
        registerActionSpec("echo", "Echo text back", "Return the input text", Map.of("text", "Text to echo"));

        ActionHandler browserTask = (sid, rid, params) -> {
            if (runtimeRunner == null) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("ok", false);
                errorResult.put("error", "runtime_not_bound: call bind_runtime(...) before browser_task");
                errorResult.put("session_id", sid);
                errorResult.put("request_id", rid);
                return errorResult;
            }
            String task = params.getOrDefault("task", "").toString().trim();
            if (task.isEmpty()) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("ok", false);
                errorResult.put("error", "missing required parameter: task");
                errorResult.put("session_id", sid);
                errorResult.put("request_id", rid);
                return errorResult;
            }
            Integer timeoutS = normalizeTimeout(params.get("timeout_s"));
            
            // Invoke runtime runner
            try {
                // Assume runtimeRunner is a function or has an invoke method
                return invokeRuntimeRunner(task, sid, rid, timeoutS);
            } catch (Exception e) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("ok", false);
                errorResult.put("error", e.getMessage());
                return errorResult;
            }
        };

        // browser_task action
        registerAction("browser_task", browserTask, true);
        registerActionSpec("browser_task", "Execute browser automation task",
            "Run Playwright browser task", Map.of("task", "Task description", "timeout_s", "Timeout in seconds"));

        // run_browser_task action alias
        registerAction("run_browser_task", browserTask, true);
        registerActionSpec("run_browser_task", "Alias of browser_task",
            "Same behavior as browser_task", Map.of("task", "Task description", "timeout_s", "Timeout in seconds"));

        // browser_set_input_files action
        registerAction("browser_set_input_files", (sid, rid, params) -> browserSetInputFiles(sid, rid, params), true);
        registerActionSpec("browser_set_input_files", "Set files on a file input",
            "Use for file upload tasks after discovering absolute paths",
            Map.of("selector", "CSS selector for file input", "paths", "Absolute file paths"));

        // list_upload_files action
        registerAction("list_upload_files", (sid, rid, params) -> listUploadFiles(sid, rid), true);
        registerActionSpec("list_upload_files", "List uploadable files",
            "Discover files from BROWSER_UPLOAD_ROOT before browser_set_input_files", Map.of());

        LOG.info("[ActionController] registered {} builtin actions", actions.size());
    }

    /**
     * Build JavaScript to set files on a Playwright file input.
     * <p>
     * Mirrors Python's {@code _build_set_input_files_script} helper.
     */
    public static String buildSetInputFilesScript(String selector, List<String> paths) {
        String effectiveSelector = selector != null ? selector : "";
        String selectorJs = "'" + effectiveSelector.replace("\\", "\\\\").replace("'", "\\'") + "'";
        String pathsJson;
        try {
            pathsJson = OBJECT_MAPPER.writeValueAsString(paths != null ? paths : List.of());
        } catch (Exception e) {
            pathsJson = "[]";
        }
        return "async (page) => {\n"
            + "  try {\n"
            + "    await page.locator(" + selectorJs + ").setInputFiles(" + pathsJson + ");\n"
            + "    return { ok: true, selector: " + selectorJs + ", paths: " + pathsJson + " };\n"
            + "  } catch (error) {\n"
            + "    const msg = String(error);\n"
            + "    if (msg.includes('strict mode violation')) {\n"
            + "      return { ok: false, error: msg, selector: " + selectorJs + ", paths: " + pathsJson + ","
            + " hint: 'Multiple file inputs matched. Use a more specific selector"
            + " (e.g. an id like #file-upload) targeting the visible input.' };\n"
            + "    }\n"
            + "    return { ok: false, error: msg, selector: " + selectorJs + ", paths: " + pathsJson + " };\n"
            + "  }\n"
            + "}";
    }

    /**
     * Return flat file entries under the upload root.
     * <p>
     * Mirrors Python's {@code _list_dir_files} helper.
     */
    public static List<Map<String, Object>> listDirFiles(Path root) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (root == null) {
            return entries;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", path.getFileName().toString());
                    entry.put("path", path.toString());
                    try {
                        entry.put("size_bytes", Files.size(path));
                    } catch (Exception e) {
                        entry.put("size_bytes", -1L);
                    }
                    entries.add(entry);
                });
        } catch (Exception ignored) {
        }
        return entries;
    }

    private Map<String, Object> listUploadFiles(String sessionId, String requestId) {
        Path uploadRoot = EnvUtils.resolveUploadRoot();
        if (uploadRoot == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", "BROWSER_UPLOAD_ROOT is not configured. Set this env var to the directory where uploadable files are stored.");
            result.put("files", List.of());
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        }
        if (!Files.exists(uploadRoot)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", "Upload root directory does not exist: " + uploadRoot);
            result.put("files", List.of());
            result.put("upload_root", uploadRoot.toString());
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("upload_root", uploadRoot.toString());
        result.put("files", listDirFiles(uploadRoot));
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        return result;
    }

    private Map<String, Object> browserSetInputFiles(String sessionId, String requestId, Map<String, Object> params) {
        String selector = stringParam(params, "selector", "").trim();
        String effectiveSelector = selector.isEmpty() ? "input[type=\"file\"]" : selector;
        List<String> effectivePaths = stringListParam(params.get("paths"));
        if (effectivePaths.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", "paths is required and must be non-empty");
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        }

        String jsCode = buildSetInputFilesScript(effectiveSelector, effectivePaths);
        if (codeExecutor != null) {
            Object raw;
            try {
                raw = invokeCodeExecutor(jsCode);
            } catch (Exception e) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", false);
                result.put("error", "browser_run_code failed: " + e.getMessage());
                result.put("session_id", sessionId);
                result.put("request_id", requestId);
                return result;
            }
            Map<String, Object> parsed = ParsingUtils.extractJsonObject(raw);
            if (parsed.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", false);
                result.put("error", "Could not parse set_input_files result JSON from browser_run_code output");
                result.put("raw_preview", preview(raw));
                return result;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
            result.put("selector", parsed.getOrDefault("selector", effectiveSelector));
            result.put("paths", parsed.getOrDefault("paths", effectivePaths));
            result.put("error", parsed.get("error"));
            return result;
        }

        if (runtimeRunner == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", "runtime_not_bound: call bind_runtime(...) before browser_set_input_files");
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        }

        Map<String, Object> runtimeResult = invokeRuntimeRunner(
            buildRunCodeTask(jsCode, "set input files on " + effectiveSelector),
            sessionId,
            requestId,
            null
        );
        if (!Boolean.TRUE.equals(runtimeResult.get("ok"))) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", runtimeResult.getOrDefault("error", "runtime error"));
            result.put("runtime", runtimeResult);
            return result;
        }
        Map<String, Object> parsed = ParsingUtils.extractJsonObject(runtimeResult.get("final"));
        if (parsed.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", "Could not parse set_input_files result JSON from runtime final output");
            result.put("raw_preview", preview(runtimeResult.get("final")));
            result.put("runtime", runtimeResult);
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
        result.put("selector", parsed.getOrDefault("selector", effectiveSelector));
        result.put("paths", parsed.getOrDefault("paths", effectivePaths));
        result.put("error", parsed.get("error"));
        result.put("runtime", runtimeResult);
        return result;
    }

    private static String buildRunCodeTask(String jsCode, String purpose) {
        Map<String, Object> toolInput = new LinkedHashMap<>();
        toolInput.put("code", jsCode);
        String toolInputJson;
        try {
            toolInputJson = OBJECT_MAPPER.writeValueAsString(toolInput);
        } catch (Exception e) {
            toolInputJson = "{\"code\":\"\"}";
        }
        return "Execute this browser operation: " + purpose + ".\n"
            + "Call browser_run_code exactly once with this JSON input:\n"
            + toolInputJson + "\n\n"
            + "Then return your required top-level response JSON. "
            + "Set its `final` field to the exact JSON result returned by browser_run_code.";
    }

    private static Integer normalizeTimeout(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(params.get(key));
    }

    private static List<String> stringListParam(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addNonBlankString(result, item);
            }
            return result;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addNonBlankString(result, java.lang.reflect.Array.get(value, i));
            }
            return result;
        }
        addNonBlankString(result, value);
        return result;
    }

    private static void addNonBlankString(List<String> result, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isEmpty()) {
            result.add(text);
        }
    }

    private static String preview(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 400 ? text : text.substring(0, 400);
    }

    private Object invokeCodeExecutor(String jsCode) throws Exception {
        Object raw;
        if (codeExecutor instanceof Function fn) {
            raw = fn.apply(jsCode);
        } else {
            Method method = findSingleStringMethod(codeExecutor, "execute", "apply", "runCode");
            if (method == null) {
                throw new IllegalStateException("code_executor invocation failed");
            }
            raw = method.invoke(codeExecutor, jsCode);
        }
        return resolveFuture(raw);
    }

    private Method findRuntimeRunnerMethod(Object candidate) {
        if (candidate == null) {
            return null;
        }
        for (String name : List.of("runBrowserTask", "run_browser_task")) {
            try {
                return candidate.getClass().getMethod(name, String.class, String.class, String.class, Integer.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method findSingleStringMethod(Object candidate, String... names) {
        if (candidate == null) {
            return null;
        }
        for (String name : names) {
            try {
                return candidate.getClass().getMethod(name, String.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Object resolveFuture(Object value) {
        if (value instanceof CompletableFuture<?> future) {
            return future.join();
        }
        return value;
    }

    /**
     * Invoke runtime runner for browser tasks.
     */
    private Map<String, Object> invokeRuntimeRunner(String task, String sessionId, String requestId, Integer timeoutS) {
        if (runtimeRunner == null) {
            Map<String, Object> fallbackResult = new LinkedHashMap<>();
            fallbackResult.put("ok", false);
            fallbackResult.put("error", "runtime_runner invocation failed");
            return fallbackResult;
        }
        if (runtimeRunner instanceof Function fn) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("task", task);
            args.put("session_id", sessionId);
            args.put("request_id", requestId);
            if (timeoutS != null) {
                args.put("timeout_s", timeoutS);
            }
            Object result = resolveFuture(fn.apply(args));
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        }
        // Fallback: try to invoke via reflection
        try {
            Method method = findRuntimeRunnerMethod(runtimeRunner);
            if (method == null) {
                throw new NoSuchMethodException("runBrowserTask");
            }
            Object result = resolveFuture(method.invoke(runtimeRunner, task, sessionId, requestId, timeoutS));
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        } catch (Exception e) {
            LOG.debug("[ActionController] reflection invocation failed", e);
        }

        Map<String, Object> fallbackResult = new LinkedHashMap<>();
        fallbackResult.put("ok", false);
        fallbackResult.put("error", "runtime_runner invocation failed");
        return fallbackResult;
    }

    /**
     * Normalize action name.
     */
    private String normalizeActionName(String name) {
        if (name == null) return "";
        String normalized = name.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        return normalized;
    }

    /**
     * Enter browser worker action context.
     */
    public void enterBrowserWorkerActionContext() {
        inBrowserWorkerAction = true;
    }

    /**
     * Exit browser worker action context.
     */
    public void exitBrowserWorkerActionContext() {
        inBrowserWorkerAction = false;
    }

    /**
     * Snapshot current state.
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("actions", new HashMap<>(actions));
        state.put("action_specs", new HashMap<>(actionSpecs));
        return state;
    }

    /**
     * Action handler interface.
     */
    @FunctionalInterface
    public interface ActionHandler {
        Map<String, Object> handle(String sessionId, String requestId, Map<String, Object> params);
    }

    /**
     * Action spec metadata.
     */
    @Data
    public static class ActionSpec {
        private final String summary;
        private final String whenToUse;
        private final Map<String, String> params;

        public ActionSpec(String summary, String whenToUse, Map<String, String> params) {
            this.summary = summary != null ? summary : "";
            this.whenToUse = whenToUse != null ? whenToUse : "";
            this.params = params != null ? params : Map.of();
        }
    }

    /**
     * Action result wrapper.
     */
    @Data
    @Builder
    public static class ActionResult {
        private String action;
        private String sessionId;
        private String requestId;
        private boolean ok;
        private Object data;
        private String error;

        public static ActionResult success(String action, String sessionId, String requestId, Object data) {
            return ActionResult.builder()
                .action(action)
                .sessionId(sessionId)
                .requestId(requestId)
                .ok(true)
                .data(data)
                .build();
        }

        public static ActionResult error(String action, String sessionId, String requestId, String error) {
            return ActionResult.builder()
                .action(action)
                .sessionId(sessionId)
                .requestId(requestId)
                .ok(false)
                .error(error)
                .build();
        }

        public static ActionResult fromResponse(String action, String sessionId, String requestId, Map<String, Object> response) {
            boolean ok = Boolean.TRUE.equals(response.get("ok"));
            Object errorObj = response.get("error");
            return ActionResult.builder()
                .action(action)
                .sessionId(sessionId)
                .requestId(requestId)
                .ok(ok)
                .data(response)
                .error(ok || errorObj == null ? null : String.valueOf(errorObj))
                .build();
        }
    }
}
