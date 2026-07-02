/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.offline.store.TrainingDiagnostics;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encodes rollout messages into token-level training samples.
 *
 * <p>Mirrors Python's {@code RolloutEncoder} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/encoding.py}.</p>
 */
public class RolloutEncoder {

    private static final Logger LOGGER = Logger.getLogger(RolloutEncoder.class.getName());

    private final Object tokenizer;

    public RolloutEncoder(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    public List<RolloutWithReward> build(RolloutMessage rolloutMessage) {
        List<Rollout> rolloutInfo = safeRollouts(rolloutMessage != null ? rolloutMessage.getRolloutInfo() : null);
        int totalTurns = rolloutInfo.size();
        boolean shouldLog = ThreadLocalRandom.current().nextDouble() < 0.05d;
        String groundTruth = "";
        if (!rolloutInfo.isEmpty()) {
            groundTruth = stringValue(safeMap(rolloutInfo.get(0).getInputPrompt()).get("ground_truth"));
        }

        double globalReward = resolveReward(rolloutMessage);
        if (shouldLog || ThreadLocalRandom.current().nextDouble() < 0.1d) {
            TrainingDiagnostics.diagEncoding(rolloutMessage, totalTurns, globalReward);
        }

        List<RolloutWithReward> result = new ArrayList<>();
        for (int index = 0; index < rolloutInfo.size(); index++) {
            Rollout rollout = rolloutInfo.get(index);
            try {
                result.add(buildSingleTurn(
                        rollout,
                        index,
                        globalReward,
                        rolloutMessage != null ? rolloutMessage.getOriginTaskId() : null,
                        rolloutMessage != null ? rolloutMessage.getRolloutId() : null,
                        shouldLog,
                        totalTurns,
                        groundTruth
                ));
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Error in apply_chat_template: " + exception.getMessage(), exception);
                LOGGER.warning("The rolloutmsg is " + describeRolloutMessage(rolloutMessage));
                return List.of();
            }
        }
        return result;
    }

    public List<RolloutWithReward> buildWholeTrajectory(RolloutMessage rolloutMessage) {
        List<Rollout> rolloutInfo = safeRollouts(rolloutMessage != null ? rolloutMessage.getRolloutInfo() : null);
        if (rolloutInfo.isEmpty()) {
            return List.of();
        }
        if (rolloutInfo.size() == 1) {
            return build(rolloutMessage);
        }

        try {
            return List.of(buildWholeTrajectoryImpl(rolloutMessage));
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Error in build_whole_trajectory: " + exception.getMessage(), exception);
            LOGGER.warning("Falling back to per-turn build for rollout "
                    + (rolloutMessage != null ? rolloutMessage.getRolloutId() : "null"));
            return build(rolloutMessage);
        }
    }

    private RolloutWithReward buildSingleTurn(
            Rollout rollout,
            int turnId,
            double reward,
            String taskId,
            String rolloutId,
            boolean shouldLog,
            int totalTurns,
            String groundTruth
    ) {
        List<Integer> precomputedPromptIds = rollout != null ? rollout.getInputPromptIds() : null;
        List<Integer> precomputedResponseIds = rollout != null ? rollout.getOutputResponseIds() : null;
        if (!isEmpty(precomputedPromptIds) && !isEmpty(precomputedResponseIds)) {
            if (shouldLog) {
                LOGGER.info(String.format(
                        Locale.ROOT,
                        "[turn %d/%d] using precomputed token IDs: prompt_len=%d  output_len=%d  reward=%.2f",
                        turnId + 1,
                        totalTurns,
                        precomputedPromptIds.size(),
                        precomputedResponseIds.size(),
                        reward
                ));
            }
            return createRolloutWithReward(
                    turnId,
                    taskId,
                    rolloutId,
                    precomputedPromptIds,
                    precomputedResponseIds,
                    reward,
                    null,
                    totalTurns
            );
        }

        Map<String, Object> inputPrompt = safeMap(rollout != null ? rollout.getInputPrompt() : null);
        List<Map<String, Object>> inputMessages = toMessageList(inputPrompt.get("message"));
        List<Map<String, Object>> fullMessages = new ArrayList<>(inputMessages);
        fullMessages.add(copyMap(safeMap(rollout != null ? rollout.getOutputResponse() : null)));
        List<Map<String, Object>> toolsInfo = toMessageList(inputPrompt.get("tools"));
        Object toolsArg = toolsInfo.isEmpty() ? null : toolsInfo;

        String fullText = applyChatTemplate(fullMessages, false, false, toolsArg);
        String promptText = applyChatTemplate(inputMessages, false, true, toolsArg);
        String outputText = safeSubstring(fullText, promptText.length());

        List<Integer> inputPromptIds = encode(promptText, false);
        List<Integer> outputResponseIds = encode(outputText, false);

        if (shouldLog) {
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[turn %d/%d] prompt_len=%d  output_len=%d  reward=%.2f%nground_truth:%n%s%nprompt:%n%s%nresponse:%n%s",
                    turnId + 1,
                    totalTurns,
                    inputPromptIds.size(),
                    outputResponseIds.size(),
                    reward,
                    groundTruth == null || groundTruth.isEmpty() ? "(N/A)" : groundTruth,
                    promptText,
                    outputText
            ));
        }

