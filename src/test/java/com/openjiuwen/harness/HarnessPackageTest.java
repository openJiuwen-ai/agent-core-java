/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package exports in {@code openjiuwen/harness/__init__.py}.
 */
class HarnessPackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/harness/__init__.py", HarnessPackage.PYTHON_MODULE);
    }

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        DeepAgent.class,
                        TaskLoopEventHandler.class,
                        TaskLoopEventExecutor.class,
                        DeepAgentConfig.class,
                        DeepAgentConfig.AudioModelConfig.class,
                        DeepAgentConfig.VisionModelConfig.class,
                        "create_deep_agent",
                        Workspace.class
                ),
                HarnessPackage.exports()
        );
    }

    @Test
    void resolvesLazyAttributesAndUnknownNames() {
        assertEquals(DeepAgent.class, HarnessPackage.getAttribute("DeepAgent"));
        assertEquals(TaskLoopEventHandler.class, HarnessPackage.getAttribute("TaskLoopEventHandler"));
        assertEquals(TaskLoopEventExecutor.class, HarnessPackage.getAttribute("TaskLoopEventExecutor"));
        assertEquals(DeepAgentConfig.class, HarnessPackage.getAttribute("DeepAgentConfig"));
        assertEquals(DeepAgentConfig.AudioModelConfig.class, HarnessPackage.getAttribute("AudioModelConfig"));
        assertEquals(DeepAgentConfig.VisionModelConfig.class, HarnessPackage.getAttribute("VisionModelConfig"));
        assertEquals("create_deep_agent", HarnessPackage.getAttribute("create_deep_agent"));
        assertEquals(Workspace.class, HarnessPackage.getAttribute("Workspace"));

        assertThrows(IllegalArgumentException.class, () -> HarnessPackage.getAttribute("missing"));
    }

    @Test
    void delegatesCreateDeepAgentToFactory() {
        Object model = new Object();
        DeepAgent agent = HarnessPackage.createDeepAgent(model);

        assertSame(model, agent.deepConfig().getModel());
        assertTrue(agent.getSubagents().containsKey("general-purpose"));
    }
}
