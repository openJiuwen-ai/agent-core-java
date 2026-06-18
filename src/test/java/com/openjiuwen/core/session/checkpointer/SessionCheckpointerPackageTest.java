/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the checkpointer package facade.
 *
 * <p>Mirrors Python's {@code __all__} in
 * {@code openjiuwen/core/session/checkpointer/__init__.py}.</p>
 */
class SessionCheckpointerPackageTest {

    @Test
    void allReturnsPythonExportOrder() {
        assertEquals(List.of(
                "CheckpointerFactory",
                "CheckpointerProvider",
                "Checkpointer",
                "Storage",
                "build_key",
                "build_key_with_namespace",
                "SESSION_NAMESPACE_AGENT",
                "SESSION_NAMESPACE_AGENT_TEAM",
                "SESSION_NAMESPACE_WORKFLOW",
                "WORKFLOW_NAMESPACE_GRAPH"
        ), SessionCheckpointerPackage.all());
    }

    @Test
    void resolvesTypeAndSourceExports() {
        assertTrue(SessionCheckpointerPackage.exports("CheckpointerFactory"));
        assertEquals(
                "openjiuwen.core.session.checkpointer.checkpointer.CheckpointerFactory",
                SessionCheckpointerPackage.sourceFor("CheckpointerFactory")
        );
        assertEquals(
                "com.openjiuwen.core.session.checkpointer.CheckpointerFactory",
                SessionCheckpointerPackage.javaTypeNameFor("CheckpointerFactory")
        );
        assertSame(
                CheckpointerFactory.class,
                SessionCheckpointerPackage.resolveType("CheckpointerFactory").orElseThrow()
        );
        assertTrue(SessionCheckpointerPackage.resolveType("build_key").isEmpty());
    }

    @Test
    void exposesCheckpointerConstants() {
        assertEquals(
                Checkpointer.SESSION_NAMESPACE_AGENT,
                SessionCheckpointerPackage.constantValueFor("SESSION_NAMESPACE_AGENT")
        );
        assertEquals(
                Checkpointer.SESSION_NAMESPACE_AGENT_TEAM,
                SessionCheckpointerPackage.constantValueFor("SESSION_NAMESPACE_AGENT_TEAM")
        );
        assertEquals(
                Checkpointer.SESSION_NAMESPACE_WORKFLOW,
                SessionCheckpointerPackage.constantValueFor("SESSION_NAMESPACE_WORKFLOW")
        );
        assertEquals(
                Checkpointer.WORKFLOW_NAMESPACE_GRAPH,
                SessionCheckpointerPackage.constantValueFor("WORKFLOW_NAMESPACE_GRAPH")
        );
    }
}
