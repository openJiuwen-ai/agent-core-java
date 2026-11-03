/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherWorkspaceTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureWorkspaceWritesExpectedEnvEntries() throws Exception {
        Path envFile = tempDir.resolve(".env");
        LauncherWorkspace.ensureWorkspace(
                envFile,
                "http://gateway.local",
                "glm-5",
                "/models/test",
                "batch",
                null,
                8,
                Map.of("WEB_USER_ID", "tester")
        );

        String text = Files.readString(envFile);
        assertTrue(text.contains("API_BASE=\"http://gateway.local\""));
        assertTrue(text.contains("MODEL_NAME=\"glm-5\""));
        assertTrue(text.contains("TRAJECTORY_BATCH_SIZE=8"));
        assertTrue(text.contains("CUSTOM_HEADERS='{\"x-user-id\":\"tester\"}'"));
    }
}
