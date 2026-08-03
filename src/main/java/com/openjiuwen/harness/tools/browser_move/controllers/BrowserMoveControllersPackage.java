/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for browser-move controller exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/browser_move/controllers/__init__.py}.</p>
 */
public final class BrowserMoveControllersPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/tools/browser_move/controllers/__init__.py";
    public static final String DESCRIPTION = "Controller package exports.";
    public static final String MODULE_ALIAS = "controllers";
    public static final Class<BaseController> BASE_CONTROLLER = BaseController.class;
    public static final Class<ActionController> ACTION_CONTROLLER = ActionController.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseController",
            "ActionController",
            "get_default_controller",
            "bind_runtime",
            "bind_runtime_runner",
            "clear_runtime_runner",
            "bind_code_executor",
            "clear_code_executor",
            "browser_worker_action_context",
            "register_action",
            "register_action_spec",
            "register_builtin_actions",
            "register_example_actions",
            "list_actions",
            "describe_actions",
            "run_action"
    );

    private BrowserMoveControllersPackage() {
    }

    public static ActionController getDefaultController() {
        return ActionController.getDefaultController();
    }

    public static void bindRuntime(Object runtime) {
        ActionController.bindRuntimeForDefault(runtime);
    }

    public static void bindRuntimeRunner(ActionController.RuntimeRunner runner) {
        ActionController.bindRuntimeRunnerForDefault(runner);
    }

    public static void clearRuntimeRunner() {
        ActionController.clearRuntimeRunnerForDefault();
    }

    public static void bindCodeExecutor(ActionController.CodeExecutor executor) {
        ActionController.bindCodeExecutorForDefault(executor);
    }

    public static void clearCodeExecutor() {
        ActionController.clearCodeExecutorForDefault();
    }

    public static ActionController.BrowserWorkerActionContext browserWorkerActionContext() {
        return ActionController.getDefaultController().browserWorkerActionContext();
    }

    public static void registerAction(String name, ActionController.ActionHandler handler) {
        registerAction(name, handler, true);
    }

    public static void registerAction(String name, ActionController.ActionHandler handler, boolean overwrite) {
        ActionController.registerActionForDefault(name, handler, overwrite);
    }

    public static void registerActionSpec(
            String name,
            String summary,
            String whenToUse,
            Map<String, String> params
    ) {
        ActionController.registerActionSpecForDefault(name, summary, whenToUse, params);
    }

    public static void registerBuiltinActions() {
        ActionController.registerBuiltinActions(ActionController.getDefaultController());
    }

    public static void registerExampleActions() {
        registerBuiltinActions();
    }

    public static List<String> listActions() {
        return ActionController.listActionsForDefault();
    }

    public static Map<String, Map<String, Object>> describeActions() {
        return ActionController.describeActionsForDefault();
    }

    public static Map<String, Object> runAction(
            String action,
            String sessionId,
            String requestId,
            Map<String, Object> kwargs
    ) {
        return ActionController.runActionForDefault(action, sessionId, requestId, kwargs);
    }
}
