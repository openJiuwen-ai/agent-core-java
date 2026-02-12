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
import com.openjiuwen.core.memory.prompt.CategorizerPrompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Categorizer for memory classification using LLM.
 * Corresponds to Python: process/extract/categorizer.py
 */
public class Categorizer {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    public Categorizer() {
    }

    /**
     * Get categories from messages using LLM.
     *
     * @param messages        Current messages
     * @param historyMessages History messages
     * @param baseChatModel   Tuple of model name and model client
     * @return CompletableFuture of list of category strings
     */
    public static CompletableFuture<List<String>> getCategories(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Pair<String, Model> baseChatModel
    ) {
        return getCategories(messages, historyMessages, baseChatModel, 3);
    }

    /**
     * Get categories from messages using LLM.
     *
     * @param messages        Current messages
     * @param historyMessages History messages
     * @param baseChatModel   Tuple of model name and model client
     * @param retries         Number of retries on failure
     * @return CompletableFuture of list of category strings
     */
    public static CompletableFuture<List<String>> getCategories(
            List<BaseMessage> messages,
            List<BaseMessage> historyMessages,
            Pair<String, Model> baseChatModel,
            int retries
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> modelInput = ExtractUtils.buildModelInput(
                    messages,
                    historyMessages,
                    CategorizerPrompt.CATEGORIZATION_PROMPT
            );

            Model modelClient = baseChatModel.getValue();

            logger.debug("Start to get categories, input: {}", modelInput);

            JsonOutputParser parser = new JsonOutputParser();

            for (int attempt = 0; attempt < retries; attempt++) {
                try {
                    BaseMessage response = modelClient.invoke(modelInput).join();
                    Object content = response.getContent();
                    String contentStr = content instanceof String ? (String) content : content.toString();

                    Object parsed = parser.parse(contentStr).join();

                    logger.debug("Succeed to get categories, result: {}", parsed);

                    if (parsed instanceof Map<?, ?> parsedMap && parsedMap.containsKey("categories")) {
                        Object categories = parsedMap.get("categories");
                        if (categories instanceof List<?> categoryList) {
                            @SuppressWarnings("unchecked")
                            List<String> result = (List<String>) categoryList;
                            return result;
                        }
                    }
                } catch (Exception e) {
                    if (attempt < retries - 1) {
                        continue;
                    }
                    logger.error("categories model output format error: {}", e.getMessage());
                }
            }

            return List.of();
        });
    }
}
