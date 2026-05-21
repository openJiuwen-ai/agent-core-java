/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.AgentRlTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RolloutEncoder: build (per-turn), buildWholeTrajectory; schemas validation in context.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_encoding.py}.
 */
class TestEncoding extends AgentRlTestFixture {

    private RolloutEncoder encoder;

    @BeforeEach
    void createEncoder() {
        encoder = new RolloutEncoder(mockTokenizer);
    }

    // -- helper factories ---------------------------------------------------

    private static RolloutMessage rolloutMsgSingleTurn() {
        Rollout rollout = new Rollout();
        rollout.turnId = 0;
        rollout.inputPrompt = new HashMap<>();
        rollout.inputPrompt.put("message", Arrays.<Map<String, Object>>asList(
                message("user", "hi")
        ));
        rollout.outputResponse = message("assistant", "hello");

        RolloutMessage msg = new RolloutMessage();
        msg.taskId = "t1";
        msg.originTaskId = "o1";
        msg.rolloutId = "r1";
        msg.rolloutInfo = Collections.singletonList(rollout);
        msg.rewardList = Collections.singletonList(0.5);
        msg.globalReward = 0.5;
        msg.turnCount = 1;
        msg.roundNum = 0;
        return msg;
    }

    private static RolloutMessage rolloutMsgThreeTurns() {
        List<Rollout> rollouts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Rollout r = new Rollout();
            r.turnId = i;
            r.inputPrompt = new HashMap<>();
            r.inputPrompt.put("message", Arrays.<Map<String, Object>>asList(
                    message("user", "q" + i)
            ));
            r.outputResponse = message("assistant", "a" + i);
            rollouts.add(r);
        }
        RolloutMessage msg = new RolloutMessage();
        msg.taskId = "t1";
        msg.originTaskId = "o1";
        msg.rolloutId = "r1";
        msg.rolloutInfo = rollouts;
        msg.rewardList = Arrays.asList(0.3, 0.5, 0.8);
        msg.globalReward = 0.8;
        msg.turnCount = 3;
        msg.roundNum = 0;
        return msg;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static RolloutMessage emptyRolloutMessage() {
        RolloutMessage msg = new RolloutMessage();
        msg.taskId = "t1";
        msg.originTaskId = "o1";
        msg.rolloutId = "r1";
        msg.rolloutInfo = Collections.emptyList();
        msg.rewardList = Collections.emptyList();
        msg.globalReward = 0.0;
        msg.turnCount = 0;
        msg.roundNum = 0;
        return msg;
    }

    // -- test classes -------------------------------------------------------

    @Nested
    @DisplayName("TestRolloutEncoderBuild")
    class TestRolloutEncoderBuild {

        @Test
        @DisplayName("build single turn returns one sample")
        void testBuildSingleTurnReturnsOneSample() {
            RolloutMessage msg = rolloutMsgSingleTurn();
            List<RolloutWithReward> out = encoder.build(msg);

            assertThat(out).hasSize(1);
            RolloutWithReward sample = out.get(0);
            assertThat(sample.reward).isEqualTo(0.5);
            assertThat(sample.nTurns).isEqualTo(1);
            assertThat(sample.inputPromptIds).isNotEmpty();
            assertThat(sample.outputResponseIds).isNotEmpty();
        }

        @Test
        @DisplayName("build three turns returns three samples with same global reward")
        void testBuildThreeTurnsReturnsThreeSamplesSameGlobalReward() {
            RolloutMessage msg = rolloutMsgThreeTurns();
            List<RolloutWithReward> out = encoder.build(msg);

            assertThat(out).hasSize(3);
            for (RolloutWithReward sample : out) {
                assertThat(sample.reward).isEqualTo(msg.globalReward);
                assertThat(sample.nTurns).isEqualTo(3);
            }
        }
    }

    @Nested
    @DisplayName("TestRolloutEncoderBuildWholeTrajectory")
    class TestRolloutEncoderBuildWholeTrajectory {

