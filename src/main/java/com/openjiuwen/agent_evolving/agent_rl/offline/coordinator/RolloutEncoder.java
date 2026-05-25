/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Encodes RolloutMessage objects into RolloutWithReward training samples.
 * <p>
 * Supports two modes:
 * - per-turn: each dialogue turn becomes a separate training sample
 * - whole-trajectory: the entire multi-turn conversation is one sample
 *   with a loss_mask marking model-generated vs environment tokens
 * <p>
 * Mirrors Python's {@code RolloutEncoder} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.encoding}.
 */
public class RolloutEncoder {

    private static final Logger logger = Logger.getLogger(RolloutEncoder.class.getName());
    private static final Random random = new Random();
    
    private Object tokenizer; // Placeholder for tokenizer interface

    public RolloutEncoder(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    /**
     * Build rollout training samples in per-turn mode.
     * <p>
     * All turns receive the same global_reward to ensure GRPO sees
     * proper reward variance across rollouts rather than artificial
     * variance across turns within the same rollout.
     *
     * @param rolloutMsg Rollout message to encode
     * @return List of encoded rollouts with rewards
     */
    public List<RolloutWithReward> build(RolloutMessage rolloutMsg) {
        List<?> rolloutInfo = rolloutMsg.getRolloutInfo();
        int totalTurns = rolloutInfo.size();
        boolean shouldLog = random.nextDouble() < 0.05;
        
        String groundTruth = "";
        if (!rolloutInfo.isEmpty()) {
            Object firstRollout = rolloutInfo.get(0);
            Object inputPrompt = getInputPrompt(firstRollout);
            if (inputPrompt instanceof java.util.Map) {
                Object gt = ((java.util.Map<?, ?>) inputPrompt).get("ground_truth");
                groundTruth = gt != null ? gt.toString() : "";
            }
        }

        Double globalReward = rolloutMsg.getGlobalReward();
        if (globalReward == null) {
            List<Double> rewardList = rolloutMsg.getRewardList();
            if (rewardList != null && !rewardList.isEmpty()) {
                globalReward = rewardList.get(rewardList.size() - 1);
            } else {
                globalReward = 0.0;
            }
        }

        // Log diagnostics occasionally
        if (shouldLog || random.nextDouble() < 0.1) {
            logEncodingDiagnostic(rolloutMsg, totalTurns, globalReward);
        }

        List<RolloutWithReward> resultList = new ArrayList<>();
        for (int i = 0; i < rolloutInfo.size(); i++) {
            try {
                resultList.add(
                    buildSingleTurn(
                        rolloutInfo.get(i),
                        i,
                        globalReward,
                        rolloutMsg.getOriginTaskId(),
                        rolloutMsg.getRolloutId(),
                        shouldLog,
                        totalTurns,
                        groundTruth
                    )
                );
            } catch (Exception e) {
                logger.warning("Error in apply_chat_template: " + e.getMessage());
                logger.warning("The rolloutmsg is " + rolloutMsg.toString());
                return new ArrayList<>();
            }
        }
        return resultList;
    }

    /**
     * Build a single input-output rollout sample.
     */
    private RolloutWithReward buildSingleTurn(
            Object rollout,
            int turnId,
            Double reward,
            String taskId,
            String rolloutId,
            boolean shouldLog,
            int totalTurns,
            String groundTruth
    ) {
        // Check for precomputed token IDs
        List<Integer> prePromptIds = getInputPromptIds(rollout);
        List<Integer> preResponseIds = getOutputResponseIds(rollout);
        
        if (prePromptIds != null && preResponseIds != null && 
            !prePromptIds.isEmpty() && !preResponseIds.isEmpty()) {
            if (shouldLog) {
                logger.info(String.format(
                    "[turn %d/%d] using precomputed token IDs: prompt_len=%d  output_len=%d  reward=%.2f",
                    turnId + 1, totalTurns,
                    prePromptIds.size(), preResponseIds.size(),
                    reward != null ? reward : 0.0
                ));
            }
            return new RolloutWithReward(
                turnId, taskId, rolloutId,
                prePromptIds, preResponseIds,
                reward != null ? reward : 0.0, totalTurns
            );
        }
        
        // Fallback to tokenizer-based encoding
        Object inputPrompt = getInputPrompt(rollout);
        Object outputResponse = getOutputResponse(rollout);
        
        List<?> inputMessages = getInputMessages(inputPrompt);
        List<Object> outputMessages = new ArrayList<>();
        outputMessages.add(outputResponse);
        
        List<Object> fullMessages = new ArrayList<>();
        fullMessages.addAll(inputMessages);
        fullMessages.addAll(outputMessages);
        
        List<?> toolsInfo = getToolsInfo(inputPrompt);

        // Apply chat template (requires tokenizer implementation)
        String fullText = applyChatTemplate(fullMessages, false, toolsInfo);
        String promptText = applyChatTemplate(inputMessages, true, toolsInfo);
        String outputText = fullText.substring(promptText.length());

        List<Integer> inputPromptIds = encode(promptText, false);
        List<Integer> outputResponseIds = encode(outputText, false);

        if (shouldLog) {
            logger.info(String.format(
                "[turn %d/%d] prompt_len=%d  output_len=%d  reward=%.2f\n" +
                "ground_truth:\n%s\nprompt:\n%s\nresponse:\n%s",
                turnId + 1, totalTurns,
                inputPromptIds.size(), outputResponseIds.size(),
                reward != null ? reward : 0.0,
                groundTruth != null && !groundTruth.isEmpty() ? groundTruth : "(N/A)",
                promptText,
                outputText
            ));
        }

        return new RolloutWithReward(
            turnId, taskId, rolloutId,
            inputPromptIds, outputResponseIds,
            reward != null ? reward : 0.0, totalTurns
        );
    }

    /**
     * Build a single whole-trajectory training sample from all turns.
     * <p>
     * Concatenates the full multi-turn conversation into one sample:
     * - prompt  = initial [system, user]
     * - response = the rest (assistant + tool_response + assistant + ...)
     * - loss_mask: 1 on model-generated tokens, 0 on environment tokens
     *
     * @param rolloutMsg Rollout message to encode
     * @return List containing single whole-trajectory sample, or per-turn fallback
     */
    public List<RolloutWithReward> buildWholeTrajectory(RolloutMessage rolloutMsg) {
        List<?> rolloutInfo = rolloutMsg.getRolloutInfo();
        
        if (rolloutInfo == null || rolloutInfo.isEmpty()) {
            return new ArrayList<>();
        }

        if (rolloutInfo.size() == 1) {
            return build(rolloutMsg);
        }

        try {
            List<RolloutWithReward> result = new ArrayList<>();
            result.add(buildWholeTrajectoryImpl(rolloutMsg));
            return result;
        } catch (Exception e) {
            logger.warning("Error in build_whole_trajectory: " + e.getMessage());
            logger.warning("Falling back to per-turn build for rollout " + rolloutMsg.getRolloutId());
            return build(rolloutMsg);
        }
    }

    /**
     * Internal implementation of whole-trajectory sample construction.
     */
    private RolloutWithReward buildWholeTrajectoryImpl(RolloutMessage rolloutMsg) {
        List<?> rolloutInfo = rolloutMsg.getRolloutInfo();
        Object lastTurn = rolloutInfo.get(rolloutInfo.size() - 1);
        
        Object firstInputPrompt = getInputPrompt(rolloutInfo.get(0));
        List<?> initialMessages = getInputMessages(firstInputPrompt);
        List<?> toolsInfo = getToolsInfo(firstInputPrompt);

        // Get all messages from last turn
        Object lastInputPrompt = getInputPrompt(lastTurn);
        List<?> lastInputMessages = getInputMessages(lastInputPrompt);
        Object lastOutputResponse = getOutputResponse(lastTurn);
        
        List<Object> allMessages = new ArrayList<>();
        allMessages.addAll(lastInputMessages);
        allMessages.add(lastOutputResponse);

        // Apply chat template
        String promptText = applyChatTemplate(initialMessages, true, toolsInfo);
        String fullText = applyChatTemplate(allMessages, false, toolsInfo);
        String outputText = fullText.substring(promptText.length());

        List<Integer> inputPromptIds = encode(promptText, false);
        List<Integer> outputResponseIds = encode(outputText, false);

        Double globalReward = rolloutMsg.getGlobalReward();
        if (globalReward == null) {
            globalReward = 0.0;
        }

        return new RolloutWithReward(
            0, // turn_id for whole trajectory
            rolloutMsg.getOriginTaskId(),
            rolloutMsg.getRolloutId(),
            inputPromptIds,
            outputResponseIds,
            globalReward,
            rolloutInfo.size()
        );
    }

    // -- Helper methods for accessing rollout data --

    private Object getInputPrompt(Object rollout) {
        // Access rollout.input_prompt field
        if (rollout instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) rollout).get("input_prompt");
        }
        // Add reflection-based access for POJO
        return null;
    }

    private Object getOutputResponse(Object rollout) {
        if (rollout instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) rollout).get("output_response");
        }
        return null;
    }

    private List<Integer> getInputPromptIds(Object rollout) {
        if (rollout instanceof java.util.Map) {
            Object ids = ((java.util.Map<?, ?>) rollout).get("input_prompt_ids");
            if (ids instanceof List) {
                return (List<Integer>) ids;
            }
        }
        return null;
    }

    private List<Integer> getOutputResponseIds(Object rollout) {
        if (rollout instanceof java.util.Map) {
            Object ids = ((java.util.Map<?, ?>) rollout).get("output_response_ids");
            if (ids instanceof List) {
                return (List<Integer>) ids;
            }
        }
        return null;
    }

    private List<?> getInputMessages(Object inputPrompt) {
        if (inputPrompt instanceof java.util.Map) {
            Object messages = ((java.util.Map<?, ?>) inputPrompt).get("message");
            if (messages instanceof List) {
                return (List<?>) messages;
            }
        }
        return new ArrayList<>();
    }

    private List<?> getToolsInfo(Object inputPrompt) {
        if (inputPrompt instanceof java.util.Map) {
            Object tools = ((java.util.Map<?, ?>) inputPrompt).get("tools");
            if (tools instanceof List) {
                return (List<?>) tools;
            }
        }
        return new ArrayList<>();
    }

    // -- Tokenizer interface methods (placeholder implementation) --

    private String applyChatTemplate(List<?> messages, boolean addGenerationPrompt, List<?> tools) {
        // Requires actual tokenizer implementation
        // This is a placeholder that returns concatenated messages
        StringBuilder sb = new StringBuilder();
        for (Object msg : messages) {
            sb.append(msg.toString());
        }
        if (addGenerationPrompt) {
            sb.append("<|assistant|>");
        }
        return sb.toString();
    }

    private List<Integer> encode(String text, boolean addSpecialTokens) {
        // Requires actual tokenizer implementation
        // Placeholder: simple character-to-integer conversion
        List<Integer> ids = new ArrayList<>();
        for (char c : text.toCharArray()) {
            ids.add((int) c);
        }
        return ids;
    }

    private void logEncodingDiagnostic(RolloutMessage rolloutMsg, int totalTurns, Double globalReward) {
        logger.info(String.format(
            "Encoding diagnostic: total_turns=%d, global_reward=%.4f, rollout_id=%s",
            totalTurns, globalReward != null ? globalReward : 0.0, rolloutMsg.getRolloutId()
        ));
    }

    public Object getTokenizer() { return tokenizer; }
    public void setTokenizer(Object tokenizer) { this.tokenizer = tokenizer; }
}