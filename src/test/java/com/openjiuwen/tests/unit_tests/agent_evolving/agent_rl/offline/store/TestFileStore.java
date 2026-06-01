/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.FileRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for FileRolloutStore.
 * <p>
 * Mirrors Python's {@code test_file_store_e2e.py} in
 * {@code tests/system_tests/agent_evolving/agent_rl/offline/store/}.
 */
@DisplayName("FileStore Tests")
class TestFileStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    @DisplayName("save and query train/val rollouts")
    void testFileStoreE2eSaveAndQueryTrainVal(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        RolloutMessage msgTrain = makeRolloutMessage("train-1", "o1", 0.8);
        RolloutMessage msgVal = makeRolloutMessage("val-1", "o1", 0.9);

        store.saveRollout(0, "train-1", msgTrain, "train");
        store.saveRollout(0, "val-1", msgVal, "val");

        assertThat(tmpDir.resolve("train").resolve("rollouts").resolve("steps_000000_000099.jsonl")).exists();
        assertThat(tmpDir.resolve("val").resolve("rollouts").resolve("steps_000000_000099.jsonl")).exists();

        List<Map<String, Object>> results = store.queryRollouts(Map.of(), 10);
        Set<Object> taskIds = results.stream().map(row -> row.get("task_id")).collect(Collectors.toSet());
        assertThat(taskIds).contains("train-1", "val-1");
    }

    @Test
    @DisplayName("save step summary")
    void testFileStoreE2eStepSummary(@TempDir Path tmpDir) throws Exception {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        store.saveStepSummary(150, Map.of("loss", 0.42, "reward_mean", 0.5));

        Path summaryFile = tmpDir.resolve("step_summaries").resolve("steps_000100_000199.jsonl");
        assertThat(summaryFile).exists();
        Map<String, Object> doc = MAPPER.readValue(Files.readString(summaryFile).strip(), MAP_TYPE);
        assertThat(doc.get("step")).isEqualTo(150);
        assertThat((Map<String, Object>) doc.get("metrics"))
                .containsEntry("loss", 0.42)
                .containsEntry("reward_mean", 0.5);
    }

    @Test
    @DisplayName("query with filter and limit")
    void testFileStoreE2eQueryWithFilterAndLimit(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        for (int i = 0; i < 5; i++) {
            store.saveRollout(0, "t" + i, makeRolloutMessage("t" + i, "o1", 1.0), "train");
        }

        List<Map<String, Object>> filtered = store.queryRollouts(Map.of("task_id", "t2"), 10);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst()).containsEntry("task_id", "t2");

        List<Map<String, Object>> limited = store.queryRollouts(Map.of(), 3);
        assertThat(limited).hasSize(3);
    }

    @Test
    @DisplayName("close is no-op")
    void testFileStoreE2eCloseNoError(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        store.close();
    }

    private static RolloutMessage makeRolloutMessage(String taskId, String originTaskId, double reward) {
        Rollout rollout = new Rollout();
        rollout.setTurnId(0);
        rollout.setInputPrompt(Map.of("message", List.of(Map.of("role", "user", "content", "hi"))));
        rollout.setOutputResponse(Map.of("role", "assistant", "content", "hello"));

        RolloutMessage message = new RolloutMessage();
        message.setTaskId(taskId);
        message.setOriginTaskId(originTaskId);
        message.setRolloutId("r1");
        message.setRolloutInfo(List.of(rollout));
        message.setRewardList(List.of(reward));
        message.setGlobalReward(reward);
        message.setTurnCount(1);
        message.setRoundNum(0);
        return message;
    }
}
