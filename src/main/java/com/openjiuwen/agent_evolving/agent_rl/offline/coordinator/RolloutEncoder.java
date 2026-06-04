/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final Logger LOGGER = Logger.getLogger(RolloutEncoder.class.getName());
    private static final Random RANDOM = new Random();

    private Object tokenizer;

    public RolloutEncoder(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    /**
     * Typed tokenizer adapter for Java tests and integrations.
     */
    public interface ChatTokenizer {
        String applyChatTemplate(List<?> messages, boolean tokenize, boolean addGenerationPrompt, List<?> tools);

        List<Integer> encode(String text, boolean addSpecialTokens);
    }

    /**
     * Build rollout training samples in per-turn mode.
     * <p>
     * All turns receive the same global reward to avoid artificial reward variance across
     * turns from the same rollout.
     *
     * @param rolloutMsg Rollout message to encode
     * @return encoded rollouts with rewards
     */
    public List<RolloutWithReward> build(RolloutMessage rolloutMsg) {
        List<?> rolloutInfo = rolloutMsg.getRolloutInfo();
        int totalTurns = rolloutInfo == null ? 0 : rolloutInfo.size();
        boolean shouldLog = RANDOM.nextDouble() < 0.05d;
        String groundTruth = extractGroundTruth(rolloutInfo);
        double globalReward = resolveReward(rolloutMsg);

        if (shouldLog || RANDOM.nextDouble() < 0.1d) {
            logEncodingDiagnostic(rolloutMsg, totalTurns, globalReward);
        }

        List<RolloutWithReward> resultList = new ArrayList<>();
        for (int i = 0; i < totalTurns; i++) {
            try {
                resultList.add(buildSingleTurn(
                        rolloutInfo.get(i),
                        i,
                        globalReward,
                        rolloutMsg.getOriginTaskId(),
                        rolloutMsg.getRolloutId(),
                        shouldLog,
                        totalTurns,
                        groundTruth
                ));
            } catch (Exception e) {
                LOGGER.warning("Error in apply_chat_template: " + e.getMessage());
                LOGGER.warning("The rolloutmsg is " + rolloutMsg);
                return new ArrayList<>();
            }
        }
        return resultList;
    }

    /**
     * Build a single whole-trajectory training sample from all turns.
     *
     * @param rolloutMsg Rollout message to encode
     * @return a one-sample list, per-turn fallback, or an empty list for empty input
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
            return List.of(buildWholeTrajectoryImpl(rolloutMsg));
        } catch (Exception e) {
            LOGGER.warning("Error in build_whole_trajectory: " + e.getMessage());
            LOGGER.warning("Falling back to per-turn build for rollout " + rolloutMsg.getRolloutId());
            return build(rolloutMsg);
        }
    }

    private RolloutWithReward buildSingleTurn(
            Object rollout,
            int turnId,
            double reward,
            String taskId,
            String rolloutId,
            boolean shouldLog,
            int totalTurns,
            String groundTruth) {
        List<Integer> prePromptIds = getInputPromptIds(rollout);
        List<Integer> preResponseIds = getOutputResponseIds(rollout);
        if (prePromptIds != null && preResponseIds != null
                && !prePromptIds.isEmpty() && !preResponseIds.isEmpty()) {
            if (shouldLog) {
                LOGGER.info(String.format(
                        "[turn %d/%d] using precomputed token IDs: prompt_len=%d output_len=%d reward=%.2f",
                        turnId + 1,
                        totalTurns,
                        prePromptIds.size(),
                        preResponseIds.size(),
                        reward
                ));
            }
            return new RolloutWithReward(turnId, taskId, rolloutId, prePromptIds, preResponseIds, reward, totalTurns);
        }

        Object inputPrompt = getInputPrompt(rollout);
        Object outputResponse = getOutputResponse(rollout);
        List<?> inputMessages = getInputMessages(inputPrompt);
        List<?> toolsInfo = getToolsInfo(inputPrompt);
        List<Object> fullMessages = new ArrayList<>(inputMessages);
        fullMessages.add(outputResponse);

        String fullText = applyChatTemplate(fullMessages, false, toolsInfo);
        String promptText = applyChatTemplate(inputMessages, true, toolsInfo);
        String outputText = suffixAfterPrompt(fullText, promptText);

        List<Integer> inputPromptIds = encode(promptText, false);
        List<Integer> outputResponseIds = encode(outputText, false);

        if (shouldLog) {
            LOGGER.info(String.format(
                    "[turn %d/%d] prompt_len=%d output_len=%d reward=%.2f%nground_truth:%n%s%nprompt:%n%s%nresponse:%n%s",
                    turnId + 1,
                    totalTurns,
                    inputPromptIds.size(),
                    outputResponseIds.size(),
                    reward,
                    groundTruth == null || groundTruth.isBlank() ? "(N/A)" : groundTruth,
                    promptText,
                    outputText
            ));
        }

        return new RolloutWithReward(
                turnId,
                taskId,
                rolloutId,
                inputPromptIds,
                outputResponseIds,
                reward,
                totalTurns
        );
    }

    private RolloutWithReward buildWholeTrajectoryImpl(RolloutMessage rolloutMsg) {
        List<?> rolloutInfo = rolloutMsg.getRolloutInfo();
        Object firstTurn = rolloutInfo.get(0);
        Object lastTurn = rolloutInfo.get(rolloutInfo.size() - 1);

        Object firstInputPrompt = getInputPrompt(firstTurn);
        Object lastInputPrompt = getInputPrompt(lastTurn);
        List<?> initialMessages = getInputMessages(firstInputPrompt);
        List<?> lastInputMessages = getInputMessages(lastInputPrompt);
        List<?> toolsInfo = getToolsInfo(firstInputPrompt);
        Object lastOutputResponse = getOutputResponse(lastTurn);

        List<Object> allMessages = new ArrayList<>(lastInputMessages);
        allMessages.add(lastOutputResponse);

        String promptText = applyChatTemplate(initialMessages, true, toolsInfo);
        String fullText = applyChatTemplate(allMessages, false, toolsInfo);
        String responseText = suffixAfterPrompt(fullText, promptText);

        List<Integer> promptIds = encode(promptText, false);
        List<Integer> responseIds = encode(responseText, false);
        int nPrompt = promptIds.size();
        List<Integer> lossMask = new ArrayList<>(responseIds.size());
        for (int i = 0; i < responseIds.size(); i++) {
            lossMask.add(0);
        }

        for (Object rollout : rolloutInfo) {
            Object inputPrompt = getInputPrompt(rollout);
            List<?> messagesBefore = getInputMessages(inputPrompt);
            String textBefore = applyChatTemplate(messagesBefore, true, toolsInfo);
            int nBefore = encode(textBefore, false).size();

            List<Object> messagesAfter = new ArrayList<>(messagesBefore);
            messagesAfter.add(getOutputResponse(rollout));
            String textAfter = applyChatTemplate(messagesAfter, false, toolsInfo);
            int nAfter = encode(textAfter, false).size();

            int start = nBefore - nPrompt;
            int end = nAfter - nPrompt;
            for (int i = Math.max(0, start); i < Math.min(lossMask.size(), end); i++) {
                lossMask.set(i, 1);
            }
        }

        RolloutWithReward result = new RolloutWithReward(
                0,
                rolloutMsg.getOriginTaskId(),
                rolloutMsg.getRolloutId(),
                promptIds,
                responseIds,
                resolveReward(rolloutMsg),
                rolloutInfo.size()
        );
        result.setLossMask(lossMask);
        return result;
    }

    private static String extractGroundTruth(List<?> rolloutInfo) {
        if (rolloutInfo == null || rolloutInfo.isEmpty()) {
            return "";
        }
        Object inputPrompt = getInputPrompt(rolloutInfo.get(0));
        if (inputPrompt instanceof Map<?, ?> map) {
            Object groundTruth = map.get("ground_truth");
            return groundTruth == null ? "" : groundTruth.toString();
        }
        return "";
    }

    private static double resolveReward(RolloutMessage rolloutMsg) {
        Double globalReward = rolloutMsg.getGlobalReward();
        if (globalReward != null) {
            return globalReward;
        }
        List<Double> rewardList = rolloutMsg.getRewardList();
        if (rewardList != null && !rewardList.isEmpty()) {
            return rewardList.get(rewardList.size() - 1);
        }
        return 0.0d;
    }

    private static Object getInputPrompt(Object rollout) {
        if (rollout instanceof Rollout typed) {
            return typed.getInputPrompt();
        }
        if (rollout instanceof Map<?, ?> map) {
            return map.get("input_prompt");
        }
        return readNoArg(rollout, "getInputPrompt");
    }

    private static Object getOutputResponse(Object rollout) {
        if (rollout instanceof Rollout typed) {
            return typed.getOutputResponse();
        }
        if (rollout instanceof Map<?, ?> map) {
            return map.get("output_response");
        }
        return readNoArg(rollout, "getOutputResponse");
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> getInputPromptIds(Object rollout) {
        Object ids;
        if (rollout instanceof Rollout typed) {
            ids = typed.getInputPromptIds();
        } else if (rollout instanceof Map<?, ?> map) {
            ids = map.get("input_prompt_ids");
        } else {
            ids = readNoArg(rollout, "getInputPromptIds");
        }
        return ids instanceof List<?> list ? (List<Integer>) list : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> getOutputResponseIds(Object rollout) {
        Object ids;
        if (rollout instanceof Rollout typed) {
            ids = typed.getOutputResponseIds();
        } else if (rollout instanceof Map<?, ?> map) {
            ids = map.get("output_response_ids");
        } else {
            ids = readNoArg(rollout, "getOutputResponseIds");
        }
        return ids instanceof List<?> list ? (List<Integer>) list : null;
    }

    private static List<?> getInputMessages(Object inputPrompt) {
        if (inputPrompt instanceof Map<?, ?> map) {
            Object messages = map.get("message");
            return messages instanceof List<?> list ? list : List.of();
        }
        return List.of();
    }

    private static List<?> getToolsInfo(Object inputPrompt) {
        if (inputPrompt instanceof Map<?, ?> map) {
            Object tools = map.get("tools");
            return tools instanceof List<?> list ? list : List.of();
        }
        return List.of();
    }

    private String applyChatTemplate(List<?> messages, boolean addGenerationPrompt, List<?> tools) {
        List<?> toolsArg = tools == null || tools.isEmpty() ? null : tools;
        if (tokenizer instanceof ChatTokenizer typedTokenizer) {
            return typedTokenizer.applyChatTemplate(messages, false, addGenerationPrompt, toolsArg);
        }
        Object result = invokeTokenizer(
                "applyChatTemplate",
                new Class<?>[] {List.class, boolean.class, boolean.class, List.class},
                new Object[] {messages, false, addGenerationPrompt, toolsArg}
        );
        if (result == MissingValue.INSTANCE) {
            result = invokeTokenizer(
                    "applyChatTemplate",
                    new Class<?>[] {List.class, boolean.class, boolean.class},
                    new Object[] {messages, false, addGenerationPrompt}
            );
        }
        if (result == MissingValue.INSTANCE) {
            result = invokeTokenizer(
                    "applyChatTemplate",
                    new Class<?>[] {List.class, boolean.class},
                    new Object[] {messages, addGenerationPrompt}
            );
        }
        return result == MissingValue.INSTANCE ? simpleChatTemplate(messages, addGenerationPrompt) : result.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> encode(String text, boolean addSpecialTokens) {
        if (tokenizer instanceof ChatTokenizer typedTokenizer) {
            return typedTokenizer.encode(text, addSpecialTokens);
        }
        Object result = invokeTokenizer(
                "encode",
                new Class<?>[] {String.class, boolean.class},
                new Object[] {text, addSpecialTokens}
        );
        if (result == MissingValue.INSTANCE) {
            result = invokeTokenizer(
                    "encode",
                    new Class<?>[] {String.class},
                    new Object[] {text}
            );
        }
        if (result instanceof List<?> list) {
            return (List<Integer>) list;
        }
        return simpleEncode(text);
    }

    private Object invokeTokenizer(String name, Class<?>[] parameterTypes, Object[] args) {
        if (tokenizer == null) {
            return MissingValue.INSTANCE;
        }
        try {
            Method method = tokenizer.getClass().getMethod(name, parameterTypes);
            return method.invoke(tokenizer, args);
        } catch (NoSuchMethodException e) {
            return MissingValue.INSTANCE;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("tokenizer method failed: " + name, e);
        }
    }

    private static Object readNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String suffixAfterPrompt(String fullText, String promptText) {
        if (fullText == null) {
            return "";
        }
        if (promptText == null || promptText.isEmpty()) {
            return fullText;
        }
        if (fullText.length() >= promptText.length()) {
            return fullText.substring(promptText.length());
        }
        return "";
    }

    private static String simpleChatTemplate(List<?> messages, boolean addGenerationPrompt) {
        StringBuilder sb = new StringBuilder();
        for (Object message : messages) {
            sb.append(message);
        }
        if (addGenerationPrompt) {
            sb.append("<assistant>");
        }
        return sb.toString();
    }

    private static List<Integer> simpleEncode(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>(text.length());
        for (char c : text.toCharArray()) {
            ids.add((int) c);
        }
        return ids;
    }

    private static void logEncodingDiagnostic(RolloutMessage rolloutMsg, int totalTurns, double globalReward) {
        LOGGER.info(String.format(
                "Encoding diagnostic: total_turns=%d, global_reward=%.4f, rollout_id=%s",
                totalTurns,
                globalReward,
                rolloutMsg.getRolloutId()
        ));
    }

    public Object getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    private enum MissingValue {
        INSTANCE
    }
}
