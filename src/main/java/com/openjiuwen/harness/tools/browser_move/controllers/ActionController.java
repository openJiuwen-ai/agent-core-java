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
     * Execute an action by name.
     *
     * @param name Action name
     * @param params Action parameters
     * @return CompletableFuture with action result
     */
    public CompletableFuture<ActionResult> executeAction(String name, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("[ActionController] execute_action name={} params={}", name, params.keySet());

                Object handler = getAction(name);
                if (handler == null) {
                    return ActionResult.error("Action not found: " + name);
                }

                // Placeholder - actual implementation depends on handler invocation
                Object result = invokeHandler(handler, params);
                return ActionResult.success(result);
            } catch (Exception e) {
                LOG.error("[ActionController] execute_action failed name={}", name, e);
                return ActionResult.error(e.getMessage());
            }
        });
    }

    /**
     * Invoke handler with parameters.
     */
    private Object invokeHandler(Object handler, Map<String, Object> params) {
        // Placeholder - actual implementation depends on handler type
        LOG.debug("[ActionController] invoke_handler");
        return Collections.singletonMap("result", "Action executed");
    }

    /**
     * Action result wrapper.
     */
    @Data
    @Builder
    public static class ActionResult {
        private boolean success;
        private Object data;
        private String error;

        public static ActionResult success(Object data) {
            return ActionResult.builder().success(true).data(data).build();
        }

        public static ActionResult error(String error) {
            return ActionResult.builder().success(false).error(error).build();
        }
    }
}