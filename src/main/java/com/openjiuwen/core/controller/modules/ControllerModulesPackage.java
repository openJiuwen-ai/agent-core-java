/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import java.util.List;

/**
 * Public controller modules package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.controller.modules} in
 * {@code openjiuwen/core/controller/modules/__init__.py}.</p>
 */
public final class ControllerModulesPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/controller/modules/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "EventHandlerInput",
            "EventHandler",
            "EventQueue",
            "TaskManagerState",
            "TaskManager",
            "TaskFilter",
            "TaskExecutor",
            "TaskExecutorDependencies",
            "TaskExecutorRegistry",
            "TaskScheduler",
            "IntentRecognizer",
            "EventHandlerWithIntentRecognition"
    );

    private ControllerModulesPackage() {
    }
}
