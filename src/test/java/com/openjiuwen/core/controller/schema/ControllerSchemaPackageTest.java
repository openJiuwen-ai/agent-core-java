/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's package surface in
 * {@code openjiuwen/core/controller/schema/__init__.py}.
 */
class ControllerSchemaPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        List<String> expected = List.of(
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

        assertEquals(
                "openjiuwen/core/controller/schema/__init__.py",
                ControllerSchemaPackage.PYTHON_MODULE
        );
        assertIterableEquals(expected, ControllerSchemaPackage.EXPORTED_SYMBOLS);
        assertSame(ControllerSchemaPackage.EXPORTED_SYMBOLS, ControllerSchemaPackage.all());
        assertIterableEquals(expected, ControllerSchemaPackage.EXPORTED_TYPES.keySet());
    }

    @Test
    void resolvesExportedTypes() {
        assertSame(DataFrame.TextDataFrame.class, ControllerSchemaPackage.typeFor("TextDataFrame"));
        assertSame(DataFrame.FileDataFrame.class, ControllerSchemaPackage.typeFor("FileDataFrame"));
        assertSame(DataFrame.JsonDataFrame.class, ControllerSchemaPackage.typeFor("JsonDataFrame"));
        assertSame(DataFrame.class, ControllerSchemaPackage.typeFor("DataFrame"));
        assertSame(EventType.class, ControllerSchemaPackage.typeFor("EventType"));
        assertSame(Event.class, ControllerSchemaPackage.typeFor("Event"));
        assertSame(InputEvent.class, ControllerSchemaPackage.typeFor("InputEvent"));
        assertSame(TaskInteractionEvent.class, ControllerSchemaPackage.typeFor("TaskInteractionEvent"));
        assertSame(TaskCompletionEvent.class, ControllerSchemaPackage.typeFor("TaskCompletionEvent"));
        assertSame(TaskFailedEvent.class, ControllerSchemaPackage.typeFor("TaskFailedEvent"));
        assertSame(ControllerOutputPayload.class, ControllerSchemaPackage.typeFor("ControllerOutputPayload"));
        assertSame(ControllerOutputChunk.class, ControllerSchemaPackage.typeFor("ControllerOutputChunk"));
        assertSame(ControllerOutput.class, ControllerSchemaPackage.typeFor("ControllerOutput"));
        assertSame(IntentType.class, ControllerSchemaPackage.typeFor("IntentType"));
        assertSame(Intent.class, ControllerSchemaPackage.typeFor("Intent"));
        assertSame(TaskStatus.class, ControllerSchemaPackage.typeFor("TaskStatus"));
        assertSame(Task.class, ControllerSchemaPackage.typeFor("Task"));
    }

    @Test
    void recordsPythonModelRebuildTargets() {
        List<Class<?>> expected = List.of(
                Task.class,
                TaskCompletionEvent.class,
                TaskInteractionEvent.class,
                TaskFailedEvent.class
        );

        assertIterableEquals(expected, ControllerSchemaPackage.MODEL_REBUILD_TARGETS);
        assertSame(ControllerSchemaPackage.MODEL_REBUILD_TARGETS, ControllerSchemaPackage.rebuildTargets());
    }
}
