/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy;

import java.util.Arrays;
import java.util.List;

/**
 * Package metadata bridge for legacy controller exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/controller/legacy/__init__.py}.</p>
 */
public final class LegacyControllerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/controller/legacy/__init__.py";
    public static final String DESCRIPTION = "Controller legacy module - Agent controllers";

    public static final List<String> CONTROLLER_CLASSES = List.of("BaseController");
    public static final List<String> INTENT_CLASSES = List.of(
            "IntentDetectionController",
            "IntentType",
            "Intent",
            "TaskQueue"
    );
    public static final List<String> TASK_CLASSES = List.of(
            "Task",
            "TaskInput",
            "TaskStatus",
            "TaskResult"
    );
    public static final List<String> REASONER_CLASSES = List.of("IntentDetector", "Planner");
    public static final List<String> EVENT_CLASSES = List.of(
            "Event",
            "EventType",
            "EventPriority",
            "EventSource",
            "EventContent",
            "EventContext",
            "SourceType"
    );
    public static final List<String> CONFIG_CLASSES = List.of(
            "IntentDetectionConfig",
            "PlannerConfig",
            "ProactiveIdentifierConfig",
            "ReflectorConfig",
            "ReasonerConfig"
    );

    public static final List<String> ALL = concat(
            CONTROLLER_CLASSES,
            INTENT_CLASSES,
            TASK_CLASSES,
            REASONER_CLASSES,
            EVENT_CLASSES,
            CONFIG_CLASSES
    );

    private LegacyControllerPackage() {
    }

    @SafeVarargs
    private static List<String> concat(List<String>... groups) {
        return Arrays.stream(groups)
                .flatMap(List::stream)
                .toList();
    }
}