        @Test
        @DisplayName("buildWholeTrajectory single turn falls back to build")
        void testBuildWholeTrajectorySingleTurnFallsBackToBuild() {
            RolloutMessage msg = rolloutMsgSingleTurn();
            List<RolloutWithReward> out = encoder.buildWholeTrajectory(msg);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).lossMask).isNull();
        }

        @Test
        @DisplayName("buildWholeTrajectory multi turn produces one sample with loss mask")
        void testBuildWholeTrajectoryMultiTurnOneSampleWithLossMask() {
            RolloutMessage msg = rolloutMsgThreeTurns();
            List<RolloutWithReward> out = encoder.buildWholeTrajectory(msg);

            assertThat(out).hasSize(1);
            RolloutWithReward sample = out.get(0);
            assertThat(sample.lossMask).isNotNull();
            assertThat(sample.lossMask).hasSize(sample.outputResponseIds.size());
            assertThat(sample.nTurns).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("TestRolloutEncoderPrecomputedIds")
    class TestRolloutEncoderPrecomputedIds {

        @Test
        @DisplayName("build uses precomputed IDs without tokenizer")
        void testBuildUsesPrecomputedIdsWithoutTokenizer() {
            List<String> called = new ArrayList<>();

            MockTokenizer failTokenizer = new MockTokenizer() {
                @Override
                public String applyChatTemplate(
                        List<Map<String, Object>> messages,
                        boolean tokenize,
                        boolean addGenerationPrompt,
                        List<Object> tools) {
                    called.add("apply_chat_template");
                    return "";
                }

                @Override
                public List<Integer> encode(String text, boolean addSpecialTokens) {
                    called.add("encode");
                    return Collections.emptyList();
                }
            };

            RolloutEncoder enc = new RolloutEncoder(failTokenizer);

            Rollout rollout = new Rollout();
            rollout.turnId = 0;
            rollout.inputPrompt = new HashMap<>();
            rollout.inputPrompt.put("message", Arrays.<Map<String, Object>>asList(
                    message("user", "hi")
            ));
            rollout.outputResponse = message("assistant", "hello");
            rollout.inputPromptIds = Arrays.asList(1, 2, 3);
            rollout.outputResponseIds = Arrays.asList(4, 5);

            RolloutMessage msg = new RolloutMessage();
            msg.taskId = "t1";
            msg.originTaskId = "o1";
            msg.rolloutId = "r1";
            msg.rolloutInfo = Collections.singletonList(rollout);
            msg.rewardList = Collections.singletonList(1.0);
            msg.globalReward = 1.0;
            msg.turnCount = 1;
            msg.roundNum = 0;

            List<RolloutWithReward> out = enc.build(msg);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).inputPromptIds).containsExactly(1, 2, 3);
            assertThat(out.get(0).outputResponseIds).containsExactly(4, 5);
            assertThat(out.get(0).reward).isEqualTo(1.0);
            assertThat(called).as("tokenizer should not be called when precomputed IDs are present").isEmpty();
        }

        @Test
        @DisplayName("build falls back to tokenizer when IDs missing")
        void testBuildFallsBackToTokenizerWhenIdsMissing() {
            RolloutMessage msg = rolloutMsgSingleTurn();
            List<RolloutWithReward> out = encoder.build(msg);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).inputPromptIds).isNotEmpty();
            assertThat(out.get(0).outputResponseIds).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("TestSchemasInContext")
    class TestSchemasInContext {

        @Test
        @DisplayName("legal rollout message produces non empty build")
        void testLegalRolloutMessageProducesNonEmptyBuild() {
            RolloutMessage msg = rolloutMsgSingleTurn();
            List<RolloutWithReward> out = encoder.build(msg);

            assertThat(out).hasSize(1);
            assertThat(out.get(0).inputPromptIds).isNotEmpty();
            assertThat(out.get(0).outputResponseIds).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("TestRolloutEncoderBoundary")
    class TestRolloutEncoderBoundary {

        @Test
        @DisplayName("build empty rollout info returns empty list")
        void testBuildEmptyRolloutInfoReturnsEmptyList() {
            RolloutMessage msg = emptyRolloutMessage();
            List<RolloutWithReward> out = encoder.build(msg);

            assertThat(out).isEmpty();
        }

        @Test
        @DisplayName("buildWholeTrajectory empty rollout info returns empty list")
        void testBuildWholeTrajectoryEmptyRolloutInfoReturnsEmptyList() {
            RolloutMessage msg = emptyRolloutMessage();
            List<RolloutWithReward> out = encoder.buildWholeTrajectory(msg);

            assertThat(out).isEmpty();
        }

        @Test
        @DisplayName("build uses global reward when reward list shorter than turns")
        void testBuildUsesGlobalRewardWhenRewardListShorterThanTurns() {
            List<Rollout> rollouts = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Rollout r = new Rollout();
                r.turnId = i;
                r.inputPrompt = new HashMap<>();
                r.inputPrompt.put("message", Arrays.<Map<String, Object>>asList(
                        message("user", "q" + i)
                ));
                r.outputResponse = message("assistant", "a" + i);
                rollouts.add(r);
            }

            RolloutMessage msg = new RolloutMessage();
            msg.taskId = "t1";
            msg.originTaskId = "o1";
            msg.rolloutId = "r1";
            msg.rolloutInfo = rollouts;
            msg.rewardList = Collections.singletonList(0.1);
            msg.globalReward = 0.8;
            msg.turnCount = 3;
            msg.roundNum = 0;

            List<RolloutWithReward> out = encoder.build(msg);

            assertThat(out).hasSize(3);
            for (RolloutWithReward sample : out) {
                assertThat(sample.reward).isEqualTo(0.8);
            }
        }
    }

    // -- inner data model classes -------------------------------------------

    static class Rollout {
        Integer turnId;
        Map<String, Object> inputPrompt;
        Map<String, Object> outputResponse;
        List<Integer> inputPromptIds;
        List<Integer> outputResponseIds;
    }

    static class RolloutMessage {
        String taskId;
        String originTaskId;
        String rolloutId;
        List<Rollout> rolloutInfo = new ArrayList<>();
        List<Double> rewardList = new ArrayList<>();
        Double globalReward;
        int turnCount;
        Integer roundNum;
    }

    static class RolloutWithReward {
        Integer turnId;
        String taskId;
        String rolloutId;
        List<Integer> inputPromptIds;
        List<Integer> outputResponseIds;
        Double reward;
        Integer nTurns;
        List<Integer> lossMask;
    }

    static class RolloutEncoder {
        private final MockTokenizer tokenizer;

        RolloutEncoder(MockTokenizer tokenizer) {
            this.tokenizer = tokenizer;
        }

        List<RolloutWithReward> build(RolloutMessage msg) {
            if (msg.rolloutInfo == null || msg.rolloutInfo.isEmpty()) {
                return Collections.emptyList();
            }

            double globalReward = msg.globalReward != null
                    ? msg.globalReward
                    : (msg.rewardList != null && !msg.rewardList.isEmpty()
                    ? msg.rewardList.get(msg.rewardList.size() - 1)
                    : 0.0);

            List<RolloutWithReward> results = new ArrayList<>();
            for (int i = 0; i < msg.rolloutInfo.size(); i++) {
                results.add(buildSingleTurn(
                        msg.rolloutInfo.get(i), i, globalReward,
                        msg.originTaskId, msg.rolloutId,
                        msg.rolloutInfo.size()));
            }
            return results;
        }

        private RolloutWithReward buildSingleTurn(
                Rollout rollout, int turnId, double reward,
                String taskId, String rolloutId, int totalTurns) {
            if (rollout.inputPromptIds != null && rollout.outputResponseIds != null
                    && !rollout.inputPromptIds.isEmpty() && !rollout.outputResponseIds.isEmpty()) {
                RolloutWithReward r = new RolloutWithReward();
                r.turnId = turnId;
                r.taskId = taskId;
                r.rolloutId = rolloutId;
                r.inputPromptIds = rollout.inputPromptIds;
                r.outputResponseIds = rollout.outputResponseIds;
                r.reward = reward;
                r.nTurns = totalTurns;
                return r;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputMessages =
                    (List<Map<String, Object>>) rollout.inputPrompt.get("message");
            List<Map<String, Object>> outputMessages =
                    Collections.singletonList(rollout.outputResponse);
            List<Map<String, Object>> fullMessages = new ArrayList<>(inputMessages);
            fullMessages.addAll(outputMessages);

            @SuppressWarnings("unchecked")
            List<Object> toolsInfo = (List<Object>) rollout.inputPrompt.getOrDefault("tools", null);

            String fullText = tokenizer.applyChatTemplate(
                    fullMessages, false, false, toolsInfo);
            String promptText = tokenizer.applyChatTemplate(
                    inputMessages, false, true, toolsInfo);
            String outputText = fullText.substring(promptText.length());

            List<Integer> inputPromptIds = tokenizer.encode(promptText, false);
            List<Integer> outputResponseIds = tokenizer.encode(outputText, false);

            RolloutWithReward r = new RolloutWithReward();
            r.turnId = turnId;
            r.taskId = taskId;
            r.rolloutId = rolloutId;
            r.inputPromptIds = inputPromptIds;
            r.outputResponseIds = outputResponseIds;
            r.reward = reward;
            r.nTurns = totalTurns;
            return r;
        }

        List<RolloutWithReward> buildWholeTrajectory(RolloutMessage msg) {
            if (msg.rolloutInfo == null || msg.rolloutInfo.isEmpty()) {
                return Collections.emptyList();
            }
            if (msg.rolloutInfo.size() == 1) {
                return build(msg);
            }
            return Collections.singletonList(buildWholeTrajectoryImpl(msg));
        }

        private RolloutWithReward buildWholeTrajectoryImpl(RolloutMessage msg) {
            Rollout lastTurn = msg.rolloutInfo.get(msg.rolloutInfo.size() - 1);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lastInputMessages =
                    (List<Map<String, Object>>) lastTurn.inputPrompt.get("message");
            List<Map<String, Object>> allMessages = new ArrayList<>(lastInputMessages);
            allMessages.add(lastTurn.outputResponse);

            @SuppressWarnings("unchecked")
            List<Object> toolsInfo = (List<Object>) msg.rolloutInfo.get(0).inputPrompt.getOrDefault("tools", null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> initialMessages =
                    (List<Map<String, Object>>) msg.rolloutInfo.get(0).inputPrompt.get("message");

            String promptText = tokenizer.applyChatTemplate(
                    initialMessages, false, true, toolsInfo);
            String fullText = tokenizer.applyChatTemplate(
                    allMessages, false, false, toolsInfo);
            String responseText = fullText.substring(promptText.length());

            List<Integer> promptIds = tokenizer.encode(promptText, false);
            List<Integer> responseIds = tokenizer.encode(responseText, false);
            int nPrompt = promptIds.size();

            List<Integer> lossMask = new ArrayList<>();
            for (int i = 0; i < responseIds.size(); i++) {
                lossMask.add(0);
            }

            for (Rollout rollout : msg.rolloutInfo) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> msgsBefore =
                        (List<Map<String, Object>>) rollout.inputPrompt.get("message");

                String textBefore = tokenizer.applyChatTemplate(
                        msgsBefore, false, true, toolsInfo);
                int nBefore = tokenizer.encode(textBefore, false).size();

                List<Map<String, Object>> msgsAfter = new ArrayList<>(msgsBefore);
                msgsAfter.add(rollout.outputResponse);
                String textAfter = tokenizer.applyChatTemplate(
                        msgsAfter, false, false, toolsInfo);
                int nAfter = tokenizer.encode(textAfter, false).size();

                int start = Math.max(0, nBefore - nPrompt);
                int end = Math.min(lossMask.size(), nAfter - nPrompt);
                for (int i = start; i < end; i++) {
                    lossMask.set(i, 1);
                }
            }

            double reward = msg.globalReward != null
                    ? msg.globalReward
                    : (msg.rewardList != null && !msg.rewardList.isEmpty()
                    ? msg.rewardList.get(msg.rewardList.size() - 1)
                    : 0.0);

            RolloutWithReward r = new RolloutWithReward();
            r.turnId = 0;
            r.taskId = msg.originTaskId;
            r.rolloutId = msg.rolloutId;
            r.inputPromptIds = promptIds;
            r.outputResponseIds = responseIds;
            r.reward = reward;
            r.lossMask = lossMask;
            r.nTurns = msg.rolloutInfo.size();
            return r;
        }
    }
}
