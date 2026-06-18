/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for {@code openjiuwen/core/runner/resources_manager/resource_registry.py}.
 */
class ResourceRegistryTest {

    @Test
    void accessorsReturnStableBuckets() {
        ResourceRegistry registry = new ResourceRegistry();

        assertSame(registry.tool(), registry.tool());
        assertSame(registry.workflow(), registry.workflow());
        assertSame(registry.prompt(), registry.prompt());
        assertSame(registry.model(), registry.model());
        assertSame(registry.agent(), registry.agent());
        assertSame(registry.agentTeam(), registry.agentTeam());
        assertSame(registry.sysOperation(), registry.sysOperation());

        assertEquals("tool", registry.tool().kind());
        assertEquals("agent_team", registry.agentTeam().kind());
    }

    @Test
    void removeByIdStopsAtFirstMatchingPythonOrder() {
        ResourceRegistry registry = new ResourceRegistry();
        registry.tool().put("shared", "tool-value");
        registry.workflow().put("shared", "workflow-value");
        registry.agent().put("agent-only", "agent-value");
        registry.sysOperation().put("sys-only", "sys-value");

        registry.removeById("shared");
        assertFalse(registry.tool().contains("shared"));
        assertTrue(registry.workflow().contains("shared"));

        registry.removeById("agent-only");
        assertFalse(registry.agent().contains("agent-only"));

        registry.removeById("sys-only");
        assertFalse(registry.sysOperation().contains("sys-only"));
    }

    @Test
    void missingRemoveIsNoop() {
        ResourceRegistry registry = new ResourceRegistry();
        registry.prompt().put("prompt-1", "value");

        registry.removeById("missing");

        assertEquals(1, registry.prompt().size());
        assertEquals("value", registry.prompt().get("prompt-1"));
    }
}
