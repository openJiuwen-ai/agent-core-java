/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void initModelComponentsUsesModelPathAndTrustRemoteCode() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("model"));
        Map<String, Object> config = Map.of(
            "actor_rollout_ref", Map.of("model", Map.of("path", modelDir.toString())),
            "data", Map.of("trust_remote_code", true)
        );

        TaskRunner.ModelComponents components = TaskRunner.initModelComponents(config);

        assertEquals(modelDir.toAbsolutePath().normalize(), components.tokenizer().modelPath());
        assertEquals("tokenizer", components.tokenizer().componentType());
        assertEquals(true, components.tokenizer().options().get("trust_remote_code"));
        assertEquals(modelDir.toAbsolutePath().normalize(), components.processor().modelPath());
        assertEquals("processor", components.processor().componentType());
        assertEquals(true, components.processor().options().get("use_fast"));
    }

    @Test
    void copyToLocalCopiesLocalDirectoryRecursively() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("tokenizer.json"), "{}");
        Path target = tempDir.resolve("target");

        Path copied = TaskRunner.copyToLocal(source.toString(), target);

        assertEquals(target.toAbsolutePath().normalize(), copied);
        assertEquals("{}", Files.readString(target.resolve("tokenizer.json")));
    }

    @Test
    void copyToLocalUsesRemoteResolver() throws Exception {
        Path target = tempDir.resolve("remote-model");
        TaskRunner.setRemoteArtifactResolver((source, requestedTarget) -> {
            Files.createDirectories(requestedTarget);
            Files.writeString(requestedTarget.resolve("source.txt"), source);
            return requestedTarget;
        });
        try {
            Path copied = TaskRunner.copyToLocal("hf://repo/model", target);

            assertEquals(target.toAbsolutePath().normalize(), copied);
            assertEquals("hf://repo/model", Files.readString(target.resolve("source.txt")));
        } finally {
            TaskRunner.setRemoteArtifactResolver(null);
        }
    }

    @Test
    void copyToLocalFailsFastWhenRemoteResolverMissing() {
        TaskRunner.setRemoteArtifactResolver(null);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> TaskRunner.copyToLocal("s3://bucket/model", tempDir.resolve("unused"))
        );

        assertTrue(exception.getMessage().contains("remote artifact resolver is not configured"));
    }
}
