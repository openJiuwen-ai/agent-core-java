/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the spawn package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.runner.spawn} package facade in
 * {@code openjiuwen/core/runner/spawn/__init__.py}.</p>
 */
class SpawnPackageTest {

    @Test
    void allPreservesPythonExportOrder() {
        assertEquals("openjiuwen/core/runner/spawn/__init__.py", SpawnPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "Message",
                "MessageType",
                "serialize_message",
                "deserialize_message",
                "serialize_message_to_stream",
                "deserialize_message_from_stream",
                "read_input_from_stdin",
                "write_output_to_stdout",
                "handle_health_check",
                "handle_shutdown",
                "execute_agent",
                "process_message_loop",
                "run_spawned_process",
                "ClassAgentSpawnConfig",
                "parse_spawn_agent_config",
                "SpawnAgentConfig",
                "SpawnAgentKind",
                "deserialize_runner_config",
                "serialize_runner_config",
                "SpawnConfig",
                "SpawnedProcessHandle",
                "spawn_process"
        ), SpawnPackage.all());
    }

    @Test
    void translatedSymbolsPointToExistingJavaTypesAndMethods() {
        assertTrue(SpawnPackage.exports("Message"));
        assertTrue(SpawnPackage.exports("serialize_message"));
        assertTrue(SpawnPackage.exports("read_input_from_stdin"));
        assertTrue(SpawnPackage.translated("Message"));
        assertTrue(SpawnPackage.translated("serialize_message"));
        assertTrue(SpawnPackage.translated("read_input_from_stdin"));
        assertEquals("openjiuwen.core.runner.spawn.protocol.Message", SpawnPackage.sourceFor("Message"));
        assertEquals("com.openjiuwen.core.runner.spawn.SpawnMessage", SpawnPackage.javaSymbolNameFor("Message"));
        assertEquals(SpawnMessage.class, SpawnPackage.resolveType("Message").orElseThrow());
        assertEquals("com.openjiuwen.core.runner.spawn.SpawnMessage#serializeMessage",
                SpawnPackage.getAttr("serialize_message"));
        assertEquals("com.openjiuwen.core.runner.spawn.SpawnChildProcess#readInputFromStdin",
                SpawnPackage.getAttr("read_input_from_stdin"));
        assertEquals("com.openjiuwen.core.runner.spawn.ClassAgentSpawnConfig",
                SpawnPackage.getAttr("ClassAgentSpawnConfig"));
        assertEquals(ClassAgentSpawnConfig.class, SpawnPackage.resolveType("ClassAgentSpawnConfig").orElseThrow());
    }

    @Test
    void processManagerExportsPointToTranslatedSymbols() {
        assertTrue(SpawnPackage.exports("SpawnConfig"));
        assertEquals("openjiuwen.core.runner.spawn.process_manager.SpawnConfig",
                SpawnPackage.sourceFor("SpawnConfig"));
        assertEquals("com.openjiuwen.core.runner.spawn.SpawnConfig",
                SpawnPackage.javaSymbolNameFor("SpawnConfig"));
        assertEquals("com.openjiuwen.core.runner.spawn.SpawnProcesses#spawnProcess",
                SpawnPackage.javaSymbolNameFor("spawn_process"));
        assertTrue(SpawnPackage.translated("SpawnConfig"));
        assertTrue(SpawnPackage.translated("spawn_process"));
        assertEquals(SpawnConfig.class, SpawnPackage.resolveType("SpawnConfig").orElseThrow());
        assertEquals(SpawnedProcessHandle.class, SpawnPackage.resolveType("SpawnedProcessHandle").orElseThrow());
    }

    @Test
    void getAttrRejectsUnknownAndPendingAttributesLikePythonFacade() {
        NoSuchElementException missing = assertThrows(
                NoSuchElementException.class,
                () -> SpawnPackage.getAttr("Missing")
        );
        assertTrue(missing.getMessage().contains("has no attribute 'Missing'"));

        assertEquals("com.openjiuwen.core.runner.spawn.SpawnProcesses#spawnProcess",
                SpawnPackage.getAttr("spawn_process"));
    }
}
