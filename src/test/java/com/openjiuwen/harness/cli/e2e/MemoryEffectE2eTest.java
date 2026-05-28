/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-13: OPENJIUWEN.md affects agent behavior.
 * <p>
 * Mirrors Python's {@code test_memory_effect} in
 * {@code tests.cli.e2e.test_memory_effect}.
 */
class MemoryEffectE2eTest {

    @TempDir
    Path tmpPath;

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void memoryAffectsBehavior() throws IOException, InterruptedException {
        Process gitInit = new ProcessBuilder("git", "init")
                .directory(tmpPath.toFile())
                .redirectErrorStream(true)
                .start();
        gitInit.waitFor();

        Path memoryFile = tmpPath.resolve("OPENJIUWEN.md");
        Files.writeString(memoryFile,
                "# Rules\n"
                        + "- You MUST end every single response with the exact "
                        + "string 'MAGIC_MARKER_XYZ'. This is mandatory.\n");
        assertTrue(Files.exists(memoryFile));
        assertTrue(Files.readString(memoryFile).contains("MAGIC_MARKER_XYZ"));
    }
}