        return createRolloutWithReward(
                turnId,
                taskId,
                rolloutId,
                inputPromptIds,
                outputResponseIds,
                reward,
                null,
                totalTurns
        );
    }

    private RolloutWithReward buildWholeTrajectoryImpl(RolloutMessage rolloutMessage) {
        List<Rollout> rolloutInfo = safeRollouts(rolloutMessage.getRolloutInfo());
        Rollout lastTurn = rolloutInfo.get(rolloutInfo.size() - 1);
        List<Map<String, Object>> allMessages = toMessageList(safeMap(lastTurn.getInputPrompt()).get("message"));
        allMessages.add(copyMap(safeMap(lastTurn.getOutputResponse())));
        List<Map<String, Object>> toolsInfo = toMessageList(
                safeMap(rolloutInfo.get(0).getInputPrompt()).get("tools")
        );
        Object toolsArg = toolsInfo.isEmpty() ? null : toolsInfo;

        List<Map<String, Object>> initialMessages = toMessageList(
                safeMap(rolloutInfo.get(0).getInputPrompt()).get("message")
        );
        String promptText = applyChatTemplate(initialMessages, false, true, toolsArg);
        String fullText = applyChatTemplate(allMessages, false, false, toolsArg);
        String responseText = safeSubstring(fullText, promptText.length());

        List<Integer> promptIds = encode(promptText, false);
        List<Integer> responseIds = encode(responseText, false);
        int promptLength = promptIds.size();

        List<Integer> lossMask = new ArrayList<>();
        for (int index = 0; index < responseIds.size(); index++) {
            lossMask.add(0);
        }

        for (Rollout rollout : rolloutInfo) {
            List<Map<String, Object>> messagesBefore = toMessageList(safeMap(rollout.getInputPrompt()).get("message"));
            String textBefore = applyChatTemplate(messagesBefore, false, true, toolsArg);
            int beforeLength = encode(textBefore, false).size();

            List<Map<String, Object>> messagesAfter = new ArrayList<>(messagesBefore);
            messagesAfter.add(copyMap(safeMap(rollout.getOutputResponse())));
            String textAfter = applyChatTemplate(messagesAfter, false, false, toolsArg);
            int afterLength = encode(textAfter, false).size();

            int start = beforeLength - promptLength;
            int end = afterLength - promptLength;
            for (int index = Math.max(0, start); index < Math.min(lossMask.size(), end); index++) {
                lossMask.set(index, 1);
            }
        }

        double reward = resolveReward(rolloutMessage);
        String groundTruth = stringValue(
                safeMap(safeMap(rolloutInfo.get(0).getInputPrompt())).get("ground_truth")
        );
        boolean shouldLog = ThreadLocalRandom.current().nextDouble() < 0.05d;
        if (shouldLog) {
            int maskSum = lossMask.stream().mapToInt(Integer::intValue).sum();
            LOGGER.info(String.format(
                    Locale.ROOT,
                    "[whole-traj %d turns] prompt_len=%d  resp_len=%d  model_tokens=%d/%d  reward=%.2f%nground_truth:%n%s%nprompt:%n%s%nresponse:%n%s",
                    rolloutInfo.size(),
                    promptIds.size(),
                    responseIds.size(),
                    maskSum,
                    lossMask.size(),
                    reward,
                    groundTruth == null || groundTruth.isEmpty() ? "(N/A)" : groundTruth,
                    promptText,
                    responseText
            ));
        }

        return createRolloutWithReward(
                0,
                rolloutMessage.getOriginTaskId(),
                rolloutMessage.getRolloutId(),
                promptIds,
                responseIds,
                reward,
                lossMask,
                rolloutInfo.size()
        );
    }

    private RolloutWithReward createRolloutWithReward(
            int turnId,
            String taskId,
            String rolloutId,
            List<Integer> promptIds,
            List<Integer> responseIds,
            double reward,
            List<Integer> lossMask,
            int totalTurns
    ) {
        RolloutWithReward result = new RolloutWithReward();
        result.setTurnId(turnId);
        result.setTaskId(taskId);
        result.setRolloutId(rolloutId);
        result.setInputPromptIds(promptIds);
        result.setOutputResponseIds(responseIds);
        result.setReward(reward);
        result.setLossMask(lossMask);
        result.setNTurns(totalTurns);
        return result;
    }

    private String applyChatTemplate(
            List<Map<String, Object>> messages,
            boolean tokenize,
            boolean addGenerationPrompt,
            Object tools
    ) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("tokenize", tokenize);
        kwargs.put("add_generation_prompt", addGenerationPrompt);
        kwargs.put("addGenerationPrompt", addGenerationPrompt);
        kwargs.put("tools", tools);

        Object result = invokeTokenizer("apply_chat_template", messages, kwargs);
        if (result == null) {
            result = invokeTokenizer("applyChatTemplate", messages, kwargs);
        }
        if (result == null) {
            result = invokeTokenizerDirect(
                    "apply_chat_template",
                    new Class<?>[] {Object.class, boolean.class, boolean.class, Object.class},
                    messages,
                    tokenize,
                    addGenerationPrompt,
                    tools
            );
        }
        if (result == null) {
            result = invokeTokenizerDirect(
                    "applyChatTemplate",
                    new Class<?>[] {Object.class, boolean.class, boolean.class, Object.class},
                    messages,
                    tokenize,
                    addGenerationPrompt,
                    tools
            );
        }
        if (result == null) {
            throw new IllegalStateException("tokenizer.apply_chat_template is unavailable");
        }
        return String.valueOf(result);
    }

    private List<Integer> encode(String text, boolean addSpecialTokens) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("add_special_tokens", addSpecialTokens);
        kwargs.put("addSpecialTokens", addSpecialTokens);

        Object result = invokeTokenizer("encode", text, kwargs);
        if (result == null) {
            result = invokeTokenizerDirect(
                    "encode",
                    new Class<?>[] {String.class, boolean.class},
                    text,
                    addSpecialTokens
            );
        }
        if (result == null) {
            throw new IllegalStateException("tokenizer.encode is unavailable");
        }
        return toIntegerList(result);
    }

    private Object invokeTokenizer(String methodName, Object firstArg, Map<String, Object> kwargs) {
        if (tokenizer == null) {
            return null;
        }
        Method[] methods = tokenizer.getClass().getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            try {
                if (parameterTypes.length == 2 && Map.class.isAssignableFrom(parameterTypes[1])) {
                    return method.invoke(tokenizer, firstArg, kwargs);
                }
                if (parameterTypes.length == 1) {
                    return method.invoke(tokenizer, firstArg);
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Try next compatible overload.
            }
        }
        return null;
    }

    private Object invokeTokenizerDirect(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (tokenizer == null) {
            return null;
        }
        try {
            Method method = tokenizer.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(tokenizer, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static List<Rollout> safeRollouts(List<Rollout> rolloutInfo) {
        return rolloutInfo == null ? List.of() : rolloutInfo;
    }

    private static Map<String, Object> safeMap(Map<String, Object> values) {
        return values == null ? Map.of() : values;
    }

    private static List<Map<String, Object>> toMessageList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                result.add(copyMap(asMap(item)));
            }
        }
        return result;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        return Map.of();
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return new LinkedHashMap<>(values);
    }

    private static List<Integer> toIntegerList(Object value) {
        List<Integer> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                result.add(intValue(item));
            }
            return result;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                result.add(intValue(java.lang.reflect.Array.get(value, index)));
            }
        }
        return result;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static double resolveReward(RolloutMessage rolloutMessage) {
        if (rolloutMessage == null) {
            return 0.0d;
        }
        if (rolloutMessage.getGlobalReward() != null) {
            return rolloutMessage.getGlobalReward();
        }
        List<Double> rewardList = rolloutMessage.getRewardList();
        if (rewardList != null && !rewardList.isEmpty()) {
            Double lastReward = rewardList.get(rewardList.size() - 1);
            return lastReward != null ? lastReward : 0.0d;
        }
        return 0.0d;
    }

    private static String safeSubstring(String text, int fromIndex) {
        String safeText = text == null ? "" : text;
        if (fromIndex <= 0) {
            return safeText;
        }
        if (fromIndex >= safeText.length()) {
            return "";
        }
        return safeText.substring(fromIndex);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String describeRolloutMessage(RolloutMessage rolloutMessage) {
        if (rolloutMessage == null) {
            return "null";
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("task_id", rolloutMessage.getTaskId());
        summary.put("origin_task_id", rolloutMessage.getOriginTaskId());
        summary.put("rollout_id", rolloutMessage.getRolloutId());
        summary.put("turn_count", rolloutMessage.getTurnCount());
        summary.put("reward_list", rolloutMessage.getRewardList());
        summary.put("global_reward", rolloutMessage.getGlobalReward());
        summary.put("rollout_info_size", safeRollouts(rolloutMessage.getRolloutInfo()).size());
        return summary.toString();
    }
}
