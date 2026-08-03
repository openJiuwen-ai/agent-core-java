/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for controller schema exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.controller.schema} package facade in
 * {@code openjiuwen/core/controller/schema/__init__.py}.</p>
 */
public final class ControllerSchemaPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/controller/schema/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
            "Task"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    public static final List<Class<?>> MODEL_REBUILD_TARGETS = List.of(
            Task.class,
            TaskCompletionEvent.class,
            TaskInteractionEvent.class,
            TaskFailedEvent.class
    );

    private ControllerSchemaPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Resolve an exported Python symbol to the Java type used for that schema.
     *
     * @param exportedName the Python export name
     * @return the corresponding Java type, or {@code null} when not exported
     */
    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    /**
     * Java has no Pydantic forward-reference rebuild step; this records the same
     * target order that Python invokes with {@code model_rebuild()}.
     *
     * @return model rebuild targets in Python execution order
     */
    public static List<Class<?>> rebuildTargets() {
        return MODEL_REBUILD_TARGETS;
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("TextDataFrame", DataFrame.TextDataFrame.class);
        exports.put("FileDataFrame", DataFrame.FileDataFrame.class);
        exports.put("JsonDataFrame", DataFrame.JsonDataFrame.class);
        exports.put("DataFrame", DataFrame.class);
        exports.put("EventType", EventType.class);
        exports.put("Event", Event.class);
        exports.put("InputEvent", InputEvent.class);
        exports.put("TaskInteractionEvent", TaskInteractionEvent.class);
        exports.put("TaskCompletionEvent", TaskCompletionEvent.class);
        exports.put("TaskFailedEvent", TaskFailedEvent.class);
        exports.put("ControllerOutputPayload", ControllerOutputPayload.class);
        exports.put("ControllerOutputChunk", ControllerOutputChunk.class);
        exports.put("ControllerOutput", ControllerOutput.class);
        exports.put("IntentType", IntentType.class);
        exports.put("Intent", Intent.class);
        exports.put("TaskStatus", TaskStatus.class);
        exports.put("Task", Task.class);
        return Collections.unmodifiableMap(exports);
    }
}
