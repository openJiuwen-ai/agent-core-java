/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PpoConfigComposerTest {

    @Test
    void builtInOverlaySetsRuntimeFieldsAndPreservesOverlayDefaults() {
        Map<String, Object> cfg = PpoConfigComposer.composeOnlinePpoConfig("/models/qwen", 4, null);

        assertEquals("/models/qwen", nested(cfg, "actor_rollout_ref", "model").get("path"));
        assertEquals(4, nested(cfg, "trainer").get("n_gpus_per_node"));
        assertEquals("/tmp/online_ppo_ckpt", nested(cfg, "trainer").get("default_local_dir"));
        assertEquals("reinforce_plus_plus", nested(cfg, "algorithm").get("adv_estimator"));
    }

    @Test
    void builtInOverlayReturnsFreshDeepCopy() {
        Map<String, Object> first = PpoConfigComposer.composeOnlinePpoConfig("/models/a", 2, null);
        Map<String, Object> second = PpoConfigComposer.composeOnlinePpoConfig("/models/b", 2, null);

        assertNotSame(nested(first, "actor_rollout_ref", "model"), nested(second, "actor_rollout_ref", "model"));
        assertEquals("/models/a", nested(first, "actor_rollout_ref", "model").get("path"));
        assertEquals("/models/b", nested(second, "actor_rollout_ref", "model").get("path"));
    }

    @Test
    void customYamlIsLoadedAndRuntimeFieldsAreApplied(@TempDir Path tempDir) throws Exception {
        Path yaml = tempDir.resolve("custom_ppo.yaml");
        Files.writeString(yaml, """
                data:
                  train_batch_size: 3
                trainer:
                  n_gpus_per_node: 1
                  default_local_dir: ''
                actor_rollout_ref:
                  model:
                    path: old-model
                  rollout:
                    n: 2
                """);

        Map<String, Object> cfg = PpoConfigComposer.composeOnlinePpoConfig("/models/custom", 8, yaml.toString());

        assertEquals(3, nested(cfg, "data").get("train_batch_size"));
        assertEquals("/models/custom", nested(cfg, "actor_rollout_ref", "model").get("path"));
        assertEquals(2, nested(cfg, "actor_rollout_ref", "rollout").get("n"));
        assertEquals(8, nested(cfg, "trainer").get("n_gpus_per_node"));
        assertEquals("/tmp/online_ppo_ckpt", nested(cfg, "trainer").get("default_local_dir"));
    }

    @Test
    void missingCustomYamlFailsLikeHydraCompose() {
        assertThrows(IllegalArgumentException.class,
                () -> PpoConfigComposer.composeOnlinePpoConfig("/models/missing", 2, "missing.yaml"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String... path) {
        Object current = source;
        for (String segment : path) {
            current = ((Map<String, Object>) current).get(segment);
        }
        return (Map<String, Object>) current;
    }
}
