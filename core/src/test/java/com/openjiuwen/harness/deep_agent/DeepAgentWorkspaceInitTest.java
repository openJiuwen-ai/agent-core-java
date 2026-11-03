/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aligns with Python {@code DeepAgent._needs_workspace_init} / {@code init_workspace}:
 * when workspace and sys_operation are present, DirectoryBuilder materializes the schema
 * unless the root already has a {@code .workspace} marker.
 */
class DeepAgentWorkspaceInitTest {

    @TempDir
    private Path tempDir;

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void ensureInitializedCreatesDefaultWorkspaceFiles() {
        agent = HarnessFactory.createDeepAgent(
                uniqueCard("ws-init"),
                DeepAgentConfig.builder().workspacePath(tempDir.toString()).build(),
                null);

        assertThat(tempDir.resolve("AGENT.md")).exists();
        assertThat(tempDir.resolve("memory").resolve(".workspace")).exists();
        assertThat(tempDir.resolve("todo").resolve(".workspace")).exists();
    }

    @Test
    void ensureInitializedSkipsWhenRootWorkspaceMarkerExists() throws Exception {
        Files.writeString(tempDir.resolve(".workspace"), "", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("AGENT.md"), "keep existing", StandardCharsets.UTF_8);

        agent = HarnessFactory.createDeepAgent(
                uniqueCard("ws-skip"),
                DeepAgentConfig.builder().workspacePath(tempDir.toString()).build(),
                null);

        assertThat(Files.readString(tempDir.resolve("AGENT.md"), StandardCharsets.UTF_8))
                .isEqualTo("keep existing");
        assertThat(tempDir.resolve("memory").resolve(".workspace")).doesNotExist();
    }

    @Test
    void ensureInitializedSkipsRelativeWorkspacePath() {
        Path relativeRoot = Path.of("ws-rel-" + UUID.randomUUID().toString().replace("-", ""));
        try {
            agent = HarnessFactory.createDeepAgent(
                    uniqueCard("ws-rel"),
                    DeepAgentConfig.builder().workspacePath(relativeRoot.toString()).build(),
                    null);

            assertThat(relativeRoot.resolve("AGENT.md")).doesNotExist();
        } finally {
            deleteRecursively(relativeRoot);
        }
    }

    @Test
    void ensureInitializedHonorsAutoCreateWorkspaceFalse() {
        agent = HarnessFactory.createDeepAgent(
                uniqueCard("ws-off"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .autoCreateWorkspace(false)
                        .build(),
                null);

        assertThat(tempDir.resolve("AGENT.md")).doesNotExist();
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(prefix).description("workspace init test").build();
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // best-effort cleanup of a relative test directory
                }
            });
        } catch (java.io.IOException ignored) {
            // best-effort cleanup of a relative test directory
        }
    }
}
