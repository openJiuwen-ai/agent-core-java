/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File-based rollout store.
 * <p>
 * Mirrors Python's {@code FileRolloutStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.store.file_store}.
 * <p>
 * This minimal Java port focuses on directory initialization and configuration access.
 */
public class FileRolloutStore {

    private final Path savePath;
    private final int flushInterval;

    public FileRolloutStore(String savePath, int flushInterval) {
        this.savePath = Path.of(savePath);
        this.flushInterval = Math.max(1, flushInterval);
        try {
            Files.createDirectories(this.savePath.resolve("train").resolve("rollouts"));
            Files.createDirectories(this.savePath.resolve("val").resolve("rollouts"));
            Files.createDirectories(this.savePath.resolve("step_summaries"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path getSavePath() { return savePath; }
    public int getFlushInterval() { return flushInterval; }
}
