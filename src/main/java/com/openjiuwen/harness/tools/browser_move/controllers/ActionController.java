/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

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
        this.runtime = runtime;
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
        return new ArrayList<>(actions.keySet());
    }

    /**
     * Describe all registered actions.
     * <p>
     * Mirrors Python's {@code describe_actions} method.
     */
    public Map<String, ActionSpec> describeActions() {
        return new HashMap<>(actionSpecs);
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
                Map<String, Object> result = handler.handle(sid, rid, params);

                // Ensure standard response fields
                Map<String, Object> response = new LinkedHashMap<>(result);
                response.putIfAbsent("ok", true);
                response.putIfAbsent("action", actionName);
                response.putIfAbsent("session_id", sid);
                response.putIfAbsent("request_id", rid);
                response.putIfAbsent("error", null);

                boolean ok = Boolean.TRUE.equals(response.get("ok"));
                LOG.info("[ActionController] execute_action end action={} ok={}", actionName, ok);
                return ActionResult.success(actionName, sid, rid, response);
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

        // browser_task action
        registerAction("browser_task", (sid, rid, params) -> {
            if (runtimeRunner == null) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("ok", false);
                errorResult.put("error", "runtime_not_bound: call bind_runtime(...) before browser_task");
                return errorResult;
            }
            String task = params.getOrDefault("task", "").toString().trim();
            if (task.isEmpty()) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("ok", false);
                errorResult.put("error", "missing required parameter: task");
                return errorResult;
            }
            Integer timeoutS = params.containsKey("timeout_s") ? 
                Integer.parseInt(params.get("timeout_s").toString()) : null;
            
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
        }, true);
        registerActionSpec("browser_task", "Execute browser automation task",
            "Run Playwright browser task", Map.of("task", "Task description", "timeout_s", "Timeout in seconds"));

        LOG.info("[ActionController] registered {} builtin actions", actions.size());
    }

    /**
     * Invoke runtime runner for browser tasks.
     */
    private Map<String, Object> invokeRuntimeRunner(String task, String sessionId, String requestId, Integer timeoutS) {
        if (runtimeRunner instanceof Function fn) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("task", task);
            args.put("session_id", sessionId);
            args.put("request_id", requestId);
            if (timeoutS != null) {
                args.put("timeout_s", timeoutS);
            }
            Object result = fn.apply(args);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        }
        // Fallback: try to invoke via reflection
        try {
            java.lang.reflect.Method method = runtimeRunner.getClass().getMethod("runBrowserTask", String.class, String.class, String.class, Integer.class);
            Object result = method.invoke(runtimeRunner, task, sessionId, requestId, timeoutS);
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
        if (name == null) return null;
        String normalized = name.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        return normalized.isEmpty() ? null : normalized;
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
    }
}