/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.util.List;

/**
 * Public checkpointing package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing} in
 * {@code openjiuwen/agent_evolving/checkpointing/__init__.py}.</p>
 */
public final class CheckpointingPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/checkpointing/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "EvolveCheckpoint",
            "FileCheckpointStore",
            "EvolutionStore",
            "CheckpointManager",
            "DefaultCheckpointManager"
    );

    private CheckpointingPackage() {
    }
}
