/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.hierarchical_msgbus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/__init__.py}.
 */
class HierarchicalMsgbusPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/core/multi_agent/teams/hierarchical_msgbus/__init__.py",
                HierarchicalMsgbusPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "HierarchicalTeam",
                "HierarchicalTeamConfig",
                "SupervisorAgent",
                "P2PAbilityManager"
        ), HierarchicalMsgbusPackage.all());
        assertSame(HierarchicalMsgbusPackage.ALL, HierarchicalMsgbusPackage.all());
    }

    @Test
    void exposesTranslatedDependencyTypes() {
        assertTrue(HierarchicalMsgbusPackage.exports("HierarchicalTeam"));
        assertTrue(HierarchicalMsgbusPackage.exports("P2PAbilityManager"));
        assertFalse(HierarchicalMsgbusPackage.exports("missing"));
        assertSame(HierarchicalTeam.class, HierarchicalMsgbusPackage.typeFor("HierarchicalTeam"));
        assertSame(HierarchicalTeamConfig.class, HierarchicalMsgbusPackage.typeFor("HierarchicalTeamConfig"));
        assertSame(SupervisorAgent.class, HierarchicalMsgbusPackage.typeFor("SupervisorAgent"));
        assertSame(P2PAbilityManager.class, HierarchicalMsgbusPackage.typeFor("P2PAbilityManager"));
    }
}
