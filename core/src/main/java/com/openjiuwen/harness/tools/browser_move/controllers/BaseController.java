/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import java.util.List;
import java.util.Map;

/**
 * Base controller contract for action dispatchers.
 *
 * <p>Mirrors Python's {@code BaseController} in
 * {@code openjiuwen/harness/tools/browser_move/controllers/base.py}.
 */
public interface BaseController {

    void bindRuntime(Object runtime);

    void bindRuntimeRunner(Object runner);

    void clearRuntimeRunner();

    void bindCodeExecutor(Object executor);

    void clearCodeExecutor();

    void registerAction(String name, Object handler, boolean overwrite);

    void registerActionSpec(
            String name,
            String summary,
            String whenToUse,
            Map<String, String> params
    );

    List<String> listActions();

    Map<String, Map<String, Object>> describeActions();

    Map<String, Object> runAction(
            String action,
            String sessionId,
            String requestId,
            Map<String, Object> kwargs
    );
}
