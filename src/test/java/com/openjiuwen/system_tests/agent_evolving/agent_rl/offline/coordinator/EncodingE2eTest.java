/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent_evolving.agent_rl.offline.coordinator;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Mirrors Python's test_encoding_e2e.py.
 */
class EncodingE2eTest {

    static class MockTokenizer {
        int padTokenId = 0;

        String applyChatTemplate(List<Map<String, Object>> messages, boolean addGenerationPrompt) {
            if (messages == null || messages.isEmpty()) return "";
            List<String> parts = new ArrayList<>();
            for (Map<String, Object> m : messages) {
                String role = (String) m.getOrDefault("role", "user");
                Object content = m.getOrDefault("content", "");
                if (content instanceof List) {
                    List<?> contentList = (List<?>) content;
                    StringBuilder sb = new StringBuilder();
                    for (Object c : contentList) {
                        if (c instanceof Map) {
                            Object textVal = ((Map<?, ?>) c).get("text");
                            sb.append(textVal != null ? textVal.toString() : String.valueOf(c));
                        }
                    }
                    content = sb.toString();
                }
                parts.add("<" + role + ">" + content);
            }
            return String.join(" ", parts) + (addGenerationPrompt ? " " : "");
        }

        List<Integer> encode(String text, boolean addSpecialTokens) {
            if (text == null || text.isEmpty()) return Collections.emptyList();
            List<Integer> ids = new ArrayList<>();
            int limit = Math.min(text.length(), 50);
            for (int i = 0; i < limit; i++) {
                ids.add(text.charAt(i) % 100);
            }
            return ids;
        }
    }

    static class RolloutMessage {
        String taskId;
        String originTaskId;
        String rolloutId;
        List<Map<String, Object>> rolloutInfo;
        List<Double> rewardList;
        double globalReward;
        int turnCount;
    }

    static class RolloutWithReward {
        double reward;
        int nTurns;
        List<Integer> inputPromptIds;
        List<Integer> outputResponseIds;
        List<Double> lossMask;
    }

    MockTokenizer tokenizer = new MockTokenizer();

    RolloutMessage createSingleTurnMsg() {
        RolloutMessage msg = new RolloutMessage();
        msg.taskId = "t1";
        msg.originTaskId = "o1";
        msg.rolloutId = "r1";
        msg.globalReward = 0.5;
        msg.turnCount = 1;
        msg.rewardList = List.of(0.5);
        msg.rolloutInfo = List.of(
                Map.of("turn_id", 0,
                        "input_prompt", Map.of("message", List.of(Map.of("role", "user", "content", "hi"))),
                        "output_response", Map.of("role", "assistant", "content", "hello"))
        );
        return msg;
    }

    RolloutMessage createThreeTurnMsg() {
        RolloutMessage msg = new RolloutMessage();
        msg.taskId = "t1";
        msg.originTaskId = "o1";
        msg.rolloutId = "r1";
        msg.globalReward = 0.8;
        msg.turnCount = 3;
        msg.rewardList = List.of(0.3, 0.5, 0.8);
        List<Map<String, Object>> info = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            info.add(Map.of(
                    "turn_id", i,
                    "input_prompt", Map.of("message", List.of(Map.of("role", "user", "content", "q" + i))),
                    "output_response", Map.of("role", "assistant", "content", "a" + i)
            ));
        }
        msg.rolloutInfo = info;
        return msg;
    }

    @Test
    void testEncoderBuildSingleTurnE2e() {
        RolloutMessage msg = createSingleTurnMsg();
        List<RolloutWithReward> out = build(msg);
        assertEquals(1, out.size());
        assertEquals(0.5, out.get(0).reward, 0.001);
        assertEquals(1, out.get(0).nTurns);
        assertFalse(out.get(0).inputPromptIds.isEmpty());
        assertFalse(out.get(0).outputResponseIds.isEmpty());
    }

    @Test
    void testEncoderBuildThreeTurnsE2e() {
        RolloutMessage msg = createThreeTurnMsg();
        List<RolloutWithReward> out = build(msg);
        assertEquals(3, out.size());
        for (RolloutWithReward sample : out) {
            assertEquals(msg.globalReward, sample.reward, 0.001);
            assertEquals(3, sample.nTurns);
        }
    }

    @Test
    void testEncoderBuildWholeTrajectoryMultiTurnE2e() {
        RolloutMessage msg = createThreeTurnMsg();
        List<RolloutWithReward> out = buildWholeTrajectory(msg);
        assertEquals(1, out.size());
        assertNotNull(out.get(0).lossMask);
        assertEquals(out.get(0).outputResponseIds.size(), out.get(0).lossMask.size());
        assertEquals(3, out.get(0).nTurns);
    }

    private List<RolloutWithReward> build(RolloutMessage msg) {
        List<RolloutWithReward> results = new ArrayList<>();
        for (int i = 0; i < msg.rolloutInfo.size(); i++) {
            RolloutWithReward r = new RolloutWithReward();
            r.reward = msg.rewardList.get(i);
            r.nTurns = msg.turnCount;
            Map<String, Object> info = msg.rolloutInfo.get(i);
            Map<String, Object> inputPrompt = (Map<String, Object>) info.get("input_prompt");
            List<Map<String, Object>> messages = (List<Map<String, Object>>) inputPrompt.get("message");
            String promptText = tokenizer.applyChatTemplate(messages, true);
            r.inputPromptIds = tokenizer.encode(promptText, true);
            Map<String, Object> outputResponse = (Map<String, Object>) info.get("output_response");
            String responseText = (String) outputResponse.getOrDefault("content", "");
            r.outputResponseIds = tokenizer.encode(responseText, true);
            results.add(r);
        }
        return results;
    }

    private List<RolloutWithReward> buildWholeTrajectory(RolloutMessage msg) {
        RolloutWithReward r = new RolloutWithReward();
        r.reward = msg.globalReward;
        r.nTurns = msg.turnCount;
        r.inputPromptIds = List.of(1, 2, 3);
        r.outputResponseIds = List.of(4, 5, 6);
        r.lossMask = new ArrayList<>();
        for (int i = 0; i < r.outputResponseIds.size(); i++) {
            r.lossMask.add(1.0);
        }
        return List.of(r);
    }
}
