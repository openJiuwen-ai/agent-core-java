/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.prompts.PromptApplier;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code LongTermMemoryExtractor} in
 * {@code openjiuwen/core/memory/process/extract/long_term_memory_extractor.py}.
 */
public final class LongTermMemoryExtractor {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final int DEFAULT_RETRIES = 3;
    private static final DateTimeFormatter MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern("MM.dd");
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("long-term-memory-extractor-io");

    private LongTermMemoryExtractor() {
    }

    public static CompletionStage<Map<String, Object>> extractLongTermMemory(
            ExtractMemoryParams extractMemoryParas,
            String timestamp,
            MemoryScopeConfig scopeConfig
    ) {
        return extractLongTermMemory(extractMemoryParas, timestamp, scopeConfig, DEFAULT_RETRIES);
    }

    public static CompletionStage<Map<String, Object>> extractLongTermMemory(
            ExtractMemoryParams extractMemoryParas,
            String timestamp,
            MemoryScopeConfig scopeConfig,
            int retries
    ) {
        StringBuilder referenceStr = new StringBuilder();
        StringBuilder inputMsgStr = new StringBuilder();
        appendReference(referenceStr, extractMemoryParas.getHistoryMessages(), false, inputMsgStr);
        appendReference(referenceStr, extractMemoryParas.getMessages(), true, inputMsgStr);

        MemoryScopeConfig effectiveScopeConfig = scopeConfig == null ? new MemoryScopeConfig() : scopeConfig;
        Map<String, String> promptVariables = new LinkedHashMap<>();
        promptVariables.put("conversation_time", timestamp);
        promptVariables.put("input_messages", inputMsgStr.toString());
        promptVariables.put("reference_messages", referenceStr.toString());
        promptVariables.put("user_profile_definition", nullToEmpty(effectiveScopeConfig.getUserProfileDefinition()));
        promptVariables.put("semantic_memory_definition", nullToEmpty(effectiveScopeConfig.getSemanticMemoryDefinition()));
        promptVariables.put("episodic_memory_definition", nullToEmpty(effectiveScopeConfig.getEpisodicMemoryDefinition()));
        promptVariables.put("current_week", buildTimeContext(timestamp));

        String promptContent = new PromptApplier().apply("fragment_memory_prompt", promptVariables);
        List<BaseMessage> modelInput = List.of(new UserMessage(promptContent));
        return CompletableFuture.supplyAsync(
                () -> invokeAndParse(extractMemoryParas, modelInput, retries),
                IO_EXECUTOR);
    }

    static String buildTimeContext(String timestamp) {
        try {
            LocalDate date = parseIsoDate(timestamp);
            LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = monday.plusDays(6);
            return monday.getYear() + "年" + monday.getMonthValue() + "月" + monday.getDayOfMonth() + "日(周一)～"
                    + sunday.getYear() + "年" + sunday.getMonthValue() + "月" + sunday.getDayOfMonth() + "日(周日)"
                    + "（即" + MONTH_DAY_FORMAT.format(monday) + "～" + MONTH_DAY_FORMAT.format(sunday) + "）";
        } catch (DateTimeParseException | NullPointerException exception) {
            return timestamp;
        }
    }

    private static Map<String, Object> invokeAndParse(
            ExtractMemoryParams extractMemoryParas,
            List<BaseMessage> modelInput,
            int retries
    ) {
        JsonOutputParser parser = new JsonOutputParser();
        int maxRetries = Math.max(1, retries);
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                AssistantMessage response = extractMemoryParas.getBaseChatModel()
                        .invoke(modelInput).toCompletableFuture().join();
                Object parsed = parser.parse(response.getContentAsString()).toCompletableFuture().join();
                if (parsed instanceof Map<?, ?> parsedMap) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    parsedMap.forEach((key, value) -> result.put(String.valueOf(key), value));
                    return result;
                }
            } catch (RuntimeException exception) {
                if (attempt >= maxRetries - 1) {
                    MEMORY_LOGGER.error("[{}] Long term memory extractor model output format error: {}",
                            LogEventType.MEMORY_PROCESS.getValue(), exception.toString());
                }
            }
        }
        return Map.of();
    }

    private static void appendReference(
            StringBuilder referenceStr,
            List<BaseMessage> messages,
            boolean collectUserMessages,
            StringBuilder inputMsgStr
    ) {
        if (messages == null) {
            return;
        }
        for (BaseMessage message : messages) {
            String name = message.getName() == null ? message.getRole() : message.getName();
            String line = name + ": " + message.getContentAsString() + "\n";
            referenceStr.append(line);
            if (collectUserMessages && "user".equals(message.getRole())) {
                inputMsgStr.append(line);
            }
        }
    }

    private static LocalDate parseIsoDate(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp).toLocalDate();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(timestamp).toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDate.parse(timestamp);
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
