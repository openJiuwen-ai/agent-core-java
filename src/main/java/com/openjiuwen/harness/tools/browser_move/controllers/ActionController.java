/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.tools.browser_move.utils.BrowserMoveEnv;
import com.openjiuwen.harness.tools.browser_move.utils.ParsingUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runtime-native custom action controller for Playwright runtime.
 *
 * <p>Mirrors Python's {@code ActionController} in
 * {@code openjiuwen/harness/tools/browser_move/controllers/action.py}.</p>
 */
public class ActionController implements BaseController {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> RECURSIVE_BROWSER_ACTIONS = Set.of("browser_task", "run_browser_task");
    private static final ActionController DEFAULT_CONTROLLER = new ActionController();

    private final Map<String, ActionHandler> actions;
    private final Map<String, ActionSpec> actionSpecs;
    private final ReentrantLock lock;
    private boolean inBrowserWorkerAction;
    private Object runtimeRunner;
    private Object codeExecutor;

    public ActionController() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>(), null, null, new ReentrantLock());
    }

    public ActionController(
            Map<String, ActionHandler> actions,
            Map<String, ActionSpec> actionSpecs,
            Object runtimeRunner,
            Object codeExecutor,
            ReentrantLock lock
    ) {
        this.actions = actions == null ? new LinkedHashMap<>() : actions;
        this.actionSpecs = actionSpecs == null ? new LinkedHashMap<>() : actionSpecs;
        this.runtimeRunner = runtimeRunner;
        this.codeExecutor = codeExecutor;
        this.lock = lock == null ? new ReentrantLock() : lock;
    }

    public static ActionController getDefaultController() {
        return DEFAULT_CONTROLLER;
    }

    public static void bindRuntimeRunnerForDefault(RuntimeRunner runner) {
        DEFAULT_CONTROLLER.bindRuntimeRunner(runner);
    }

    public BrowserWorkerActionContext browserWorkerActionContext() {
        return new BrowserWorkerActionContext(this);
    }

    @Override
    public void bindRuntime(Object runtime) {
        if (runtime == null || findRuntimeRunnerMethod(runtime) == null) {
            throw new IllegalArgumentException("runtime must expose an async run_browser_task(...) method");
        }
        this.runtimeRunner = runtime;
    }

    public void bindRuntimeRunner(RuntimeRunner runner) {
        this.runtimeRunner = runner;
    }

    @Override
    public void bindRuntimeRunner(Object runner) {
        this.runtimeRunner = runner;
    }

    @Override
    public void clearRuntimeRunner() {
        this.runtimeRunner = null;
    }

    public void bindCodeExecutor(CodeExecutor executor) {
        this.codeExecutor = executor;
    }

    @Override
    public void bindCodeExecutor(Object executor) {
        this.codeExecutor = executor;
    }

    @Override
    public void clearCodeExecutor() {
        this.codeExecutor = null;
    }

    public void registerAction(String name, ActionHandler handler) {
        registerAction(name, handler, true);
    }

    public void registerAction(String name, ActionHandler handler, boolean overwrite) {
        String actionName = normalizeActionName(name);
        if (actionName.isEmpty()) {
            throw new IllegalArgumentException("action name must be non-empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must be callable");
        }
        if (!overwrite && actions.containsKey(actionName)) {
            throw new IllegalArgumentException("action already exists: " + actionName);
        }
        actions.put(actionName, handler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerAction(String name, Object handler, boolean overwrite) {
        if (handler instanceof ActionHandler actionHandler) {
            registerAction(name, actionHandler, overwrite);
            return;
        }
        registerAction(name, (sessionId, requestId, params) -> {
            if (handler instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
            return handler;
        }, overwrite);
    }

    @Override
    public void registerActionSpec(String name, String summary, String whenToUse, Map<String, String> params) {
        String actionName = normalizeActionName(name);
        if (actionName.isEmpty()) {
            throw new IllegalArgumentException("action name must be non-empty");
        }
        actionSpecs.put(actionName, new ActionSpec(summary, whenToUse, params));
    }

    @Override
    public List<String> listActions() {
        List<String> names = new ArrayList<>(actions.keySet());
        Collections.sort(names);
        return names;
    }

    @Override
    public Map<String, Map<String, Object>> describeActions() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String name : listActions()) {
            ActionSpec spec = actionSpecs.get(name);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("summary", spec == null ? "" : spec.summary());
            details.put("when_to_use", spec == null ? "" : spec.whenToUse());
            details.put("params", spec == null ? Map.of() : spec.params());
            result.put(name, details);
        }
        return result;
    }

    @Override
    public Map<String, Object> runAction(String action, String sessionId, String requestId, Map<String, Object> kwargs) {
        String actionName = normalizeActionName(action);
        String sid = trimToEmpty(sessionId);
        String rid = trimToEmpty(requestId);
        Map<String, Object> params = kwargs == null ? Map.of() : kwargs;
        String paramKeys = String.join(",", params.keySet().stream().map(String::valueOf).sorted().toList());

        LOGGER.info(
                "CONTROLLER_ACTION start action={} session_id={} request_id={} param_keys={}",
                actionName,
                sid.isEmpty() ? "-" : sid,
                rid.isEmpty() ? "-" : rid,
                paramKeys.isEmpty() ? "-" : paramKeys);

        if (inBrowserWorkerAction && RECURSIVE_BROWSER_ACTIONS.contains(actionName)) {
            String error = "recursive_browser_task_blocked: browser workers must not invoke browser_task/run_browser_task via browser_custom_action; return a JSON error instead";
            LOGGER.warning(
                    "CONTROLLER_ACTION blocked action={} session_id={} request_id={} error={}",
                    actionName,
                    sid.isEmpty() ? "-" : sid,
                    rid.isEmpty() ? "-" : rid,
                    error);
            return errorResult(
                    actionName,
                    sid,
                    rid,
                    error);
        }

        ActionHandler handler = actions.get(actionName);
        if (handler == null) {
            LOGGER.warning(
                    "CONTROLLER_ACTION unknown action={} session_id={} request_id={}",
                    actionName,
                    sid.isEmpty() ? "-" : sid,
                    rid.isEmpty() ? "-" : rid);
            return errorResult(actionName, sid, rid, "unknown action: " + actionName);
        }

        try {
            lock.lock();
            Object raw = maybeAwait(handler.handle(sid, rid, params));
            Map<String, Object> response = raw instanceof Map<?, ?> map
                    ? castMap(map)
                    : new LinkedHashMap<>(Map.of("result", raw));
            response.putIfAbsent("ok", true);
            response.putIfAbsent("action", actionName);
            response.putIfAbsent("session_id", sid);
            response.putIfAbsent("request_id", rid);
            response.putIfAbsent("error", null);
            boolean ok = Boolean.TRUE.equals(response.get("ok"));
            Object error = ok ? null : response.get("error");
            LOGGER.info(
                    "CONTROLLER_ACTION end action={} session_id={} request_id={} ok={}{}",
                    actionName,
                    sid.isEmpty() ? "-" : sid,
                    rid.isEmpty() ? "-" : rid,
                    ok,
                    error == null ? "" : " error=" + error);
            return response;
        } catch (Exception exception) {
            LOGGER.error(
                    "CONTROLLER_ACTION error action={} session_id={} request_id={} error={}",
                    actionName,
                    sid.isEmpty() ? "-" : sid,
                    rid.isEmpty() ? "-" : rid,
                    exception);
            return errorResult(actionName, sid, rid, exception.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void registerBuiltinActions() {
        registerBuiltinActions(this);
    }

    public void registerExampleActions() {
        registerBuiltinActions();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("actions", new LinkedHashMap<>(actions));
        snapshot.put("action_specs", new LinkedHashMap<>(actionSpecs));
        snapshot.put("runtime_runner", runtimeRunner);
        snapshot.put("code_executor", codeExecutor);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    public void restore(Map<String, Object> snapshot) {
        actions.clear();
        actionSpecs.clear();
        if (snapshot == null) {
            runtimeRunner = null;
            codeExecutor = null;
            return;
        }
        Object restoredActions = snapshot.get("actions");
        if (restoredActions instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof ActionHandler handler) {
                    actions.put(key, handler);
                }
            }
        }
        Object restoredSpecs = snapshot.get("action_specs");
        if (restoredSpecs instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof ActionSpec spec) {
                    actionSpecs.put(key, spec);
                }
            }
        }
        runtimeRunner = snapshot.get("runtime_runner");
        codeExecutor = snapshot.get("code_executor");
    }

    public static String buildSetInputFilesScript(String selector, List<String> paths) {
        String effectiveSelector = selector == null ? "" : selector;
        String selectorJs = "'" + effectiveSelector.replace("\\", "\\\\").replace("'", "\\'") + "'";
        String pathsJson;
        try {
            pathsJson = OBJECT_MAPPER.writeValueAsString(paths == null ? List.of() : paths);
        } catch (Exception exception) {
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
                + " hint: 'Multiple file inputs matched. Use a more specific selector (e.g. an id like #file-upload) targeting the visible input.' };\n"
                + "    }\n"
                + "    return { ok: false, error: msg, selector: " + selectorJs + ", paths: " + pathsJson + " };\n"
                + "  }\n"
                + "}";
    }

    public static List<Map<String, Object>> listDirFiles(Path root) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (root == null) {
            return entries;
        }
        try (var stream = Files.list(root)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("name", path.getFileName().toString());
                        entry.put("path", path.toString());
                        try {
                            entry.put("size_bytes", Files.size(path));
                        } catch (Exception exception) {
                            entry.put("size_bytes", -1L);
                        }
                        entries.add(entry);
                    });
        } catch (Exception ignored) {
            return List.of();
        }
        return entries;
    }

    public static void bindRuntimeForDefault(Object runtime) {
        DEFAULT_CONTROLLER.bindRuntime(runtime);
    }

    public static void clearRuntimeRunnerForDefault() {
        DEFAULT_CONTROLLER.clearRuntimeRunner();
    }

    public static void bindCodeExecutorForDefault(CodeExecutor executor) {
        DEFAULT_CONTROLLER.bindCodeExecutor(executor);
    }

    public static void clearCodeExecutorForDefault() {
        DEFAULT_CONTROLLER.clearCodeExecutor();
    }

    public static void registerActionForDefault(String name, ActionHandler handler, boolean overwrite) {
        DEFAULT_CONTROLLER.registerAction(name, handler, overwrite);
    }

    public static void registerActionSpecForDefault(String name, String summary, String whenToUse, Map<String, String> params) {
        DEFAULT_CONTROLLER.registerActionSpec(name, summary, whenToUse, params);
    }

    public static List<String> listActionsForDefault() {
        return DEFAULT_CONTROLLER.listActions();
    }

    public static Map<String, Map<String, Object>> describeActionsForDefault() {
        return DEFAULT_CONTROLLER.describeActions();
    }

    public static Map<String, Object> runActionForDefault(String action, String sessionId, String requestId, Map<String, Object> kwargs) {
        return DEFAULT_CONTROLLER.runAction(action, sessionId, requestId, kwargs);
    }

    public static void registerBuiltinActions(ActionController controller) {
        ActionController ctl = controller == null ? DEFAULT_CONTROLLER : controller;

        ctl.registerAction("ping", (sessionId, requestId, params) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("pong", true);
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        }, true);
        ctl.registerActionSpec("ping", "Health check action.", "Use to verify controller dispatch and session/request threading.", Map.of());

        ctl.registerAction("echo", (sessionId, requestId, params) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("text", params.getOrDefault("text", ""));
            result.put("session_id", sessionId);
            result.put("request_id", requestId);
            return result;
        }, true);
        ctl.registerActionSpec("echo", "Echoes provided text and metadata.",
                "Use for debugging payload passthrough through browser_custom_action.",
                Map.of("text", "string: text to echo back"));

        ctl.registerAction("browser_task", (sessionId, requestId, params) -> ctl.browserTask(sessionId, requestId, params), true);
        ctl.registerAction("run_browser_task", (sessionId, requestId, params) -> ctl.browserTask(sessionId, requestId, params), true);
        ctl.registerActionSpec("browser_task", "Runs a free-form browser task through runtime.run_browser_task.",
                "Use for generic website tasks when no specialized custom action applies.",
                Map.of("task", "string: required task prompt for the browser worker", "timeout_s", "int: optional positive timeout override"));
        ctl.registerActionSpec("run_browser_task", "Alias of browser_task.", "Same behavior as browser_task.",
                Map.of("task", "string: required task prompt for the browser worker", "timeout_s", "int: optional positive timeout override"));

        ctl.registerAction("browser_get_element_coordinates",
                (sessionId, requestId, params) -> ctl.browserGetElementCoordinates(sessionId, requestId, params), true);
        ctl.registerActionSpec("browser_get_element_coordinates",
                "Resolves source/target screen coordinates by selectors or explicit coordinates.",
                "Use when you need coordinates for one element (element_source only) or two (source + target). element_target is optional.",
                Map.of(
                        "element_source", "string: source selector/text alias",
                        "element_target", "string: target selector/text alias",
                        "coord_source_x", "int: source x coordinate",
                        "coord_source_y", "int: source y coordinate",
                        "coord_target_x", "int: target x coordinate",
                        "coord_target_y", "int: target y coordinate"));

        ctl.registerAction("browser_drag_and_drop",
                (sessionId, requestId, params) -> ctl.browserDragAndDrop(sessionId, requestId, params), true);
        ctl.registerActionSpec("browser_drag_and_drop",
                "Performs drag-and-drop using selectors or explicit coordinates.",
                "Use for drag-and-drop tasks instead of generic browser_run_task text-only instructions.",
                Map.of(
                        "element_source", "string: source selector/text alias",
                        "element_target", "string: target selector/text alias",
                        "coord_source_x", "int: source x coordinate",
                        "coord_source_y", "int: source y coordinate",
                        "coord_target_x", "int: target x coordinate",
                        "coord_target_y", "int: target y coordinate"));

        ctl.registerAction("browser_set_input_files",
                (sessionId, requestId, params) -> ctl.browserSetInputFiles(sessionId, requestId, params), true);
        ctl.registerActionSpec("browser_set_input_files",
                "Sets files on an <input type='file'> element.",
                "Use for all file upload tasks. Call list_upload_files first to get absolute paths.",
                Map.of(
                        "selector", "string: CSS selector for the file input",
                        "paths", "list[string]: absolute file paths to set on the input"));

        ctl.registerAction("list_upload_files",
                (sessionId, requestId, params) -> ctl.listUploadFiles(sessionId, requestId), true);
        ctl.registerActionSpec("list_upload_files",
                "Lists files available for upload from the configured BROWSER_UPLOAD_ROOT directory.",
                "Call this before browser_set_input_files to discover exact absolute file paths.",
                Map.of());
    }

    private Map<String, Object> browserTask(String sessionId, String requestId, Map<String, Object> params) throws Exception {
        if (runtimeRunner == null) {
            return errorResult("browser_task", sessionId, requestId,
                    "runtime_not_bound: call bind_runtime(...) before browser_task");
        }
        String task = trimToEmpty(stringValue(params.get("task")));
        if (task.isEmpty()) {
            return errorResult("browser_task", sessionId, requestId, "missing required parameter: task");
        }
        Integer timeoutS = normalizeTimeout(params.get("timeout_s"));
        return invokeRuntimeRunner(task, sessionId, requestId, timeoutS);
    }

    private Map<String, Object> browserGetElementCoordinates(String sessionId, String requestId, Map<String, Object> params) throws Exception {
        Map<String, Object> payload = buildDragPayload(params);
        if (!hasSourceSelector(payload) && !hasCoordinateInputs(payload)) {
            return Map.of(
                    "ok", false,
                    "error", "Missing location inputs. Provide at least element_source (element_target is optional), or coord_source_x/coord_source_y/coord_target_x/coord_target_y. Aliases source/target and source_x/source_y/target_x/target_y are also supported.");
        }
        String jsCode = buildCoordinateScript(payload);
        if (codeExecutor != null) {
            Object raw = invokeCodeExecutor(jsCode);
            Map<String, Object> parsed = ParsingUtils.extractJsonObject(raw);
            if (parsed.isEmpty()) {
                return Map.of(
                        "ok", false,
                        "error", "Could not parse coordinate result JSON from browser_run_code output",
                        "raw_preview", preview(raw));
            }
            return coordinateResult(parsed, null);
        }
        if (runtimeRunner == null) {
            return errorResult(
                    "browser_get_element_coordinates",
                    sessionId,
                    requestId,
                    "runtime_not_bound: call bind_runtime(...) before browser_get_element_coordinates");
        }
        Map<String, Object> runtimeResult = invokeRuntimeRunner(
                buildRunCodeTask(jsCode, "resolve source/target coordinates"),
                sessionId,
                requestId,
                normalizeTimeout(params.get("timeout_s")));
        if (!Boolean.TRUE.equals(runtimeResult.get("ok"))) {
            return Map.of("ok", false, "error", runtimeResult.getOrDefault("error", "runtime error"), "runtime", runtimeResult);
        }
        Map<String, Object> parsed = ParsingUtils.extractJsonObject(runtimeResult.get("final"));
        if (parsed.isEmpty()) {
            return Map.of(
                    "ok", false,
                    "error", "Could not parse coordinate result JSON from runtime final output",
                    "final_preview", preview(runtimeResult.get("final")),
                    "runtime", runtimeResult);
        }
        return coordinateResult(parsed, runtimeResult);
    }

    private Map<String, Object> browserDragAndDrop(String sessionId, String requestId, Map<String, Object> params) throws Exception {
        Map<String, Object> payload = buildDragPayload(params);
        if (!hasSelectorInputs(payload) && !hasCoordinateInputs(payload)) {
            return Map.of(
                    "ok", false,
                    "error", "Missing drag inputs. Provide either element_source + element_target, or coord_source_x/coord_source_y/coord_target_x/coord_target_y. Aliases source/target and source_x/source_y/target_x/target_y are also supported.");
        }
        String jsCode = buildDragScript(payload);
        if (codeExecutor != null) {
            Object raw = invokeCodeExecutor(jsCode);
            Map<String, Object> parsed = ParsingUtils.extractJsonObject(raw);
            if (parsed.isEmpty()) {
                return Map.of(
                        "ok", false,
                        "error", "Could not parse drag result JSON from browser_run_code output",
                        "raw_preview", preview(raw));
            }
            return dragResult(parsed, null);
        }
        if (runtimeRunner == null) {
            return errorResult(
                    "browser_drag_and_drop",
                    sessionId,
                    requestId,
                    "runtime_not_bound: call bind_runtime(...) before browser_drag_and_drop");
        }
        Map<String, Object> runtimeResult = invokeRuntimeRunner(
                buildRunCodeTask(jsCode, "drag and drop"),
                sessionId,
                requestId,
                normalizeTimeout(params.get("timeout_s")));
        if (!Boolean.TRUE.equals(runtimeResult.get("ok"))) {
            return Map.of("ok", false, "error", runtimeResult.getOrDefault("error", "runtime error"), "runtime", runtimeResult);
        }
        Map<String, Object> parsed = ParsingUtils.extractJsonObject(runtimeResult.get("final"));
        if (parsed.isEmpty()) {
            return Map.of(
                    "ok", false,
                    "error", "Could not parse drag result JSON from runtime final output",
                    "final_preview", preview(runtimeResult.get("final")),
                    "runtime", runtimeResult);
        }
        return dragResult(parsed, runtimeResult);
    }

    private Map<String, Object> listUploadFiles(String sessionId, String requestId) {
        Path uploadRoot = BrowserMoveEnv.resolveUploadRoot();
        if (uploadRoot == null) {
            return Map.of(
                    "ok", false,
                    "error", "BROWSER_UPLOAD_ROOT is not configured. Set this env var to the directory where uploadable files are stored.",
                    "files", List.of(),
                    "session_id", sessionId,
                    "request_id", requestId);
        }
        if (!Files.exists(uploadRoot)) {
            return Map.of(
                    "ok", false,
                    "error", "Upload root directory does not exist: " + uploadRoot,
                    "files", List.of(),
                    "upload_root", uploadRoot.toString(),
                    "session_id", sessionId,
                    "request_id", requestId);
        }
        return Map.of(
                "ok", true,
                "upload_root", uploadRoot.toString(),
                "files", listDirFiles(uploadRoot),
                "session_id", sessionId,
                "request_id", requestId);
    }

    private Map<String, Object> browserSetInputFiles(String sessionId, String requestId, Map<String, Object> params) throws Exception {
        String selector = trimToEmpty(stringValue(params.get("selector")));
        String effectiveSelector = selector.isEmpty() ? "input[type=\"file\"]" : selector;
        List<String> effectivePaths = stringList(params.get("paths"));
        if (effectivePaths.isEmpty()) {
            return errorResult("browser_set_input_files", sessionId, requestId, "paths is required and must be non-empty");
        }

        String jsCode = buildSetInputFilesScript(effectiveSelector, effectivePaths);
        if (codeExecutor != null) {
            Object raw = invokeCodeExecutor(jsCode);
            Map<String, Object> parsed = ParsingUtils.extractJsonObject(raw);
            if (parsed.isEmpty()) {
                return Map.of(
                        "ok", false,
                        "error", "Could not parse set_input_files result JSON from browser_run_code output",
                        "raw_preview", preview(raw));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
            result.put("selector", parsed.getOrDefault("selector", effectiveSelector));
            result.put("paths", parsed.getOrDefault("paths", effectivePaths));
            result.put("error", parsed.get("error"));
            return result;
        }
        if (runtimeRunner == null) {
            return errorResult(
                    "browser_set_input_files",
                    sessionId,
                    requestId,
                    "runtime_not_bound: call bind_runtime(...) before browser_set_input_files");
        }
        Map<String, Object> runtimeResult = invokeRuntimeRunner(
                buildRunCodeTask(jsCode, "set input files on " + effectiveSelector),
                sessionId,
                requestId,
                null);
        if (!Boolean.TRUE.equals(runtimeResult.get("ok"))) {
            return Map.of("ok", false, "error", runtimeResult.getOrDefault("error", "runtime error"), "runtime", runtimeResult);
        }
        Map<String, Object> parsed = ParsingUtils.extractJsonObject(runtimeResult.get("final"));
        if (parsed.isEmpty()) {
            return Map.of(
                    "ok", false,
                    "error", "Could not parse set_input_files result JSON from runtime final output",
                    "raw_preview", preview(runtimeResult.get("final")),
                    "runtime", runtimeResult);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
        result.put("selector", parsed.getOrDefault("selector", effectiveSelector));
        result.put("paths", parsed.getOrDefault("paths", effectivePaths));
        result.put("error", parsed.get("error"));
        result.put("runtime", runtimeResult);
        return result;
    }

    private Object invokeCodeExecutor(String jsCode) throws Exception {
        if (codeExecutor instanceof CodeExecutor executor) {
            return maybeAwait(executor.execute(jsCode));
        }
        Method method = findSingleStringMethod(codeExecutor, "execute", "apply", "runCode");
        if (method == null) {
            throw new IllegalStateException("code_executor invocation failed");
        }
        return maybeAwait(method.invoke(codeExecutor, jsCode));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeRuntimeRunner(String task, String sessionId, String requestId, Integer timeoutS) throws Exception {
        if (runtimeRunner instanceof RuntimeRunner runner) {
            return castMap(maybeAwait(runner.run(task, sessionId, requestId, timeoutS)));
        }
        Method method = findRuntimeRunnerMethod(runtimeRunner);
        if (method == null) {
            throw new IllegalStateException("runtime_runner invocation failed");
        }
        return castMap(maybeAwait(method.invoke(runtimeRunner, task, sessionId, requestId, timeoutS)));
    }

    private static Method findRuntimeRunnerMethod(Object candidate) {
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

    private static Method findSingleStringMethod(Object candidate, String... names) {
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

    private static Object maybeAwait(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().join();
        }
        return value;
    }

    private static String buildRunCodeTask(String jsCode, String purpose) {
        String toolInput;
        try {
            toolInput = OBJECT_MAPPER.writeValueAsString(Map.of("code", jsCode));
        } catch (Exception exception) {
            toolInput = "{\"code\":\"\"}";
        }
        return "Execute this browser operation: " + purpose + ".\n"
                + "Call browser_run_code exactly once with this JSON input:\n"
                + toolInput + "\n\n"
                + "Then return your required top-level response JSON. Set its `final` field to the exact JSON result returned by browser_run_code.";
    }

    static String buildCoordinateScript(Map<String, Object> payload) {
        return wrapPageScript(
                buildSelectorResolutionHelpers(toJson(payload)),
                buildCoordinateResolutionBody());
    }

    static String buildDragScript(Map<String, Object> payload) {
        return wrapPageScript(
                buildSelectorResolutionHelpers(toJson(payload)),
                buildDragOperationBody());
    }

    private static String wrapPageScript(String... parts) {
        StringBuilder builder = new StringBuilder("async (page) => {\n");
        for (String part : parts) {
            builder.append(part);
        }
        return builder.append("}").toString();
    }

    private static String buildSelectorResolutionHelpers(String payloadJson) {
        return "  const params = " + payloadJson + ";\n"
                + "  if (params.url && String(params.url).trim()) {\n"
                + "    await page.goto(String(params.url).trim());\n"
                + "  }\n"
                + "  const getTextBox = async (query, role) => {\n"
                + "    const term = String(query || '').trim().toLowerCase();\n"
                + "    if (!term) return null;\n"
                + "    return await page.evaluate(({ term, role }) => {\n"
                + "      const all = Array.from(document.querySelectorAll('body *'));\n"
                + "      const score = (el) => {\n"
                + "        const text = String(el.textContent || '').trim().toLowerCase();\n"
                + "        if (!text) return -1;\n"
                + "        if (text === term) return 2;\n"
                + "        if (text.includes(term)) return 1;\n"
                + "        return -1;\n"
                + "      };\n"
                + "      const toVisibleBox = (el) => {\n"
                + "        if (!el) return null;\n"
                + "        const candidates = role === 'target' && el.parentElement ? [el.parentElement, el] : [el];\n"
                + "        for (const candidate of candidates) {\n"
                + "          const rect = candidate.getBoundingClientRect();\n"
                + "          if (rect && rect.width > 0 && rect.height > 0) {\n"
                + "            return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };\n"
                + "          }\n"
                + "        }\n"
                + "        return null;\n"
                + "      };\n"
                + "      const exactMatches = all.filter((el) => score(el) === 2);\n"
                + "      for (const el of exactMatches) {\n"
                + "        const box = toVisibleBox(el);\n"
                + "        if (box) return box;\n"
                + "      }\n"
                + "      const fuzzyMatches = all.filter((el) => score(el) === 1);\n"
                + "      for (const el of fuzzyMatches) {\n"
                + "        const box = toVisibleBox(el);\n"
                + "        if (box) return box;\n"
                + "      }\n"
                + "      return null;\n"
                + "    }, { term, role });\n"
                + "  };\n"
                + "  const extractTextFromHasText = (s) => {\n"
                + "    if (!s || typeof s !== 'string') return s;\n"
                + "    const m = String(s).match(/:has-text\\s*\\(\\s*['\\\"]([^'\\\"]*)['\\\"]\\s*\\)/);\n"
                + "    return m ? m[1] : s;\n"
                + "  };\n"
                + "  const getPoint = async (selector, offset, role) => {\n"
                + "    let box = null;\n"
                + "    if (selector) {\n"
                + "      try {\n"
                + "        const el = await page.$(selector);\n"
                + "        if (el) box = await el.boundingBox();\n"
                + "      } catch (_err) {\n"
                + "        box = null;\n"
                + "      }\n"
                + "    }\n"
                + "    if (!box) {\n"
                + "      const textTerm = extractTextFromHasText(selector) || selector;\n"
                + "      box = await getTextBox(textTerm, role);\n"
                + "    }\n"
                + "    if (!box) return null;\n"
                + "    if (offset && Number.isFinite(offset.x) && Number.isFinite(offset.y)) {\n"
                + "      return { x: Math.trunc(box.x + offset.x), y: Math.trunc(box.y + offset.y) };\n"
                + "    }\n"
                + "    return { x: Math.trunc(box.x + box.width / 2), y: Math.trunc(box.y + box.height / 2) };\n"
                + "  };\n";
    }

    private static String buildCoordinateResolutionBody() {
        return "  let source = null;\n"
                + "  let target = null;\n"
                + "  if (params.element_source || params.element_target) {\n"
                + "    if (params.element_source) {\n"
                + "      source = await getPoint(params.element_source, params.element_source_offset, 'source');\n"
                + "      if (!source) {\n"
                + "        return { ok: false, error: 'Failed to determine source coordinates from selector. Use the exact visible text (e.g. \"Learn more\" not \"More information\") or a valid CSS/Playwright selector.', source: null, target: null };\n"
                + "      }\n"
                + "    }\n"
                + "    if (params.element_target) {\n"
                + "      target = await getPoint(params.element_target, params.element_target_offset, 'target');\n"
                + "      if (!target) {\n"
                + "        return { ok: false, error: 'Failed to determine target coordinates from selector', source, target: null };\n"
                + "      }\n"
                + "    }\n"
                + "  } else {\n"
                + "    const values = [params.coord_source_x, params.coord_source_y, params.coord_target_x, params.coord_target_y];\n"
                + "    const allFinite = values.every((v) => Number.isFinite(v));\n"
                + "    if (!allFinite) {\n"
                + "      return { ok: false, error: 'Must provide either source/target selectors or source/target coordinates' };\n"
                + "    }\n"
                + "    source = { x: Math.trunc(params.coord_source_x), y: Math.trunc(params.coord_source_y) };\n"
                + "    target = { x: Math.trunc(params.coord_target_x), y: Math.trunc(params.coord_target_y) };\n"
                + "  }\n"
                + "  return { ok: true, source, target, error: null };\n";
    }

    private static String buildDragOperationBody() {
        return "  let source = null;\n"
                + "  let target = null;\n"
                + "  if (params.element_source && params.element_target) {\n"
                + "    source = await getPoint(params.element_source, params.element_source_offset, 'source');\n"
                + "    target = await getPoint(params.element_target, params.element_target_offset, 'target');\n"
                + "    if (!source || !target) {\n"
                + "      return { ok: false, error: 'Failed to determine source or target coordinates from selectors', source, target };\n"
                + "    }\n"
                + "  } else {\n"
                + "    const values = [params.coord_source_x, params.coord_source_y, params.coord_target_x, params.coord_target_y];\n"
                + "    const allFinite = values.every((v) => Number.isFinite(v));\n"
                + "    if (!allFinite) {\n"
                + "      return { ok: false, error: 'Must provide either source/target selectors or source/target coordinates' };\n"
                + "    }\n"
                + "    source = { x: Math.trunc(params.coord_source_x), y: Math.trunc(params.coord_source_y) };\n"
                + "    target = { x: Math.trunc(params.coord_target_x), y: Math.trunc(params.coord_target_y) };\n"
                + "  }\n"
                + "  const steps = Math.max(1, Number.isFinite(params.steps) ? Math.trunc(params.steps) : 10);\n"
                + "  const delayMs = Math.max(0, Number.isFinite(params.delay_ms) ? Math.trunc(params.delay_ms) : 5);\n"
                + "  try {\n"
                + "    await page.mouse.move(source.x, source.y);\n"
                + "    await page.mouse.down();\n"
                + "    for (let i = 1; i <= steps; i += 1) {\n"
                + "      const ratio = i / steps;\n"
                + "      const x = Math.trunc(source.x + (target.x - source.x) * ratio);\n"
                + "      const y = Math.trunc(source.y + (target.y - source.y) * ratio);\n"
                + "      await page.mouse.move(x, y);\n"
                + "      if (delayMs > 0) {\n"
                + "        await new Promise((resolve) => setTimeout(resolve, delayMs));\n"
                + "      }\n"
                + "    }\n"
                + "    await page.mouse.move(target.x, target.y);\n"
                + "    await page.mouse.move(target.x, target.y);\n"
                + "    await page.mouse.up();\n"
                + "  } catch (error) {\n"
                + "    return {\n"
                + "      ok: false,\n"
                + "      error: `Error during drag operation: ${String(error)}`,\n"
                + "      source,\n"
                + "      target,\n"
                + "      steps,\n"
                + "      delay_ms: delayMs,\n"
                + "    };\n"
                + "  }\n"
                + "  const message = params.element_source && params.element_target\n"
                + "    ? `Dragged element '${params.element_source}' to '${params.element_target}'`\n"
                + "    : `Dragged from (${source.x}, ${source.y}) to (${target.x}, ${target.y})`;\n"
                + "  return { ok: true, message, source, target, steps, delay_ms: delayMs, error: null };\n";
    }

    private static Map<String, Object> coordinateResult(Map<String, Object> parsed, Map<String, Object> runtimeResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
        result.put("source", parsed.get("source"));
        result.put("target", parsed.get("target"));
        result.put("error", parsed.get("error"));
        if (runtimeResult != null) {
            result.put("runtime", runtimeResult);
        }
        return result;
    }

    private static Map<String, Object> dragResult(Map<String, Object> parsed, Map<String, Object> runtimeResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
        result.put("message", parsed.get("message"));
        result.put("source", parsed.get("source"));
        result.put("target", parsed.get("target"));
        result.put("steps", parsed.get("steps"));
        result.put("delay_ms", parsed.get("delay_ms"));
        result.put("error", parsed.get("error"));
        if (runtimeResult != null) {
            result.put("runtime", runtimeResult);
        }
        return result;
    }

    private static boolean hasSelectorInputs(Map<String, Object> params) {
        return (hasNonBlank(params, "element_source") || hasNonBlank(params, "source"))
                && (hasNonBlank(params, "element_target") || hasNonBlank(params, "target"));
    }

    private static boolean hasSourceSelector(Map<String, Object> params) {
        return hasNonBlank(params, "element_source") || hasNonBlank(params, "source");
    }

    private static boolean hasCoordinateInputs(Map<String, Object> params) {
        return hasFiniteNumber(params, "coord_source_x", "source_x")
                && hasFiniteNumber(params, "coord_source_y", "source_y")
                && hasFiniteNumber(params, "coord_target_x", "target_x")
                && hasFiniteNumber(params, "coord_target_y", "target_y");
    }

    private static Map<String, Object> buildDragPayload(Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String sourceSelector = trimToEmpty(stringValue(safeParams.get("element_source")));
        String targetSelector = trimToEmpty(stringValue(safeParams.get("element_target")));
        if (sourceSelector.isEmpty()) {
            sourceSelector = trimToEmpty(stringValue(safeParams.get("source")));
        }
        if (targetSelector.isEmpty()) {
            targetSelector = trimToEmpty(stringValue(safeParams.get("target")));
        }

        Integer sourceX = firstInt(safeParams.get("coord_source_x"), safeParams.get("source_x"));
        Integer sourceY = firstInt(safeParams.get("coord_source_y"), safeParams.get("source_y"));
        Integer targetX = firstInt(safeParams.get("coord_target_x"), safeParams.get("target_x"));
        Integer targetY = firstInt(safeParams.get("coord_target_y"), safeParams.get("target_y"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", trimToEmpty(stringValue(safeParams.get("url"))));
        payload.put("element_source", sourceSelector);
        payload.put("element_target", targetSelector);
        payload.put("element_source_offset", normalizeOffset(safeParams.get("element_source_offset")));
        payload.put("element_target_offset", normalizeOffset(safeParams.get("element_target_offset")));
        payload.put("coord_source_x", sourceX);
        payload.put("coord_source_y", sourceY);
        payload.put("coord_target_x", targetX);
        payload.put("coord_target_y", targetY);
        payload.put("steps", toInt(safeParams.get("steps")));
        payload.put("delay_ms", toInt(safeParams.get("delay_ms")));
        return payload;
    }

    private static Integer firstInt(Object primary, Object alias) {
        Integer resolved = toInt(primary);
        return resolved != null ? resolved : toInt(alias);
    }

    private static Map<String, Integer> normalizeOffset(Object value) {
        if (value == null) {
            return null;
        }
        Integer x = null;
        Integer y = null;
        if (value instanceof Map<?, ?> map) {
            x = toInt(map.get("x"));
            y = toInt(map.get("y"));
        } else {
            try {
                Method getX = value.getClass().getMethod("getX");
                Method getY = value.getClass().getMethod("getY");
                x = toInt(getX.invoke(value));
                y = toInt(getY.invoke(value));
            } catch (Exception ignored) {
                try {
                    var xField = value.getClass().getField("x");
                    var yField = value.getClass().getField("y");
                    x = toInt(xField.get(value));
                    y = toInt(yField.get(value));
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
        }
        if (x == null || y == null) {
            return null;
        }
        return Map.of("x", x, "y", y);
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static boolean hasFiniteNumber(Map<String, Object> params, String primary, String alias) {
        if (params == null) {
            return false;
        }
        return toInt(params.get(primary)) != null || toInt(params.get(alias)) != null;
    }

    private static Integer normalizeTimeout(Object value) {
        Integer resolved = toInt(value);
        return resolved == null || resolved <= 0 ? null : resolved;
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean hasNonBlank(Map<String, Object> params, String key) {
        return params != null && !trimToEmpty(stringValue(params.get(key))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> casted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    casted.put(key, entry.getValue());
                }
            }
            return casted;
        }
        return new LinkedHashMap<>();
    }

    private static List<String> stringList(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                addNonBlank(result, item);
            }
            return result;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int index = 0; index < length; index++) {
                addNonBlank(result, Array.get(raw, index));
            }
            return result;
        }
        addNonBlank(result, raw);
        return result;
    }

    private static void addNonBlank(List<String> result, Object value) {
        String text = trimToEmpty(stringValue(value));
        if (!text.isEmpty()) {
            result.add(text);
        }
    }

    private static Map<String, Object> errorResult(String action, String sessionId, String requestId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("action", action);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        result.put("error", error);
        return result;
    }

    private static String preview(Object value) {
        String text = stringValue(value);
        return text.length() <= 400 ? text : text.substring(0, 400);
    }

    private static String normalizeActionName(String name) {
        return trimToEmpty(name).toLowerCase();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public void setInBrowserWorkerAction(boolean inBrowserWorkerAction) {
        this.inBrowserWorkerAction = inBrowserWorkerAction;
    }

    @FunctionalInterface
    public interface ActionHandler {
        Object handle(String sessionId, String requestId, Map<String, Object> params) throws Exception;
    }

    @FunctionalInterface
    public interface RuntimeRunner {
        CompletionStage<Map<String, Object>> run(String task, String sessionId, String requestId, Integer timeoutS);
    }

    @FunctionalInterface
    public interface CodeExecutor {
        CompletionStage<Object> execute(String jsCode);
    }

    public record ActionSpec(String summary, String whenToUse, Map<String, String> params) {
        public ActionSpec {
            params = params == null ? Map.of() : Map.copyOf(params);
            summary = summary == null ? "" : summary;
            whenToUse = whenToUse == null ? "" : whenToUse;
        }
    }

    public static final class BrowserWorkerActionContext implements AutoCloseable {
        private final ActionController controller;
        private boolean closed;

        private BrowserWorkerActionContext(ActionController controller) {
            this.controller = controller;
            this.controller.setInBrowserWorkerAction(true);
        }

        @Override
        public void close() {
            if (!closed) {
                controller.setInBrowserWorkerAction(false);
                closed = true;
            }
        }
    }
}
