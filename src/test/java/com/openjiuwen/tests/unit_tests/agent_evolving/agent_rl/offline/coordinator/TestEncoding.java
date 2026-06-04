/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.coordinator.RolloutEncoder;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for RolloutEncoder: build (per-turn), build_whole_trajectory;
 * schemas validation in context.
 * <p>
 * Mirrors Python's {@code test_encoding.py} in
 * {@code tests.unit_tests.agent_evolving.agent_rl.offline.coordinator}.
 */
@Tag("unit-test")
class TestEncoding {

    private RolloutEncoder encoder;

    @BeforeEach
    void createEncoder() {
        encoder = new RolloutEncoder(new MockTokenizer());
    }

    @Nested
    class TestRolloutEncoderBuild {

        @Test
        void testBuildSingleTurnReturnsOneSample() {
            RolloutMessage msg = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.build(msg);

            assertEquals(1, out.size());
            assertInstanceOf(RolloutWithReward.class, out.get(0));
            assertEquals(0.5, out.get(0).getReward(), 0.001);
            assertEquals(1, out.get(0).getNTurns());
            assertFalse(out.get(0).getInputPromptIds().isEmpty());
            assertFalse(out.get(0).getOutputResponseIds().isEmpty());
        }

        @Test
        void testBuildThreeTurnsReturnsThreeSamplesSameGlobalReward() {
            RolloutMessage msg = rolloutMsgThreeTurns();

            List<RolloutWithReward> out = encoder.build(msg);

            assertEquals(3, out.size());
            for (RolloutWithReward sample : out) {
                assertEquals(msg.getGlobalReward(), sample.getReward(), 0.001);
                assertEquals(3, sample.getNTurns());
            }
        }
    }

    @Nested
    class TestRolloutEncoderBuildWholeTrajectory {

        @Test
        void testBuildWholeTrajectorySingleTurnFallsBackToBuild() {
            RolloutMessage msg = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.buildWholeTrajectory(msg);

            assertEquals(1, out.size());
            assertNull(out.get(0).getLossMask());
        }

        @Test
        void testBuildWholeTrajectoryMultiTurnOneSampleWithLossMask() {
            RolloutMessage msg = rolloutMsgThreeTurns();

            List<RolloutWithReward> out = encoder.buildWholeTrajectory(msg);

            assertEquals(1, out.size());
            assertNotNull(out.get(0).getLossMask());
            assertEquals(out.get(0).getOutputResponseIds().size(), out.get(0).getLossMask().size());
            assertEquals(3, out.get(0).getNTurns());
        }
    }

    @Nested
    class TestRolloutEncoderPrecomputedIds {

        @Test
        void testBuildUsesPrecomputedIdsWithoutTokenizer() {
            List<String> called = new ArrayList<>();
            RolloutEncoder enc = new RolloutEncoder(new FailTokenizer(called));
            Rollout rollout = rollout(0, "hi", "hello");
            rollout.setInputPromptIds(List.of(1, 2, 3));
            rollout.setOutputResponseIds(List.of(4, 5));
            RolloutMessage msg = rolloutMessage(
                    "t1", "o1", "r1", List.of(rollout), List.of(1.0), 1.0, 1, 0);

            List<RolloutWithReward> out = enc.build(msg);

            assertEquals(1, out.size());
            assertEquals(List.of(1, 2, 3), out.get(0).getInputPromptIds());
            assertEquals(List.of(4, 5), out.get(0).getOutputResponseIds());
            assertEquals(1.0, out.get(0).getReward(), 0.001);
            assertTrue(called.isEmpty(), "tokenizer should not be called when precomputed IDs are present");
        }

        @Test
        void testBuildFallsBackToTokenizerWhenIdsMissing() {
            RolloutMessage msg = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.build(msg);

            assertEquals(1, out.size());
            assertFalse(out.get(0).getInputPromptIds().isEmpty());
            assertFalse(out.get(0).getOutputResponseIds().isEmpty());
        }
    }

    @Nested
    class TestSchemasInContext {

        @Test
        void testLegalRolloutMessageProducesNonEmptyBuild() {
            List<RolloutWithReward> out = encoder.build(rolloutMsgSingleTurn());

            assertEquals(1, out.size());
            assertFalse(out.get(0).getInputPromptIds().isEmpty());
            assertFalse(out.get(0).getOutputResponseIds().isEmpty());
        }
    }

    @Nested
    class TestRolloutEncoderBoundary {

        @Test
        void testBuildEmptyRolloutInfoReturnsEmptyList() {
            RolloutMessage msg = rolloutMessage("t1", "o1", "r1", List.of(), List.of(), 0.0, 0, 0);

            assertEquals(List.of(), encoder.build(msg));
        }

