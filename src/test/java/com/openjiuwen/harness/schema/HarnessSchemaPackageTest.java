/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.openjiuwen.harness.schema.task.TodoStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package exports in {@code openjiuwen/harness/schema/__init__.py}.
 */
class HarnessSchemaPackageTest {

    @Test
    void exportsMatchPythonAllSizeAndKeyMembers() {
        assertEquals(16, HarnessSchemaPackage.exports().size());
        assertTrue(HarnessSchemaPackage.exports().contains(AgentMode.class));
        assertTrue(HarnessSchemaPackage.exports().contains(DeepAgentConfig.SubAgentConfig.class));
        assertTrue(HarnessSchemaPackage.exports().contains("create_loop_event"));
        assertTrue(HarnessSchemaPackage.exports().contains("default_event_priority"));
    }

    @Test
    void delegatesLoopEventHelpers() {
        DeepLoopEvent event = HarnessSchemaPackage.createLoopEvent(
                7,
                DeepLoopEventType.STEER,
                "go",
                "task-1",
                null,
                null
        );

        assertEquals(DeepLoopEventType.STEER.getDefaultPriority(), event.getPriority());
        assertEquals(DeepLoopEventType.ABORT.getDefaultPriority(),
                HarnessSchemaPackage.defaultEventPriority(DeepLoopEventType.ABORT));
    }

    @Test
    void exposesStatusIconsFromTodoStatus() {
        assertEquals(TodoStatus.PENDING.getStatusIcon(), HarnessSchemaPackage.STATUS_ICONS.get(TodoStatus.PENDING));
        assertEquals(TodoStatus.COMPLETED.getStatusIcon(), HarnessSchemaPackage.STATUS_ICONS.get(TodoStatus.COMPLETED));
    }
}
