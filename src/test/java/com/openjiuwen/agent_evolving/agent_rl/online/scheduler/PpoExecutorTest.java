/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import com.openjiuwen.agent_evolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PpoExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void trainBatchRunsRunnerPublishesLoraNotifiesAndCleansFsdpCheckpoint() {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        RecordingNotifier notifier = new RecordingNotifier(false);
        FakeRunner runner = new FakeRunner();
        PpoExecutor executor = executor(repo, notifier, runner);

        String published = executor.trainBatch(
                "user1",
                List.of(sample("s1", 0.5), sample("s2", 1.0)),
                7,
                tempDir.resolve("runs").toString()
        );

        assertNotNull(published);
        assertTrue(Files.exists(Path.of(published).resolve("adapter_model.safetensors")));
        assertEquals("v1", repo.getLatest("user1").orElseThrow().version());
        assertEquals(0.75, repo.getLatest("user1").orElseThrow().rewardAvg(), 1e-9);
        assertEquals(List.of(Map.of("user_id", "user1", "lora_path", published)), notifier.calls);
        assertEquals("0,1", runner.trainingGpuIds);
        assertEquals(2, asMap(runner.config.get("trainer")).get("n_gpus_per_node"));
        assertEquals(1, runner.batches.size());
        assertFalse(Files.exists(runner.exportRunDir.resolve("fsdp_ckpt")));
        assertTrue(executor.isPpoInitialized());
    }

    @Test
    void trainBatchReturnsNullWithoutRepositoryAndSkipsNotifier() {
        RecordingNotifier notifier = new RecordingNotifier(false);
        FakeRunner runner = new FakeRunner();
        PpoExecutor executor = executor(null, notifier, runner);

        String published = executor.trainBatch("user1", List.of(sample("s1", 0.5)), 1, tempDir.toString());

        assertNull(published);
        assertEquals(List.of(), notifier.calls);
        assertEquals(1, runner.batches.size());
    }

    @Test
    void notifyFailureIsNonFatal() {
        LoRARepository repo = new LoRARepository(tempDir.resolve("repo").toString());
        RecordingNotifier notifier = new RecordingNotifier(true);
        FakeRunner runner = new FakeRunner();
        PpoExecutor executor = executor(repo, notifier, runner);

        String published = executor.trainBatch("user1", List.of(sample("s1", 0.5)), 1, tempDir.toString());

        assertNotNull(published);
        assertEquals(1, notifier.calls.size());
    }

    @Test
    void acloseClosesNotifierAndRunner() {
        RecordingNotifier notifier = new RecordingNotifier(false);
        FakeRunner runner = new FakeRunner();
        PpoExecutor executor = executor(null, notifier, runner);
        executor.trainBatch("user1", List.of(sample("s1", 0.5)), 1, tempDir.toString());

        executor.aclose();

        assertTrue(notifier.closed);
        assertTrue(runner.closed);
        assertFalse(executor.isPpoInitialized());
    }

    private PpoExecutor executor(LoRARepository repo, InferenceNotifier notifier, FakeRunner runner) {
        return new PpoExecutor(
                "/models/base",
                repo,
                notifier,
                2,
                "0,1",
                null,
                () -> runner,
                PpoExecutor.VerlDataProtoAdapter::new
        );
    }

    private Map<String, Object> sample(String sampleId, double score) {
        return Map.of(
                "sample_id", sampleId,
                "session_id", "sess",
                "trajectory", Map.of(
                        "prompt_ids", List.of(1, 2),
                        "response_ids", List.of(3),
                        "response_logprobs", List.of(-0.1)
                ),
                "judge", Map.of("score", score)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    static final class FakeRunner implements PpoExecutor.PpoRunner {
        Map<String, Object> config = Map.of();
        String trainingGpuIds;
        final List<Object> batches = new ArrayList<>();
        Path exportRunDir;
        boolean closed;

        @Override
        public void init(Map<String, Object> config, String trainingGpuIds) {
            this.config = config;
            this.trainingGpuIds = trainingGpuIds;
        }

        @Override
        public Map<String, Object> trainOnBatch(Object dataProto) {
            batches.add(dataProto);
            return Map.of("loss", 1.25, "ignored", "text");
        }

        @Override
        public String exportLora(Path runDir, String baseModelPath) {
            try {
                exportRunDir = runDir;
                Path peft = runDir.resolve("peft");
                Files.createDirectories(peft);
                Files.writeString(peft.resolve("adapter_model.safetensors"), "adapter");
                Files.createDirectories(runDir.resolve("fsdp_ckpt"));
                Files.writeString(runDir.resolve("fsdp_ckpt").resolve("checkpoint.txt"), "tmp");
                return peft.toString();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static final class RecordingNotifier extends InferenceNotifier {
        private final boolean failNotify;
        final List<Map<String, String>> calls = new ArrayList<>();
        boolean closed;

        RecordingNotifier(boolean failNotify) {
            super("http://127.0.0.1:65530", 1.0, null);
            this.failNotify = failNotify;
        }

        @Override
        public CompletionStage<Void> notifyUpdate(String userId, String loraPath) {
            calls.add(Map.of("user_id", userId, "lora_path", loraPath));
            if (failNotify) {
                return CompletableFuture.failedFuture(new RuntimeException("notify failed"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
