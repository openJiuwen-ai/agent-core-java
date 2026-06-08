/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.rl_trainer;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerlConverterTest {

    @Test
    void convertBatchBuildsMasksScoresAndMetadata() {
        VerlConverter converter = new VerlConverter(new VerlConverter.Options().setPadTokenId(-1));

        VerlConverter.DataProto proto = converter.convertBatch(Map.of(
                "samples",
                List.of(sample("s1", List.of(1, 2), List.of(3), List.of(-0.2), 0.7))
        ));

        int[][] inputIds = (int[][]) proto.batch().get("input_ids");
        int[][] attentionMask = (int[][]) proto.batch().get("attention_mask");
        int[][] positionIds = (int[][]) proto.batch().get("position_ids");
        int[][] responseMask = (int[][]) proto.batch().get("response_mask");
        double[][] oldLogProbs = (double[][]) proto.batch().get("old_log_probs");
        double[][] scores = (double[][]) proto.batch().get("token_level_scores");

        assertArrayEquals(new int[] {1, 2, 3}, inputIds[0]);
        assertArrayEquals(new int[] {1, 1, 1}, attentionMask[0]);
        assertArrayEquals(new int[] {0, 1, 2}, positionIds[0]);
        assertArrayEquals(new int[] {1}, responseMask[0]);
        assertArrayEquals(new double[] {-0.2}, oldLogProbs[0], 1e-9);
        assertArrayEquals(new double[] {0.7}, scores[0], 1e-9);
        assertEquals(List.of("s1"), proto.nonTensors().get("sample_id"));
        assertEquals(1, proto.metaInfo().get("num_samples"));
    }

    @Test
    void convertSamplesSplitsInputIdsAndAppliesDefaults() {
        VerlConverter converter = new VerlConverter(new VerlConverter.Options().setDefaultScore(1.25));

        Map<String, Object> promptOnly = Map.of(
                "sample_id", "byPrompt",
                "trajectory", Map.of(
                        "prompt_ids", List.of(1, 2),
                        "input_ids", List.of(1, 2, 3, 4),
                        "prompt_text", "hello",
                        "response_text", "world"
                )
        );
        Map<String, Object> responseOnly = Map.of(
                "sample_id", "byResponse",
                "trajectory", Map.of(
                        "response_ids", List.of(6, 7),
                        "input_ids", List.of(4, 5, 6, 7),
                        "response_logprobs", List.of(-0.3),
                        "prompt_text", "foo",
                        "response_text", "bar"
                )
        );

        VerlConverter.DataProto proto = converter.convertSamples(List.of(promptOnly, responseOnly));

        int[][] prompts = (int[][]) proto.batch().get("prompts");
        int[][] responses = (int[][]) proto.batch().get("responses");
        double[][] oldLogProbs = (double[][]) proto.batch().get("old_log_probs");
        double[][] scores = (double[][]) proto.batch().get("token_level_scores");

        assertArrayEquals(new int[] {1, 2}, prompts[0]);
        assertArrayEquals(new int[] {3, 4}, responses[0]);
        assertArrayEquals(new int[] {4, 5}, prompts[1]);
        assertArrayEquals(new int[] {6, 7}, responses[1]);
        assertArrayEquals(new double[] {0.0, 0.0}, oldLogProbs[0], 1e-9);
        assertArrayEquals(new double[] {-0.3, 0.0}, oldLogProbs[1], 1e-9);
        assertArrayEquals(new double[] {1.25, 1.25}, scores[0], 1e-9);
        assertArrayEquals(new double[] {1.25, 1.25}, scores[1], 1e-9);
    }

    @Test
    void convertSamplesTruncatesPromptTailAndResponseHead() {
        VerlConverter converter = new VerlConverter(new VerlConverter.Options()
                .setMaxPromptLength(2)
                .setMaxResponseLength(1));

        VerlConverter.DataProto proto = converter.convertSamples(List.of(
                sample("s1", List.of(1, 2, 3), List.of(4, 5), List.of(-0.1, -0.2), 0.5)
        ));

        int[][] prompts = (int[][]) proto.batch().get("prompts");
        int[][] responses = (int[][]) proto.batch().get("responses");
        assertArrayEquals(new int[] {2, 3}, prompts[0]);
        assertArrayEquals(new int[] {4}, responses[0]);
        assertEquals(1, proto.metaInfo().get("prompt_truncated_samples"));
        assertEquals(1, proto.metaInfo().get("response_truncated_samples"));
    }

    @Test
    void convertSamplesDropsOverlongPromptsWhenConfigured() {
        VerlConverter converter = new VerlConverter(new VerlConverter.Options()
                .setMaxPromptLength(1)
                .setTruncation("error")
                .setFilterOverlongPrompts(true));

        VerlConverter.DataProto proto = converter.convertSamples(List.of(
                sample("drop", List.of(1, 2), List.of(3), List.of(), 0.0),
                sample("keep", List.of(9), List.of(10), List.of(), 1.0)
        ));

        assertEquals(List.of("keep"), proto.nonTensors().get("sample_id"));
        assertEquals(1, proto.metaInfo().get("dropped_samples"));
    }

    @Test
    void convertBatchRejectsMissingSamplesAndConvertSamplesRejectsEmptyInputs() {
        VerlConverter converter = new VerlConverter();

        assertThrows(IllegalArgumentException.class, () -> converter.convertBatch(Map.of("samples", List.of())));
        assertThrows(IllegalArgumentException.class, () -> converter.convertSamples(List.of()));
        assertThrows(IllegalArgumentException.class, () -> converter.convertSamples(List.of(Map.of(
                "sample_id", "broken",
                "trajectory", Map.of()
        ))));
    }

    private Map<String, Object> sample(String sampleId,
                                       List<Integer> promptIds,
                                       List<Integer> responseIds,
                                       List<Double> logProbs,
                                       double score) {
        return Map.of(
                "sample_id", sampleId,
                "session_id", "sess",
                "turn_num", 2,
                "created_at", "2026-01-01T00:00:00+00:00",
                "mode", "online",
                "io_mode", "chat",
                "model", "m",
                "trajectory", Map.of(
                        "prompt_ids", promptIds,
                        "response_ids", responseIds,
                        "response_logprobs", logProbs,
                        "prompt_text", "hello",
                        "response_text", "world"
                ),
                "judge", Map.of("score", score)
        );
    }
}
