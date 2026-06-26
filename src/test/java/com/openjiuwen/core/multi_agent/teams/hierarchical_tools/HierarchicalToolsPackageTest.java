/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.hierarchical_tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.core.multi_agent.teams.hierarchical_tools} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_tools/__init__.py}.
 */
class HierarchicalToolsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/core/multi_agent/teams/hierarchical_tools/__init__.py",
                HierarchicalToolsPackage.PYTHON_MODULE);
        assertEquals(List.of("HierarchicalTeamConfig", "HierarchicalTeam"), HierarchicalToolsPackage.all());
        assertSame(HierarchicalToolsPackage.ALL, HierarchicalToolsPackage.all());
    }

    @Test
    void exposesTranslatedTypes() {
        assertTrue(HierarchicalToolsPackage.exports("HierarchicalTeamConfig"));
        assertTrue(HierarchicalToolsPackage.exports("HierarchicalTeam"));
        assertFalse(HierarchicalToolsPackage.exports("missing"));
        assertSame(HierarchicalTeamConfig.class, HierarchicalToolsPackage.typeFor("HierarchicalTeamConfig"));
        assertSame(HierarchicalTeam.class, HierarchicalToolsPackage.typeFor("HierarchicalTeam"));
    }
}
