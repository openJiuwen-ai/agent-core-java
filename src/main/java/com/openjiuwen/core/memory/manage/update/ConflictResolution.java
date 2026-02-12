/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.memory.manage.memmodel.ConflictType;
import com.openjiuwen.core.memory.prompt.ConflictResolutionPrompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Class for handling conflict resolution between old and new memory messages.
 * <p>
 * Corresponds to Python: manage/update/conflict_resolution.py
 */
public class ConflictResolution {

    private static final LoggerProtocol logger = Loggers.MEMORY;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ConflictResolution() {
        // Utility class, no instantiation
    }

    /**
     * Check for conflicts between old messages and a new message.
     *
     * @param oldMessages    List of old messages.
     * @param newMessage     The new message to check against old messages.
     * @param baseChatModel  The chat model to use for processing (Pair of modelName and Model).
     * @return A CompletableFuture containing a list of maps representing the conflict resolution results.
     */
    public static CompletableFuture<List<Map<String, Object>>> checkConflict(
            List<String> oldMessages,
            String newMessage,
            Pair<String, Model> baseChatModel) {
        return checkConflict(oldMessages, newMessage, baseChatModel, 3);
    }

    /**
     * Check for conflicts between old messages and a new message.
     *
     * @param oldMessages    List of old messages.
     * @param newMessage     The new message to check against old messages.
     * @param baseChatModel  The chat model to use for processing (Pair of modelName and Model).
     * @param retries        Number of retries for the operation.
     * @return A CompletableFuture containing a list of maps representing the conflict resolution results.
     */
    public static CompletableFuture<List<Map<String, Object>>> checkConflict(
            List<String> oldMessages,
            String newMessage,
            Pair<String, Model> baseChatModel,
            int retries) {

        // If no old messages or no chat model, return ADD operation
        if (oldMessages == null || oldMessages.isEmpty() || baseChatModel == null) {
            logger.debug("No need to check conflict, msg len {}, ADD new message.",
                    oldMessages == null ? 0 : oldMessages.size());
            return CompletableFuture.completedFuture(List.of(
                    createResult("0", newMessage, ConflictType.ADD.getValue())
            ));
        }

        // If new message already exists in old messages, return NONE operation
        if (oldMessages.contains(newMessage)) {
            logger.debug("New message {} found in old messages {}", newMessage, oldMessages);
            return CompletableFuture.completedFuture(List.of(
                    createResult("0", newMessage, ConflictType.NONE.getValue())
            ));
        }

        Model modelClient = baseChatModel.getValue();
        String modelName = baseChatModel.getKey();
        List<Map<String, Object>> messages = getMessage(oldMessages, newMessage);

        logger.debug("Start checking conflict, input messages: {}", messages);

        return invokeWithRetry(modelClient, modelName, messages, retries);
    }

    private static CompletableFuture<List<Map<String, Object>>> invokeWithRetry(
            Model modelClient,
            String modelName,
            List<Map<String, Object>> messages,
            int retriesLeft) {

        if (retriesLeft <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }

        return modelClient.invoke(messages)
                .thenCompose(response -> {
                    try {
                        String content = response.getContent() != null ?
                                response.getContent().toString().trim().replace("'", "\"") : "";

                        Map<String, Object> result = objectMapper.readValue(content,
                                new TypeReference<Map<String, Object>>() {});

                        if (result == null) {
                            logger.debug("LLM returned non-dict result, retrying...");
                            return invokeWithRetry(modelClient, modelName, messages, retriesLeft - 1);
                        }

                        List<Map<String, Object>> output = new ArrayList<>();

                        if (result.containsKey("new_message")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> newMsg = (Map<String, Object>) result.get("new_message");
                            output.add(newMsg);
                        }

                        if (result.containsKey("old_messages") && result.get("old_messages") instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> oldMsgs = (List<Map<String, Object>>) result.get("old_messages");
                            output.addAll(oldMsgs);
                        }

                        if (!output.isEmpty()) {
                            logger.debug("Succeed to check conflict, result: {}", output);
                            return CompletableFuture.completedFuture(output);
                        }

                        return invokeWithRetry(modelClient, modelName, messages, retriesLeft - 1);

                    } catch (JsonProcessingException e) {
                        logger.debug("JSON decode error: {}, retrying...", e.getMessage());
                        return invokeWithRetry(modelClient, modelName, messages, retriesLeft - 1);
                    }
                })
                .exceptionally(e -> {
                    logger.error("Error during conflict check: {}", e.getMessage());
                    if (retriesLeft > 1) {
                        return invokeWithRetry(modelClient, modelName, messages, retriesLeft - 1).join();
                    }
                    return List.of();
                });
    }

    private static List<Map<String, Object>> getMessage(List<String> oldMessages, String newMessage) {
        Map<String, Object> newMsgInput = new HashMap<>();
        newMsgInput.put("id", "0");
        newMsgInput.put("text", newMessage);
        newMsgInput.put("event", "operation");

        List<Map<String, Object>> oldMsgInput = new ArrayList<>();
        int index = 1;
        for (String oldMessage : oldMessages) {
            Map<String, Object> oldMsg = new HashMap<>();
            oldMsg.put("id", String.valueOf(index));
            oldMsg.put("text", oldMessage);
            oldMsg.put("event", "operation");
            oldMsgInput.add(oldMsg);
            index++;
        }

        Map<String, Object> userInput = new HashMap<>();
        userInput.put("new_message", newMsgInput);
        userInput.put("old_messages", oldMsgInput);

        String userInputJson;
        try {
            userInputJson = objectMapper.writeValueAsString(userInput);
        } catch (JsonProcessingException e) {
            userInputJson = userInput.toString();
        }

        String userMessageContent = String.format(
                "现在开始：请根据设定的规则处理以下输入并生成输出：\n```json%s```",
                userInputJson
        );

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", ConflictResolutionPrompt.CONFLICT_RESOLUTION_PROMPT));
        messages.add(Map.of("role", "user", "content", userMessageContent));

        return messages;
    }

    private static Map<String, Object> createResult(String id, String text, String event) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("text", text);
        result.put("event", event);
        return result;
    }
}

