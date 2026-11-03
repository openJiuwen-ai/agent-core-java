/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.coordinator;

import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutWithReward;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestRolloutEncoderBuild} and related groups in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_encoding.py}.
 *
 * <p>Mirrors Python's {@code test_encoding_e2e} in
 * {@code tests/system_tests/agent_evolving/agent_rl/offline/coordinator/test_encoding_e2e.py}.</p>
 */
class RolloutEncoderEncodingTest {

    @Nested
    class TestRolloutEncoderBuild {

        @Test
        void testBuildSingleTurnReturnsOneSample() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(1, out.size());
            assertInstanceOf(RolloutWithReward.class, out.get(0));
            assertEquals(0.5d, out.get(0).getReward());
            assertEquals(1, out.get(0).getNTurns());
            assertTrue(!out.get(0).getInputPromptIds().isEmpty() && !out.get(0).getOutputResponseIds().isEmpty());
        }

        @Test
        void testBuildThreeTurnsReturnsThreeSamplesSameGlobalReward() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMsgThreeTurns();

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(3, out.size());
            for (RolloutWithReward sample : out) {
                assertEquals(message.getGlobalReward(), sample.getReward());
                assertEquals(3, sample.getNTurns());
            }
        }
    }

    @Nested
    class TestRolloutEncoderBuildWholeTrajectory {

        @Test
        void testBuildWholeTrajectorySingleTurnFallsBackToBuild() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.buildWholeTrajectory(message);

            assertEquals(1, out.size());
            assertNull(out.get(0).getLossMask());
        }

        @Test
        void testBuildWholeTrajectoryMultiTurnOneSampleWithLossMask() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMsgThreeTurns();

            List<RolloutWithReward> out = encoder.buildWholeTrajectory(message);

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
            FailTokenizer tokenizer = new FailTokenizer();
            RolloutEncoder encoder = new RolloutEncoder(tokenizer);
            Rollout rollout = rollout(0, "hi", "hello");
            rollout.setInputPromptIds(List.of(1, 2, 3));
            rollout.setOutputResponseIds(List.of(4, 5));
            RolloutMessage message = rolloutMessage(List.of(rollout), List.of(1.0d), 1.0d, 1);

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(1, out.size());
            assertEquals(List.of(1, 2, 3), out.get(0).getInputPromptIds());
            assertEquals(List.of(4, 5), out.get(0).getOutputResponseIds());
            assertEquals(1.0d, out.get(0).getReward());
            assertEquals(List.of(), tokenizer.called);
        }

        @Test
        void testBuildFallsBackToTokenizerWhenIdsMissing() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(1, out.size());
            assertTrue(!out.get(0).getInputPromptIds().isEmpty());
            assertTrue(!out.get(0).getOutputResponseIds().isEmpty());
        }
    }

    @Nested
    class TestSchemasInContext {

        @Test
        void testLegalRolloutMessageProducesNonEmptyBuild() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMsgSingleTurn();

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(1, out.size());
            assertTrue(!out.get(0).getInputPromptIds().isEmpty() && !out.get(0).getOutputResponseIds().isEmpty());
        }
    }

    @Nested
    class TestRolloutEncoderBoundary {

        @Test
        void testBuildEmptyRolloutInfoReturnsEmptyList() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMessage(List.of(), List.of(), 0.0d, 0);

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(List.of(), out);
        }

        @Test
        void testBuildWholeTrajectoryEmptyRolloutInfoReturnsEmptyList() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            RolloutMessage message = rolloutMessage(List.of(), List.of(), 0.0d, 0);

            List<RolloutWithReward> out = encoder.buildWholeTrajectory(message);

            assertEquals(List.of(), out);
        }

        @Test
        void testBuildUsesGlobalRewardWhenRewardListShorterThanTurns() {
            RolloutEncoder encoder = new RolloutEncoder(new MockTokenizer());
            List<Rollout> rollouts = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                rollouts.add(rollout(index, "q" + index, "a" + index));
            }
            RolloutMessage message = rolloutMessage(rollouts, List.of(0.1d), 0.8d, 3);

            List<RolloutWithReward> out = encoder.build(message);

            assertEquals(3, out.size());
            for (RolloutWithReward sample : out) {
                assertEquals(0.8d, sample.getReward());
            }
        }
    }

    private static RolloutMessage rolloutMsgSingleTurn() {
        return rolloutMessage(List.of(rollout(0, "hi", "hello")), List.of(0.5d), 0.5d, 1);
    }

    private static RolloutMessage rolloutMsgThreeTurns() {
        List<Rollout> rollouts = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            rollouts.add(rollout(index, "q" + index, "a" + index));
        }
        return rolloutMessage(rollouts, List.of(0.3d, 0.5d, 0.8d), 0.8d, 3);
    }

    private static RolloutMessage rolloutMessage(
            List<Rollout> rollouts,
            List<Double> rewards,
            double globalReward,
            int turnCount
    ) {
        RolloutMessage message = new RolloutMessage();
        message.setTaskId("t1");
        message.setOriginTaskId("o1");
        message.setRolloutId("r1");
        message.setRolloutInfo(rollouts);
        message.setRewardList(rewards);
        message.setGlobalReward(globalReward);
        message.setTurnCount(turnCount);
        message.setRoundNum(0);
        return message;
    }

    private static Rollout rollout(int turnId, String userContent, String assistantContent) {
        Rollout rollout = new Rollout();
        rollout.setTurnId(turnId);
        rollout.setInputPrompt(mapOf("message", List.of(message("user", userContent))));
        rollout.setOutputResponse(message("assistant", assistantContent));
        return rollout;
    }

    private static Map<String, Object> message(String role, String content) {
        return mapOf("role", role, "content", content);
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < kv.length; index += 2) {
            map.put(String.valueOf(kv[index]), kv[index + 1]);
        }
        return map;
    }

    public static final class MockTokenizer {

        public String apply_chat_template(Object messages, Map<String, Object> kwargs) {
            boolean addGenerationPrompt = Boolean.TRUE.equals(kwargs.get("add_generation_prompt"));
            String rendered = renderMessages(messages);
            return addGenerationPrompt ? rendered + "assistant:" : rendered;
        }

        public List<Integer> encode(String text, Map<String, Object> kwargs) {
            List<Integer> ids = new ArrayList<>();
            for (int index = 0; index < text.length(); index++) {
                ids.add((int) text.charAt(index));
            }
            return ids;
        }

        private static String renderMessages(Object messages) {
            StringBuilder builder = new StringBuilder();
            if (messages instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?> message) {
                        builder.append(message.get("role")).append(':').append(message.get("content")).append('\n');
                    }
                }
            }
            return builder.toString();
        }
    }

    public static final class FailTokenizer {
        private final List<String> called = new ArrayList<>();

        public String apply_chat_template(Object messages, Map<String, Object> kwargs) {
            called.add("apply_chat_template");
            return "";
        }

        public List<Integer> encode(String text, Map<String, Object> kwargs) {
            called.add("encode");
            return List.of();
        }
    }
}
