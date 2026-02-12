/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.outputparsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.prompt.UserProfileExtractorPrompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Extractor for user profile information using LLM.
 * Corresponds to Python: process/extract/user_profile_extractor.py
 */
public class UserProfileExtractor {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    public UserProfileExtractor() {
    }

    /**
     * Build the prompt message with user-defined dimensions.
     *
     * @param userDefine Map of user-defined dimension names to descriptions
     * @return Formatted prompt string
     */
    public static String getMessage(Map<String, String> userDefine) {
        if (userDefine != null && !userDefine.isEmpty()) {
            StringBuilder userDefineDescription = new StringBuilder();
            StringBuilder userDefineFormat = new StringBuilder();

            for (Map.Entry<String, String> entry : userDefine.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                userDefineDescription.append("    *   **").append(key).append(":** ").append(value).append("等相关信息\n");
                userDefineFormat.append(",\n    \"").append(key).append("\": []");
            }

            return UserProfileExtractorPrompt.USER_PROFILE_EXTRACTOR_PROMPT
                    .replace("{user_define_description}", userDefineDescription.toString())
                    .replace("{user_define_format}", userDefineFormat.toString());
        } else {
            return UserProfileExtractorPrompt.USER_PROFILE_EXTRACTOR_PROMPT
                    .replace("{user_define_description}", "")
                    .replace("{user_define_format}", "");
        }
    }

    /**
     * Get user profile from messages using LLM.
     *
     * @param messages        Current messages
     * @param historyMessages History messages
     * @param baseChatModel   Tuple of model name and model client
     * @param userDefine      User-defined profile dimensions
     * @param retries         Number of retries on failure
     * @return CompletableFuture of profile map
     */
    public static CompletableFuture<Map<String, Object>> getUserProfile(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Pair<String, Model> baseChatModel,
            Map<String, String> userDefine,
            int retries
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String symPrompt = getMessage(userDefine);
            List<Map<String, Object>> modelInput = ExtractUtils.buildModelInput(
                    messages,
                    historyMessages,
                    symPrompt
            );

            logger.debug("Start to get user profile, input: {}", modelInput);

            Model modelClient = baseChatModel.getValue();

            JsonOutputParser parser = new JsonOutputParser();

            for (int attempt = 0; attempt < retries; attempt++) {
                try {
                    BaseMessage response = modelClient.invoke(modelInput).join();
                    Object content = response.getContent();
                    String contentStr = content instanceof String ? (String) content : content.toString();

                    Object result = parser.parse(contentStr).join();

                    logger.debug("Succeed to get user profile, result: {}", result);

                    if (result instanceof Map<?, ?> resultMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedResult = (Map<String, Object>) resultMap;
                        return typedResult;
                    }
                } catch (Exception e) {
                    if (attempt < retries - 1) {
                        continue;
                    }
                    logger.error("user profile extractor model output format error: {}", e.getMessage());
                }
            }

            return Map.of();
        });
    }
}
