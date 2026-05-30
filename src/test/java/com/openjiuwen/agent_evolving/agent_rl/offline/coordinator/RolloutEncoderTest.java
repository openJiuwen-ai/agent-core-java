/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RolloutEncoderTest {

    @Test
    void buildUsesPrecomputedIdsAndLastRewardWhenGlobalRewardIsMissing() {
        Rollout first = rollout(
                List.of(Map.of("role", "user", "content", "q1")),
                Map.of("role", "assistant", "content", "a1"));
        first.setInputPromptIds(List.of(1, 2, 3));
        first.setOutputResponseIds(List.of(4, 5));
        Rollout second = rollout(
                List.of(Map.of("role", "user", "content", "q2")),
                Map.of("role", "assistant", "content", "a2"));
        second.setInputPromptIds(List.of(6));
        second.setOutputResponseIds(List.of(7, 8, 9));
        RolloutMessage message = message(List.of(first, second), null, List.of(0.2d, 0.7d));

        List<RolloutWithReward> samples = new RolloutEncoder(new TestTokenizer()).build(message);

        assertEquals(2, samples.size());
        assertEquals(0, samples.get(0).getTurnId());
        assertEquals(1, samples.get(1).getTurnId());
        assertEquals(0.7d, samples.get(0).getReward());
        assertEquals(0.7d, samples.get(1).getReward());
        assertEquals(List.of(1, 2, 3), samples.get(0).getInputPromptIds());
        assertEquals(List.of(7, 8, 9), samples.get(1).getOutputResponseIds());
        assertEquals(2, samples.get(0).getNTurns());
    }

    @Test
    void buildTokenizesPromptAndResponseWhenIdsAreMissing() {
        Rollout rollout = rollout(
                List.of(Map.of("role", "user", "content", "hi")),
                Map.of("role", "assistant", "content", "hello"));
        RolloutMessage message = message(List.of(rollout), 0.5d, List.of());

        List<RolloutWithReward> samples = new RolloutEncoder(new TestTokenizer()).build(message);

        assertEquals(1, samples.size());
        assertEquals(chars("<user>hi<assistant>"), samples.get(0).getInputPromptIds());
        assertEquals(chars("hello"), samples.get(0).getOutputResponseIds());
        assertEquals("origin-task", samples.get(0).getTaskId());
        assertEquals("rollout-1", samples.get(0).getRolloutId());
        assertNull(samples.get(0).getLossMask());
    }

    @Test
    void buildWholeTrajectoryComputesLossMaskForAssistantSpansOnly() {
        Rollout first = rollout(
                List.of(Map.of("role", "user", "content", "q1")),
                Map.of("role", "assistant", "content", "a1"));
        Rollout second = rollout(
                List.of(
                        Map.of("role", "user", "content", "q1"),
                        Map.of("role", "assistant", "content", "a1"),
                        Map.of("role", "user", "content", "q2")),
                Map.of("role", "assistant", "content", "a2"));
        RolloutMessage message = message(List.of(first, second), null, List.of(0.3d, 0.9d));

        List<RolloutWithReward> samples = new RolloutEncoder(new TestTokenizer()).buildWholeTrajectory(message);

        assertEquals(1, samples.size());
        RolloutWithReward sample = samples.get(0);
        assertEquals(chars("<user>q1<assistant>"), sample.getInputPromptIds());
        assertEquals(chars("a1<user>q2<assistant>a2"), sample.getOutputResponseIds());
        assertEquals(sample.getOutputResponseIds().size(), sample.getLossMask().size());
        assertEquals(4, sample.getLossMask().stream().mapToInt(Integer::intValue).sum());
        assertEquals(List.of(1, 1), sample.getLossMask().subList(0, 2));
        assertEquals(List.of(1, 1), sample.getLossMask().subList(sample.getLossMask().size() - 2,
                sample.getLossMask().size()));
        assertEquals(0.9d, sample.getReward());
        assertEquals(2, sample.getNTurns());
    }

    @Test
    void buildWholeTrajectorySingleTurnFallsBackToPerTurnBuild() {
        Rollout rollout = rollout(
                List.of(Map.of("role", "user", "content", "hi")),
                Map.of("role", "assistant", "content", "hello"));
        RolloutMessage message = message(List.of(rollout), 1.0d, List.of());

        List<RolloutWithReward> samples = new RolloutEncoder(new TestTokenizer()).buildWholeTrajectory(message);

        assertEquals(1, samples.size());
        assertNull(samples.get(0).getLossMask());
        assertFalse(samples.get(0).getInputPromptIds().isEmpty());
        assertFalse(samples.get(0).getOutputResponseIds().isEmpty());
    }

    private static Rollout rollout(List<Map<String, Object>> messages, Map<String, Object> outputResponse) {
        Rollout rollout = new Rollout();
        rollout.setInputPrompt(Map.of("message", messages));
        rollout.setOutputResponse(outputResponse);
        return rollout;
    }

    private static RolloutMessage message(List<Rollout> rollouts, Double globalReward, List<Double> rewardList) {
        RolloutMessage message = new RolloutMessage();
        message.setOriginTaskId("origin-task");
        message.setRolloutId("rollout-1");
        message.setRolloutInfo(rollouts);
        message.setGlobalReward(globalReward);
        message.setRewardList(rewardList);
        return message;
    }

    private static List<Integer> chars(String text) {
        List<Integer> ids = new ArrayList<>();
        for (char c : text.toCharArray()) {
            ids.add((int) c);
        }
        return ids;
    }

    private static final class TestTokenizer implements RolloutEncoder.ChatTokenizer {
        @Override
        public String applyChatTemplate(List<?> messages, boolean tokenize, boolean addGenerationPrompt, List<?> tools) {
            StringBuilder sb = new StringBuilder();
            for (Object message : messages) {
                Map<?, ?> map = (Map<?, ?>) message;
                sb.append("<").append(map.get("role")).append(">");
                Object content = map.get("content");
                if (content != null) {
                    sb.append(content);
                }
            }
            if (addGenerationPrompt) {
                sb.append("<assistant>");
            }
            return sb.toString();
        }

        @Override
        public List<Integer> encode(String text, boolean addSpecialTokens) {
            return chars(text);
        }
    }
}
