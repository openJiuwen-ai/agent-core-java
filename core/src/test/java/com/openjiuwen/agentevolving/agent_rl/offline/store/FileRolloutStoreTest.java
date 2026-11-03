/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
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
 * Mirrors Python's focused file-store coverage for
 * {@code openjiuwen/agent_evolving/agent_rl/offline/store/file_store.py}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.agent_rl.offline.store.test_file_store} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/store/test_file_store.py}.</p>
 *
 * <p>Also mirrors Python's {@code tests/system_tests/agent_evolving/agent_rl/offline/store/test_file_store_e2e.py}.
 * </p>
 */
@DisplayName("FileRolloutStore Tests")
class FileRolloutStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void saveTrainRolloutCreatesJsonl(@TempDir Path tmpDir) throws Exception {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        store.saveRollout(0, "t1", makeRolloutMessage("t1", "o1", 1.0d), "train");

        Path file = tmpDir.resolve("train").resolve("rollouts").resolve("steps_000000_000099.jsonl");
        assertThat(file).exists();
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(1);
        Map<String, Object> doc = MAPPER.readValue(lines.get(0), MAP_TYPE);
        assertThat(doc).containsEntry("task_id", "t1").containsEntry("global_reward", 1.0d);
    }

    @Test
    void defaultPhaseIsTrain(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        store.saveRollout(0, "t1", makeRolloutMessage("t1", "o1", 1.0d));

        assertThat(tmpDir.resolve("train").resolve("rollouts").resolve("steps_000000_000099.jsonl")).exists();
        assertThat(tmpDir.resolve("val").resolve("rollouts").resolve("steps_000000_000099.jsonl")).doesNotExist();
    }

    @Test
    void saveValRolloutCreatesJsonlInValDir(@TempDir Path tmpDir) throws Exception {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        store.saveRollout(5, "v1", makeRolloutMessage("v1", "o1", 1.0d), "val");

        Path file = tmpDir.resolve("val").resolve("rollouts").resolve("steps_000000_000099.jsonl");
        assertThat(file).exists();
        Map<String, Object> doc = MAPPER.readValue(Files.readString(file).strip(), MAP_TYPE);
        assertThat(doc).containsEntry("task_id", "v1");
    }

    @Test
    void trainAndValStayInSeparateDirs(@TempDir Path tmpDir) throws Exception {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        store.saveRollout(0, "train1", makeRolloutMessage("train1", "o1", 1.0d), "train");
        store.saveRollout(0, "val1", makeRolloutMessage("val1", "o1", 1.0d), "val");

        Path trainFile = tmpDir.resolve("train").resolve("rollouts").resolve("steps_000000_000099.jsonl");
        Path valFile = tmpDir.resolve("val").resolve("rollouts").resolve("steps_000000_000099.jsonl");
        assertThat(trainFile).exists();
        assertThat(valFile).exists();
        assertThat(MAPPER.readValue(Files.readString(trainFile).strip(), MAP_TYPE)).containsEntry("task_id", "train1");
        assertThat(MAPPER.readValue(Files.readString(valFile).strip(), MAP_TYPE)).containsEntry("task_id", "val1");
    }

    @Test
    void saveStepSummaryCreatesJsonl(@TempDir Path tmpDir) throws Exception {
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
    void fileSplittingHonorsFlushInterval(@TempDir Path tmpDir) throws Exception {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 3);

        for (int index = 0; index < 6; index++) {
            store.saveRollout(index, "t" + index, makeRolloutMessage("t" + index, "o1", 1.0d), "train");
        }

        Path firstFile = tmpDir.resolve("train").resolve("rollouts").resolve("steps_000000_000002.jsonl");
        Path secondFile = tmpDir.resolve("train").resolve("rollouts").resolve("steps_000003_000005.jsonl");
        assertThat(firstFile).exists();
        assertThat(secondFile).exists();
        assertThat(Files.readAllLines(firstFile)).hasSize(3);
        assertThat(Files.readAllLines(secondFile)).hasSize(3);
    }

    @Test
    void queryRolloutsSearchesBothTrainAndVal(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        store.saveRollout(0, "t1", makeRolloutMessage("t1", "o1", 1.0d), "train");
        store.saveRollout(0, "v1", makeRolloutMessage("v1", "o1", 1.0d), "val");

        List<Map<String, Object>> results = store.queryRollouts(Map.of(), 10);
        Set<Object> taskIds = results.stream().map(row -> row.get("task_id")).collect(Collectors.toSet());
        assertThat(taskIds).contains("t1", "v1");
    }

    @Test
    void queryRolloutsWithFilter(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        store.saveRollout(0, "t1", makeRolloutMessage("t1", "o1", 1.0d));
        store.saveRollout(0, "t2", makeRolloutMessage("t2", "o1", 1.0d));

        List<Map<String, Object>> filtered = store.queryRollouts(Map.of("task_id", "t1"), 10);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0)).containsEntry("task_id", "t1");
    }

    @Test
    void queryRolloutsHonorsLimit(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        for (int index = 0; index < 5; index++) {
            store.saveRollout(0, "t" + index, makeRolloutMessage("t" + index, "o1", 1.0d), "train");
        }

        assertThat(store.queryRollouts(Map.of(), 3)).hasSize(3);
    }

    @Test
    void queryRolloutsEmptyWhenNothingSaved(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        assertThat(store.queryRollouts(Map.of(), 100)).isEmpty();
    }

    @Test
    void closeIsNoOp(@TempDir Path tmpDir) {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);
        store.close();
    }

    @Test
    void multipleSavesAppendToSameFile(@TempDir Path tmpDir) throws Exception {
        FileRolloutStore store = new FileRolloutStore(tmpDir.toString(), 100);

        for (int index = 0; index < 3; index++) {
            store.saveRollout(10, "t" + index, makeRolloutMessage("t" + index, "o1", 1.0d));
        }

        Path file = tmpDir.resolve("train").resolve("rollouts").resolve("steps_000000_000099.jsonl");
        assertThat(Files.readAllLines(file)).hasSize(3);
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
