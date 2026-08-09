/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
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
 * Module export facade now lives on {@link DeepAgentsFactory}.
 */
class HarnessPackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/harness/__init__.py", DeepAgentsFactory.PYTHON_MODULE);
    }

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        DeepAgent.class,
                        TaskLoopEventHandler.class,
                        TaskLoopEventExecutor.class,
                        DeepAgentConfig.class,
                        "AudioModelConfig",
                        "VisionModelConfig",
                        "create_deep_agent",
                        Workspace.class
                ),
                DeepAgentsFactory.exports()
        );
    }

    @Test
    void resolvesLazyAttributesAndUnknownNames() {
        assertEquals(DeepAgent.class, DeepAgentsFactory.getAttribute("DeepAgent"));
        assertEquals(TaskLoopEventHandler.class, DeepAgentsFactory.getAttribute("TaskLoopEventHandler"));
        assertEquals(TaskLoopEventExecutor.class, DeepAgentsFactory.getAttribute("TaskLoopEventExecutor"));
        assertEquals(DeepAgentConfig.class, DeepAgentsFactory.getAttribute("DeepAgentConfig"));
        assertEquals("AudioModelConfig", DeepAgentsFactory.getAttribute("AudioModelConfig"));
        assertEquals("VisionModelConfig", DeepAgentsFactory.getAttribute("VisionModelConfig"));
        assertEquals("create_deep_agent", DeepAgentsFactory.getAttribute("create_deep_agent"));
        assertEquals(Workspace.class, DeepAgentsFactory.getAttribute("Workspace"));

        assertThrows(IllegalArgumentException.class, () -> DeepAgentsFactory.getAttribute("missing"));
    }

    @Test
    void delegatesCreateDeepAgentToFactory() {
        Object model = new Object();
        DeepAgent agent = DeepAgentsFactory.createDeepAgent(model);

        assertSame(model, agent.getConfig().getModel());
        assertTrue(agent.getConfig().getSubagents().stream().anyMatch(item -> {
            if (item instanceof com.openjiuwen.harness.subagents.SubAgentConfig spec) {
                return spec.getAgentCard() != null && "general-purpose".equals(spec.getAgentCard().getName());
            }
            return false;
        }));
    }
}
