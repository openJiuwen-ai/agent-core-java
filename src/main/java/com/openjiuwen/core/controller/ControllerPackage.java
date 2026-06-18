/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import java.util.List;

/**
 * Package metadata bridge for controller exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/controller/__init__.py}.</p>
 */
public final class ControllerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/controller/__init__.py";
    public static final String DESCRIPTION = "Controller module - Agent controllers";

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
    public static final List<String> NEW_CLASSES = List.of(
            "TextDataFrame",
            "FileDataFrame",
            "JsonDataFrame",
            "DataFrame",
            "EventType",
            "Event",
            "InputEvent",
            "TaskInteractionEvent",
            "TaskCompletionEvent",
            "TaskFailedEvent",
            "ControllerOutputPayload",
            "ControllerOutputChunk",
            "ControllerOutput",
            "IntentType",
            "Intent",
            "TaskStatus",
            "Task",
            "EventHandlerInput",
            "EventHandler",
            "EventQueue",
            "TaskManagerState",
            "TaskManager",
            "TaskFilter",
            "TaskExecutor",
            "TaskExecutorRegistry",
            "TaskScheduler",
            "ControllerConfig",
            "Controller",
            "IntentRecognizer",
            "EventHandlerWithIntentRecognition"
    );

    public static final List<String> ALL = concat(
            CONTROLLER_CLASSES,
            INTENT_CLASSES,
            TASK_CLASSES,
            REASONER_CLASSES,
            EVENT_CLASSES,
            CONFIG_CLASSES,
            NEW_CLASSES
    );

    private ControllerPackage() {
    }

    @SafeVarargs
    private static List<String> concat(List<String>... groups) {
        return java.util.Arrays.stream(groups)
                .flatMap(List::stream)
                .toList();
    }
}
