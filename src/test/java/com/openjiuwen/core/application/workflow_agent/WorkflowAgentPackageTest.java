/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the workflow-agent package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.application.workflow_agent} package facade in
 * {@code openjiuwen/core/application/workflow_agent/__init__.py}.</p>
 */
class WorkflowAgentPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        assertEquals("openjiuwen/core/application/workflow_agent/__init__.py",
                WorkflowAgentPackage.PYTHON_MODULE);
        assertIterableEquals(List.of("WorkflowAgent", "WorkflowAgentConfig", "WorkflowController"),
                WorkflowAgentPackage.all());
        assertSame(WorkflowAgentPackage.EXPORTED_SYMBOLS, WorkflowAgentPackage.all());
    }

    @Test
    void resolvesExportSourcesAndJavaTypes() {
        assertTrue(WorkflowAgentPackage.exports("WorkflowAgent"));
        assertTrue(WorkflowAgentPackage.exports("WorkflowAgentConfig"));
        assertTrue(WorkflowAgentPackage.exports("WorkflowController"));
        assertFalse(WorkflowAgentPackage.exports("ControllerAgent"));

        assertEquals("openjiuwen.core.application.workflow_agent.workflow_agent.WorkflowAgent",
                WorkflowAgentPackage.sourceFor("WorkflowAgent"));
        assertEquals("openjiuwen.core.single_agent.legacy.WorkflowAgentConfig",
                WorkflowAgentPackage.sourceFor("WorkflowAgentConfig"));
        assertEquals("openjiuwen.core.application.workflow_agent.workflow_controller.WorkflowController",
                WorkflowAgentPackage.sourceFor("WorkflowController"));

        assertSame(WorkflowAgent.class, WorkflowAgentPackage.javaTypeFor("WorkflowAgent"));
        assertSame(WorkflowAgentConfig.class, WorkflowAgentPackage.javaTypeFor("WorkflowAgentConfig"));
        assertSame(WorkflowController.class, WorkflowAgentPackage.javaTypeFor("WorkflowController"));
    }
}
