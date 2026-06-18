/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

/**
 * Focused tests for the agent-team spawn package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.spawn} package facade in
 * {@code openjiuwen/agent_teams/spawn/__init__.py}.</p>
 */
class SpawnPackageTest {

    @Test
    void allPreservesPythonExportOrder() {
        assertEquals("openjiuwen/agent_teams/spawn/__init__.py", SpawnPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "InProcessSpawnHandle",
                "inprocess_spawn",
                "get_shared_db",
                "get_shared_runtime",
                "cleanup_shared_resources"
        ), SpawnPackage.all());
    }

    @Test
    void exportedSymbolsPointToTranslatedJavaSymbols() {
        assertTrue(SpawnPackage.exports("InProcessSpawnHandle"));
        assertTrue(SpawnPackage.exports("inprocess_spawn"));
        assertTrue(SpawnPackage.exports("get_shared_db"));
        assertTrue(SpawnPackage.translated("cleanup_shared_resources"));

        assertEquals(
                "openjiuwen.agent_teams.spawn.inprocess_handle.InProcessSpawnHandle",
                SpawnPackage.sourceFor("InProcessSpawnHandle")
        );
        assertEquals(
                "openjiuwen.agent_teams.spawn.inprocess_spawn.inprocess_spawn",
                SpawnPackage.sourceFor("inprocess_spawn")
        );
        assertEquals(
                "openjiuwen.agent_teams.spawn.shared_resources.get_shared_db",
                SpawnPackage.sourceFor("get_shared_db")
        );

        assertEquals(
                "com.openjiuwen.agent_teams.spawn.InProcessSpawnHandle",
                SpawnPackage.javaSymbolNameFor("InProcessSpawnHandle")
        );
        assertEquals(
                "com.openjiuwen.agent_teams.spawn.InProcessSpawn#inprocessSpawn",
                SpawnPackage.getAttr("inprocess_spawn")
        );
        assertEquals(
                "com.openjiuwen.agent_teams.spawn.SharedResources#getSharedRuntime",
                SpawnPackage.getAttr("get_shared_runtime")
        );
        assertEquals(
                "com.openjiuwen.agent_teams.spawn.SharedResources#cleanupSharedResources",
                SpawnPackage.getAttr("cleanup_shared_resources")
        );
        assertEquals(InProcessSpawnHandle.class, SpawnPackage.resolveType("InProcessSpawnHandle").orElseThrow());
    }

    @Test
    void getAttrRejectsMissingAttributesLikePythonFacade() {
        NoSuchElementException missing = assertThrows(
                NoSuchElementException.class,
                () -> SpawnPackage.getAttr("missing")
        );

        assertTrue(missing.getMessage().contains("has no attribute 'missing'"));
    }
}
