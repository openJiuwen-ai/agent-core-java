/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the checkpointing package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing} in
 * {@code openjiuwen/agent_evolving/checkpointing/__init__.py}.</p>
 */
class CheckpointingPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/checkpointing/__init__.py", CheckpointingPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "EvolveCheckpoint",
                "FileCheckpointStore",
                "EvolutionStore",
                "CheckpointManager",
                "DefaultCheckpointManager"
        ), CheckpointingPackage.EXPORTED_SYMBOLS);
    }
}
