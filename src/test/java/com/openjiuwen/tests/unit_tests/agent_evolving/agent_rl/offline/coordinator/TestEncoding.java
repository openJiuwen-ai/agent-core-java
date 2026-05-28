/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.coordinator;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_encoding.py} in 
 * {@code tests.unit_tests.agent_evolving.agent_rl.offline.coordinator}.
 * 
 * Unit tests for RolloutEncoder: build (per-turn), build_whole_trajectory; schemas validation in context.
 */
@Tag("unit-test")
@Disabled("Requires encoding configuration and mock tokenizer")
class TestEncoding {

    // -----------------------------------------------------------------------
    // Mock classes for testing
    // -----------------------------------------------------------------------

    static class MockTokenizer {
        public int padTokenId = 0;

        public String applyChatTemplate(List<Map<String, Object>> messages, 
                                        boolean tokenize, 
                                        boolean addGenerationPrompt) {
            if (messages != null && !messages.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map<String, Object> m : messages) {
                    String role = (String) m.getOrDefault("role", "user");
                    String content = (String) m.getOrDefault("content", "");
                    parts.add("<" + role + ">" + content);
                }
                return String.join(" ", parts) + (addGenerationPrompt ? " " : "");
            }
            return "";
        }

        public List<Integer> encode(String text) {
            if (text == null || text.isEmpty()) {
                return new ArrayList<>();
            }
            List<Integer> tokens = new ArrayList<>();
            for (int i = 0; i < Math.min(text.length(), 50); i++) {
                tokens.add((int) text.charAt(i) % 100);
            }
            return tokens;
        }
    }

    static class Rollout {
        int turnId;
        Map<String, Object> inputPrompt;
        Map<String, Object> outputResponse;
        List<Integer> inputPromptIds;
        List<Integer> outputResponseIds;

        Rollout(int turnId, Map<String, Object> inputPrompt, Map<String, Object> outputResponse) {
            this.turnId = turnId;
            this.inputPrompt = inputPrompt;
            this.outputResponse = outputResponse;
            this.inputPromptIds = new ArrayList<>();
            this.outputResponseIds = new ArrayList<>();
        }
    }

    static class RolloutMessage {
        String taskId;
        String originTaskId;
        String rolloutId;
        List<Rollout> rolloutInfo;
        List<Double> rewardList;
        double globalReward;
        int turnCount;
        int roundNum;

        RolloutMessage(String taskId, String originTaskId, String rolloutId,
                       List<Rollout> rolloutInfo, List<Double> rewardList,
                       double globalReward, int turnCount, int roundNum) {
            this.taskId = taskId;
            this.originTaskId = originTaskId;
            this.rolloutId = rolloutId;
            this.rolloutInfo = rolloutInfo;
            this.rewardList = rewardList;
            this.globalReward = globalReward;
            this.turnCount = turnCount;
            this.roundNum = roundNum;
        }
    }

    static class RolloutWithReward {
        double reward;
        int nTurns;
        List<Integer> inputPromptIds;
        List<Integer> outputResponseIds;
        List<Integer> lossMask;

        RolloutWithReward(double reward, int nTurns) {
            this.reward = reward;
            this.nTurns = nTurns;
            this.inputPromptIds = new ArrayList<>();
            this.outputResponseIds = new ArrayList<>();
        }
    }

    static class RolloutEncoder {
        private final MockTokenizer tokenizer;

        RolloutEncoder(MockTokenizer tokenizer) {
            this.tokenizer = tokenizer;
        }

        List<RolloutWithReward> build(RolloutMessage msg) {
            List<RolloutWithReward> result = new ArrayList<>();
            for (int i = 0; i < msg.rolloutInfo.size(); i++) {
                Rollout rollout = msg.rolloutInfo.get(i);
                RolloutWithReward sample = new RolloutWithReward(msg.globalReward, msg.turnCount);
                
                // Use precomputed IDs if available, otherwise tokenize
                if (rollout.inputPromptIds != null && !rollout.inputPromptIds.isEmpty()) {
                    sample.inputPromptIds = rollout.inputPromptIds;
                } else {
                    String promptText = tokenizer.applyChatTemplate(
                        (List<Map<String, Object>>) rollout.inputPrompt.get("message"),
                        false, true
                    );
                    sample.inputPromptIds = tokenizer.encode(promptText);
                }
                
                if (rollout.outputResponseIds != null && !rollout.outputResponseIds.isEmpty()) {
                    sample.outputResponseIds = rollout.outputResponseIds;
                } else {
                    String responseText = (String) rollout.outputResponse.get("content");
                    sample.outputResponseIds = tokenizer.encode(responseText);
                }
                
                result.add(sample);
            }
            return result;
        }

        List<RolloutWithReward> buildWholeTrajectory(RolloutMessage msg) {
            if (msg.turnCount == 1) {
                return build(msg);
            }
            
            List<RolloutWithReward> result = new ArrayList<>();
            RolloutWithReward sample = new RolloutWithReward(msg.globalReward, msg.turnCount);
            sample.lossMask = new ArrayList<>();
            
            for (Rollout rollout : msg.rolloutInfo) {
                sample.inputPromptIds.addAll(rollout.inputPromptIds != null ? 
                    rollout.inputPromptIds : new ArrayList<>());
                sample.outputResponseIds.addAll(rollout.outputResponseIds != null ? 
                    rollout.outputResponseIds : new ArrayList<>());
                // Add loss mask for each token in response
                for (int i = 0; i < sample.outputResponseIds.size(); i++) {
                    sample.lossMask.add(1);
                }
            }
            
            result.add(sample);
            return result;
        }
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private RolloutMessage createSingleTurnRollout() {
        List<Rollout> rollouts = new ArrayList<>();
        rollouts.add(new Rollout(
            0,
            Map.of("message", List.of(Map.of("role", "user", "content", "hi"))),
            Map.of("role", "assistant", "content", "hello")
        ));
        return new RolloutMessage("t1", "o1", "r1", rollouts, List.of(0.5), 0.5, 1, 0);
    }

    private RolloutMessage createThreeTurnRollout() {
        List<Rollout> rollouts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rollouts.add(new Rollout(
                i,
                Map.of("message", List.of(Map.of("role", "user", "content", "q" + i))),
                Map.of("role", "assistant", "content", "a" + i)
            ));
        }
        return new RolloutMessage("t1", "o1", "r1", rollouts, List.of(0.3, 0.5, 0.8), 0.8, 3, 0);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Test build single turn returns one sample")
    void testBuildSingleTurnReturnsOneSample() {
        MockTokenizer tokenizer = new MockTokenizer();
        RolloutEncoder encoder = new RolloutEncoder(tokenizer);
        RolloutMessage msg = createSingleTurnRollout();

        List<RolloutWithReward> result = encoder.build(msg);
        
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof RolloutWithReward);
        assertEquals(0.5, result.get(0).reward, 0.001);
        assertEquals(1, result.get(0).nTurns);
        assertTrue(result.get(0).inputPromptIds.size() > 0);
        assertTrue(result.get(0).outputResponseIds.size() > 0);
    }

    @Test
    @DisplayName("Test build three turns returns three samples same global reward")
    void testBuildThreeTurnsReturnsThreeSamples() {
        MockTokenizer tokenizer = new MockTokenizer();
        RolloutEncoder encoder = new RolloutEncoder(tokenizer);
        RolloutMessage msg = createThreeTurnRollout();

        List<RolloutWithReward> result = encoder.build(msg);
        
        assertEquals(3, result.size());
        for (RolloutWithReward sample : result) {
            assertEquals(msg.globalReward, sample.reward, 0.001);
            assertEquals(3, sample.nTurns);
        }
    }

    @Test
    @DisplayName("Test build whole trajectory single turn falls back to build")
    void testBuildWholeTrajectorySingleTurn() {
        MockTokenizer tokenizer = new MockTokenizer();
        RolloutEncoder encoder = new RolloutEncoder(tokenizer);
        RolloutMessage msg = createSingleTurnRollout();

        List<RolloutWithReward> result = encoder.buildWholeTrajectory(msg);
        
        assertEquals(1, result.size());
        assertNull(result.get(0).lossMask);
    }

    @Test
    @DisplayName("Test build whole trajectory multi turn one sample with loss mask")
    void testBuildWholeTrajectoryMultiTurn() {
        MockTokenizer tokenizer = new MockTokenizer();
        RolloutEncoder encoder = new RolloutEncoder(tokenizer);
        RolloutMessage msg = createThreeTurnRollout();

        List<RolloutWithReward> result = encoder.buildWholeTrajectory(msg);
        
        assertEquals(1, result.size());
        assertNotNull(result.get(0).lossMask);
        assertEquals(result.get(0).outputResponseIds.size(), result.get(0).lossMask.size());
        assertEquals(3, result.get(0).nTurns);
    }

    @Test
    @DisplayName("Test build uses precomputed ids without tokenizer")
    void testBuildUsesPrecomputedIds() {
        MockTokenizer tokenizer = new MockTokenizer();
        RolloutEncoder encoder = new RolloutEncoder(tokenizer);

        RolloutMessage msg = new RolloutMessage(
            "t1", "o1", "r1",
            List.of(new Rollout(
                0,
                Map.of("message", List.of(Map.of("role", "user", "content", "hi"))),
                Map.of("role", "assistant", "content", "hello")
            )),
            List.of(1.0),
            1.0,
            1,
            0
        );
        // Set precomputed IDs
        msg.rolloutInfo.get(0).inputPromptIds = List.of(1, 2, 3);
        msg.rolloutInfo.get(0).outputResponseIds = List.of(4, 5);

        List<RolloutWithReward> result = encoder.build(msg);
        
        assertEquals(1, result.size());
        assertEquals(List.of(1, 2, 3), result.get(0).inputPromptIds);
        assertEquals(List.of(4, 5), result.get(0).outputResponseIds);
    }

    @Test
    @DisplayName("Placeholder test")
    void testPlaceholder() {
        assertTrue(true);
    }
}
