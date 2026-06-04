/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoRARepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void publishAndGetLatestCopiesArtifactAndMetadata() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        Path loraDir = makeLoraDir("adapter");

        LoRARepository.LoRAVersion version = repo.publish(
                "user1",
                loraDir.toString(),
                Map.of("trajectory_count", 10, "reward_avg", 0.6),
                "/models/base"
        );

        assertEquals("v1", version.version());
        assertEquals(10, version.trajectoryCount());
        assertEquals(0.6, version.rewardAvg(), 1e-9);
        assertTrue(Files.exists(Path.of(version.path()).resolve("adapter_model.safetensors")));

        LoRARepository.LoRAVersion latest = repo.getLatest("user1").orElseThrow();
        assertEquals("v1", latest.version());
        assertEquals("/models/base", latest.baseModel());
    }

    @Test
    void latestPointsToNewestVersion() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        for (int i = 0; i < 3; i++) {
            repo.publish("user1", makeLoraDir("adapter-" + i).toString(), Map.of("sample_count", i), "");
        }

        LoRARepository.LoRAVersion latest = repo.getLatest("user1").orElseThrow();

        assertEquals("v3", latest.version());
        assertEquals(3, repo.listVersions("user1").size());
    }

    @Test
    void getLatestReturnsEmptyForNewUser() {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());

        assertTrue(repo.getLatest("missing").isEmpty());
    }

    @Test
    void publishAcceptsSchedulerMetadataKeys() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());

        LoRARepository.LoRAVersion version = repo.publish(
                "user1",
                makeLoraDir("adapter").toString(),
                Map.of("sample_count", 12, "avg_score", 0.75),
                ""
        );

        assertEquals(12, version.trajectoryCount());
        assertEquals(0.75, version.rewardAvg(), 1e-9);
    }

    @Test
    void publishIgnoresNonNumericVersionDirs() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        Files.createDirectories(repo.getRepoPath().resolve("user1").resolve("v_test"));

        LoRARepository.LoRAVersion version = repo.publish("user1", makeLoraDir("adapter").toString());

        assertEquals("v1", version.version());
    }

    @Test
    void adapterCompatibilityMethodsUseLatestVersion() throws Exception {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        repo.saveAdapter("adapterA", makeLoraDir("adapter").toString());

        Object loaded = repo.loadAdapter("adapterA");

        assertNotNull(loaded);
        assertTrue(Files.exists(Path.of(String.valueOf(loaded)).resolve("adapter_model.safetensors")));
        assertEquals(java.util.List.of("adapterA"), repo.listAdapters());
        assertFalse(repo.listVersions("adapterA").isEmpty());
    }

    private Path makeLoraDir(String name) throws Exception {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("adapter_model.safetensors"), "dummy");
        return dir;
    }
}