        @Test
        void testBuildWholeTrajectoryEmptyRolloutInfoReturnsEmptyList() {
            RolloutMessage msg = rolloutMessage("t1", "o1", "r1", List.of(), List.of(), 0.0, 0, 0);

            assertEquals(List.of(), encoder.buildWholeTrajectory(msg));
        }

        @Test
        void testBuildUsesGlobalRewardWhenRewardListShorterThanTurns() {
            List<Rollout> rollouts = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                rollouts.add(rollout(i, "q" + i, "a" + i));
            }
            RolloutMessage msg = rolloutMessage("t1", "o1", "r1", rollouts, List.of(0.1), 0.8, 3, 0);

            List<RolloutWithReward> out = encoder.build(msg);

            assertEquals(3, out.size());
            for (RolloutWithReward sample : out) {
                assertEquals(0.8, sample.getReward(), 0.001);
            }
        }
    }

    private static RolloutMessage rolloutMsgSingleTurn() {
        return rolloutMessage(
                "t1",
                "o1",
                "r1",
                List.of(rollout(0, "hi", "hello")),
                List.of(0.5),
                0.5,
                1,
                0
        );
    }

    private static RolloutMessage rolloutMsgThreeTurns() {
        List<Rollout> rollouts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rollouts.add(rollout(i, "q" + i, "a" + i));
        }
        return rolloutMessage("t1", "o1", "r1", rollouts, List.of(0.3, 0.5, 0.8), 0.8, 3, 0);
    }

    private static Rollout rollout(int turnId, String userContent, String assistantContent) {
        Rollout rollout = new Rollout();
        rollout.setTurnId(turnId);
        rollout.setInputPrompt(Map.of("message", List.of(Map.of("role", "user", "content", userContent))));
        rollout.setOutputResponse(Map.of("role", "assistant", "content", assistantContent));
        return rollout;
    }

    private static RolloutMessage rolloutMessage(
            String taskId,
            String originTaskId,
            String rolloutId,
            List<Rollout> rolloutInfo,
            List<Double> rewardList,
            double globalReward,
            int turnCount,
            int roundNum) {
        RolloutMessage msg = new RolloutMessage();
        msg.setTaskId(taskId);
        msg.setOriginTaskId(originTaskId);
        msg.setRolloutId(rolloutId);
        msg.setRolloutInfo(rolloutInfo);
        msg.setRewardList(rewardList);
        msg.setGlobalReward(globalReward);
        msg.setTurnCount(turnCount);
        msg.setRoundNum(roundNum);
        return msg;
    }

    static class MockTokenizer implements RolloutEncoder.ChatTokenizer {
        int padTokenId = 0;

        @Override
        public String applyChatTemplate(List<?> messages, boolean tokenize, boolean addGenerationPrompt, List<?> tools) {
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            for (Object item : messages) {
                if (item instanceof Map<?, ?> message) {
                    String role = String.valueOf(message.containsKey("role") ? message.get("role") : "user");
                    Object contentObj = message.containsKey("content") ? message.get("content") : "";
                    String content;
                    if (contentObj instanceof List<?> contentItems) {
                        List<String> textParts = new ArrayList<>();
                        for (Object contentItem : contentItems) {
                            if (contentItem instanceof Map<?, ?> contentMap) {
                                Object text = contentMap.get("text");
                                textParts.add(text != null ? String.valueOf(text) : String.valueOf(contentMap));
                            }
                        }
                        content = String.join(" ", textParts);
                    } else {
                        content = contentObj != null ? String.valueOf(contentObj) : "";
                    }
                    parts.add("<" + role + ">" + content);
                } else {
                    parts.add(String.valueOf(item));
                }
            }
            return String.join(" ", parts) + (addGenerationPrompt ? " " : "");
        }

        @Override
        public List<Integer> encode(String text, boolean addSpecialTokens) {
            if (text == null || text.isEmpty()) {
                return new ArrayList<>();
            }
            List<Integer> tokens = new ArrayList<>();
            int limit = Math.min(text.length(), 50);
            for (int i = 0; i < limit; i++) {
                tokens.add((int) text.charAt(i) % 100);
            }
            return tokens;
        }
    }

    static final class FailTokenizer extends MockTokenizer {
        private final List<String> called;

        FailTokenizer(List<String> called) {
            this.called = called;
        }

        @Override
        public String applyChatTemplate(List<?> messages, boolean tokenize, boolean addGenerationPrompt, List<?> tools) {
            called.add("apply_chat_template");
            return "";
        }

        @Override
        public List<Integer> encode(String text, boolean addSpecialTokens) {
            called.add("encode");
            return List.of();
        }
    }
}
