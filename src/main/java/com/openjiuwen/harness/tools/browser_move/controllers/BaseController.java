/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base controller contract for action dispatchers.
 *
 * <p>Mirrors Python's {@code BaseController} in
 * {@code openjiuwen.harness.tools.browser_move.controllers.base}.
 */
public abstract class BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(BaseController.class);

    /** Registered action handlers. */
    protected final Map<String, Object> actions = new ConcurrentHashMap<>();

    /** Action specifications/metadata. */
    protected final Map<String, Map<String, Object>> actionSpecs = new ConcurrentHashMap<>();

    /** Bound runtime object. */
    protected Object runtime;

    /** Runtime runner callable. */
    protected Object runtimeRunner;

    /** Code executor callable. */
    protected Object codeExecutor;

    /**
     * Bind a runtime object used by runtime-backed actions.
     */
    public abstract void bindRuntime(Object runtime);

    /**
     * Bind a runtime runner callable.
     */
    public abstract void bindRuntimeRunner(Object runner);

    /**
     * Clear any currently bound runtime runner.
     */
    public abstract void clearRuntimeRunner();

    /**
     * Bind a direct code executor callable.
     */
    public abstract void bindCodeExecutor(Object executor);

    /**
     * Clear the bound code executor.
     */
    public abstract void clearCodeExecutor();

    /**
     * Register an action handler.
     */
    public void registerAction(String name, Object handler, boolean overwrite) {
        if (overwrite || !actions.containsKey(name)) {
            actions.put(name, handler);
            LOG.debug("[BaseController] register_action name={}", name);
        }
    }

    /**
     * Register metadata for an action.
     */
    public void registerActionSpec(String name, String summary, String whenToUse, Map<String, String> params) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("name", name);
        spec.put("summary", summary != null ? summary : "");
        spec.put("when_to_use", whenToUse != null ? whenToUse : "");
        if (params != null) {
            spec.put("params", params);
        }
        actionSpecs.put(name, spec);
        LOG.debug("[BaseController] register_action_spec name={}", name);
    }

    /**
     * List registered action names.
     */
    public List<String> listActions() {
        return new ArrayList<>(actions.keySet());
    }

    /**
     * Return metadata for registered actions.
     */
    public Map<String, Map<String, Object>> describeActions() {
        return new LinkedHashMap<>(actionSpecs);
    }

    /**
     * Check if action is registered.
     */
    public boolean hasAction(String name) {
        return actions.containsKey(name);
    }

    /**
     * Get action handler.
     */
    public Object getAction(String name) {
        return actions.get(name);
    }
}