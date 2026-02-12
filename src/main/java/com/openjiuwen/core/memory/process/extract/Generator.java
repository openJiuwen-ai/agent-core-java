/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.memmodel.MemoryType;
import com.openjiuwen.core.memory.manage.memmodel.UserProfileUnit;
import com.openjiuwen.core.memory.manage.memmodel.VariableUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generator for memory units from conversation.
 * Corresponds to Python: process/extract/generation.py Generator
 */
public class Generator {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    /**
     * Mapping from category string to MemoryType.
     */
    public static final Map<String, MemoryType> CATEGORY_TO_CLASS = Map.of(
            "user_profile", MemoryType.USER_PROFILE
    );

    public Generator() {
    }

    /**
     * Generate all memory units based on input.
     *
     * @param messages        Current messages
     * @param config          Agent memory configuration
     * @param userId          User ID
     * @param scopeId         Scope ID
     * @param baseChatModel   Chat model tuple
     * @param historyMessages History messages
     * @param messageMemId    Message memory ID
     * @return CompletableFuture of list of memory units
     */
    public CompletableFuture<List<BaseMemoryUnit>> genAllMemory(
            List<BaseMessage> messages,
            AgentMemoryConfig config,
            String userId,
            String scopeId,
            Pair<String, Model> baseChatModel,
            List<BaseMessage> historyMessages,
            String messageMemId
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (messages == null || config == null || userId == null || scopeId == null || baseChatModel == null) {
                logger.error("messages, config, user_id, scope_id, model are required parameters");
                return List.of();
            }

            ExtractMemoryParams extractMemoryParams = new ExtractMemoryParams(
                    userId,
                    scopeId,
                    messages,
                    historyMessages != null ? historyMessages : List.of(),
                    baseChatModel
            );

            List<BaseMemoryUnit> allMemoryResults = new ArrayList<>();

            try {
                List<VariableUnit> variableUnits = genExtractedData(extractMemoryParams, config).join();
                allMemoryResults.addAll(variableUnits);
            } catch (Exception e) {
                logger.debug("Failed to extract variables: {}", e.getMessage());
            }

            if (!config.isEnableLongTermMem()) {
                logger.info("Not enable long term memory");
                return allMemoryResults;
            }

            try {
                List<String> categories = Categorizer.getCategories(
                        messages,
                        historyMessages != null ? historyMessages : List.of(),
                        baseChatModel
                ).join();

                List<BaseMemoryUnit> mergedUnits = categoriesToMemoryUnit(
                        categories,
                        extractMemoryParams,
                        messageMemId,
                        null
                ).join();
                allMemoryResults.addAll(mergedUnits);

            } catch (Exception e) {
                String msg = e.getMessage();
                if (e instanceof IllegalArgumentException) {
                    logger.warning("Get conflict info has value exception: {}", msg);
                } else {
                    logger.warning("Get conflict info has exception: {}", msg);
                }
            }

            return allMemoryResults;
        });
    }

    /**
     * Generate extracted variable memory units based on input.
     *
     * @param extractMemoryParams Extract memory parameters
     * @param config              Agent memory configuration
     * @return CompletableFuture of list of variable units
     */
    public CompletableFuture<List<VariableUnit>> genExtractedData(
            ExtractMemoryParams extractMemoryParams,
            AgentMemoryConfig config
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<ExtractedData> extractedData = generateExtract(
                    config,
                    extractMemoryParams.historyMessages(),
                    extractMemoryParams.messages(),
                    extractMemoryParams.baseChatModel()
            ).join();

            List<VariableUnit> variableUnits = new ArrayList<>();
            for (ExtractedData tmpData : extractedData) {
                variableUnits.add(VariableUnit.builder()
                        .userId(extractMemoryParams.userId())
                        .scopeId(extractMemoryParams.scopeId())
                        .variableName(tmpData.key())
                        .variableMem(tmpData.value())
                        .build());
            }
            return variableUnits;
        });
    }

    /**
     * Generate user profile memory unit based on input.
     *
     * @param extractMemoryParams Extract memory parameters
     * @param messageMemId        Message memory ID
     * @param userDefine          User-defined profile dimensions
     * @return CompletableFuture of list of user profile units
     */
    public CompletableFuture<List<UserProfileUnit>> genUserProfile(
            ExtractMemoryParams extractMemoryParams,
            String messageMemId,
            Map<String, String> userDefine
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> userProfileMemory = UserProfileExtractor.getUserProfile(
                    extractMemoryParams.messages(),
                    extractMemoryParams.historyMessages(),
                    extractMemoryParams.baseChatModel(),
                    userDefine,
                    3
            ).join();

            List<UserProfileUnit> userProfileData = new ArrayList<>();

            for (Map.Entry<String, Object> entry : userProfileMemory.entrySet()) {
                String profileType = entry.getKey();
                Object profileList = entry.getValue();

                if (!(profileList instanceof List<?>)) {
                    logger.warning("User profile extractor output format error: {} is not a list", profileList);
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<String> profiles = (List<String>) profileList;
                for (String profile : profiles) {
                    userProfileData.add(UserProfileUnit.builder()
                            .userId(extractMemoryParams.userId())
                            .scopeId(extractMemoryParams.scopeId())
                            .profileType(profileType)
                            .profileMem(profile)
                            .messageMemId(messageMemId)
                            .build());
                }
            }

            return userProfileData;
        });
    }

    /**
     * Convert categories to memory units.
     *
     * @param categories          List of category strings
     * @param extractMemoryParams Extract memory parameters
     * @param messageMemId        Message memory ID
     * @param userDefine          User-defined profile dimensions
     * @return CompletableFuture of list of memory units
     */
    private CompletableFuture<List<BaseMemoryUnit>> categoriesToMemoryUnit(
            List<String> categories,
            ExtractMemoryParams extractMemoryParams,
            String messageMemId,
            Map<String, String> userDefine
    ) {
        return CompletableFuture.supplyAsync(() -> {
            List<BaseMemoryUnit> memoryUnits = new ArrayList<>();

            for (String category : categories) {
                if (!CATEGORY_TO_CLASS.containsKey(category)) {
                    logger.warning("Unsupported memory category: {}, skipped.", category);
                    continue;
                }

                MemoryType memClass = CATEGORY_TO_CLASS.get(category);
                if (memClass == MemoryType.USER_PROFILE) {
                    List<UserProfileUnit> userProfileUnits = genUserProfile(
                            extractMemoryParams,
                            messageMemId,
                            userDefine
                    ).join();
                    memoryUnits.addAll(userProfileUnits);
                }
            }

            return memoryUnits;
        });
    }

    /**
     * Generate extract data from config and messages.
     *
     * @param config          Agent memory configuration
     * @param historyMessages History messages
     * @param messages        Current messages
     * @param baseChatModel   Chat model tuple
     * @return CompletableFuture of list of extracted data
     */
    private CompletableFuture<List<ExtractedData>> generateExtract(
            AgentMemoryConfig config,
            List<BaseMessage> historyMessages,
            List<BaseMessage> messages,
            Pair<String, Model> baseChatModel
    ) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder historySummary = new StringBuilder();
            if (historyMessages != null) {
                for (BaseMessage msg : historyMessages) {
                    historySummary.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                }
            }

            return ComprehensionExtractor.extract(
                    messages,
                    new BaseMessage(historySummary.toString(), ""),
                    baseChatModel,
                    config
            ).join();
        });
    }
}
