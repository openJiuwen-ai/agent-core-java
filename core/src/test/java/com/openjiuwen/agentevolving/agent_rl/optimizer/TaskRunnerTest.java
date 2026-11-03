/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.rl_trainer.VerlConverter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    @Disabled("Legacy Python packaging layout check; Java SDK CI does not checkout ../agent-core-0.1.14")
    void runtimeEnvPrependsAgentCoreAndFiltersPackageSubdir() {
        Map<String, Object> runtimeEnv = TaskRunner.getPpoRayRuntimeEnv();

        @SuppressWarnings("unchecked")
        Map<String, String> envVars = (Map<String, String>) runtimeEnv.get("env_vars");

        assertEquals("true", envVars.get("TOKENIZERS_PARALLELISM"));
        assertTrue(envVars.get("PYTHONPATH").contains("agent-core-0.1.14")
                || envVars.get("PYTHONPATH").contains("python_to_java_v2"));
        assertFalse(envVars.get("PYTHONPATH").endsWith("openjiuwen/agent_evolving"));
    }

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

    @Test
    void offlineRunnerInitializesTrainerAndRejectsUnsupportedStrategy() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("offline-model"));
        OfflineTaskRunner runner = new OfflineTaskRunner();

        runner.initTrainer(config(modelDir, "fsdp", "sync", 2, 1));

        assertTrue(runner.isReady());
        assertEquals(0, runner.getGlobalSteps());
        assertEquals("tokenizer", runner.getTokenizer().componentType());

        assertThrows(
                RuntimeException.class,
                () -> runner.initWorkerMapping(config(modelDir, "megatron", "sync", 2, 1))
        );
    }

    @Test
    void onlineRunnerTrainsBatchAndExportsLoraDirectory() throws Exception {
        Path modelDir = Files.createDirectory(tempDir.resolve("online-model"));
        OnlineTaskRunner runner = new OnlineTaskRunner();
        runner.initTrainer(config(modelDir, "fsdp", "sync", 1, 1));

        Map<String, Object> metrics = runner.trainOnBatch(new VerlConverter.DataProto(
                Map.of(
                        "responses", new int[][] {{1}, {2}},
                        "token_level_scores", new double[][] {{0.5}, {1.0}}
                ),
                Map.of(
                        "data_id_list", List.of("case-a", "case-b"),
                        "sample_id", List.of("case-a", "case-b")
                ),
                Map.of()
        ));
        String exported = runner.exportLora(tempDir.resolve("lora").toString(), modelDir.toString());

        assertTrue(runner.isReady());
        assertEquals(1, runner.getGlobalSteps());
        assertEquals(2, metrics.get("training/batch_size"));
        assertTrue(Files.exists(Path.of(exported).resolve("adapter_config.json")));
    }

    private static Map<String, Object> config(Path modelDir,
                                              String strategy,
                                              String rolloutMode,
                                              int gpusPerNode,
                                              int nodes) {
        Map<String, Object> actorRolloutRef = new LinkedHashMap<>();
        actorRolloutRef.put("model", new LinkedHashMap<>(Map.of("path", modelDir.toString())));
        actorRolloutRef.put("actor", new LinkedHashMap<>(Map.of("strategy", strategy)));
        actorRolloutRef.put("rollout", new LinkedHashMap<>(Map.of("mode", rolloutMode)));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("extra_info", "{\"index\":0}", "messages", List.of())));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trust_remote_code", true);
        data.put("train_files", rows);
        data.put("val_files", rows);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("actor_rollout_ref", actorRolloutRef);
        config.put("data", data);
        config.put("trainer", new LinkedHashMap<>(Map.of("n_gpus_per_node", gpusPerNode, "nnodes", nodes)));
        config.put("reward_model", new LinkedHashMap<>(Map.of("reward_kwargs", Map.of())));
        return config;
    }
}
