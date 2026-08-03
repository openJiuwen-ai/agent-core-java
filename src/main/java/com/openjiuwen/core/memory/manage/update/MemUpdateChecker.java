/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.prompts.PromptApplier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Detects redundant and conflicting memories and returns add/delete actions.
 *
 * <p>Mirrors Python's {@code MemUpdateChecker} in
 * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
 */
public class MemUpdateChecker {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("mem-update-checker-io");
    private final PromptApplier promptApplier;

    public MemUpdateChecker() {
        this(PromptApplier.getInstance());
    }

    public MemUpdateChecker(PromptApplier promptApplier) {
        this.promptApplier = promptApplier == null ? PromptApplier.getInstance() : promptApplier;
    }

    public CompletableFuture<List<MemoryActionItem>> check(
            Map<String, String> newMemories,
            Map<String, String> oldMemories,
            Model baseChatModel
    ) {
        return check(newMemories, oldMemories, baseChatModel, 3);
    }

    public CompletableFuture<List<MemoryActionItem>> check(
            Map<String, String> newMemories,
            Map<String, String> oldMemories,
            Model baseChatModel,
            int retries
    ) {
        Map<String, String> newMemoryMap = copyStringMap(newMemories);
        Map<String, String> oldMemoryMap = copyStringMap(oldMemories);
        if (baseChatModel == null) {
            Loggers.MEMORY.debug(
                    "No need to check memories - no old memories or no model event_type={} metadata={}",
                    LogEventType.MEMORY_PROCESS.getValue(),
                    Map.of("new_count", newMemoryMap.size(), "old_count", oldMemoryMap.size())
            );
            return CompletableFuture.completedFuture(addAllNewMemories(newMemoryMap));
        }

        Set<String> duplicateIds = new HashSet<>(newMemoryMap.keySet());
        duplicateIds.retainAll(oldMemoryMap.keySet());
        if (!duplicateIds.isEmpty()) {
            Loggers.MEMORY.debug(
                    "Found {} duplicate memory IDs event_type={} metadata={}",
                    duplicateIds.size(),
                    LogEventType.MEMORY_PROCESS.getValue(),
                    Map.of("duplicate_ids", new ArrayList<>(duplicateIds))
            );
        }

        FormatInputResult input = formatInput(newMemoryMap, oldMemoryMap);
        String userPrompt = promptApplier.apply(
                "memory_update_check",
                Map.of("new_information", input.newInfo(), "old_information", input.oldInfo())
        );
        List<BaseMessage> messages = List.of(new UserMessage(userPrompt));

        Loggers.MEMORY.debug(
                "Start checking memory conflicts event_type={} metadata={}",
                LogEventType.MEMORY_PROCESS.getValue(),
                Map.of("input_messages", messages.stream().map(BaseMessage::modelDump).toList())
        );

        return CompletableFuture.supplyAsync(
                () -> doCheck(newMemoryMap, oldMemoryMap, baseChatModel, messages, retries),
                IO_EXECUTOR);
    }

    public static FormatInputResult formatInput(Map<String, String> newMemories, Map<String, String> oldMemories) {
        List<String> newInfoLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : copyStringMap(newMemories).entrySet()) {
            newInfoLines.add(entry.getKey() + ": " + entry.getValue());
        }
        Collections.reverse(newInfoLines);

        List<String> oldInfoLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : copyStringMap(oldMemories).entrySet()) {
            oldInfoLines.add(entry.getKey() + ": " + entry.getValue());
        }
        return new FormatInputResult(String.join("\n", newInfoLines), String.join("\n", oldInfoLines));
    }

    private List<MemoryActionItem> doCheck(
            Map<String, String> newMemories,
            Map<String, String> oldMemories,
            Model baseChatModel,
            List<BaseMessage> messages,
            int retries
    ) {
        JsonOutputParser parser = new JsonOutputParser();
        List<MemCheckItem> checkResults = new ArrayList<>();
        boolean parsedSuccessfully = false;

        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                AssistantMessage response = baseChatModel.invoke(messages).toCompletableFuture().join();
                Object parsedResult = parser.parse(response.getContentAsString()).join();
                List<?> parsedItems = normalizeParsedResult(parsedResult);
                if (parsedItems == null) {
                    if (attempt >= retries - 1) {
                        return addAllNewMemories(newMemories);
                    }
                    continue;
                }
                parsedSuccessfully = true;
                for (Object item : parsedItems) {
                    checkResults.add(OBJECT_MAPPER.convertValue(item, MemCheckItem.class));
                }
                Loggers.MEMORY.debug(
                        "Succeeded to check memories, got {} results event_type={} metadata={}",
                        checkResults.size(),
                        LogEventType.MEMORY_PROCESS.getValue(),
                        Map.of("result_count", checkResults.size())
                );
                break;
            } catch (IllegalArgumentException exception) {
                if (attempt < retries - 1) {
                    Loggers.MEMORY.warning(
                            "Memory check parse error, retrying ({}/{}): {} event_type={} exception={}",
                            attempt + 1,
                            retries,
                            exception.getMessage(),
                            LogEventType.MEMORY_PROCESS.getValue(),
                            exception.toString()
                    );
                    continue;
                }
                Loggers.MEMORY.error(
                        "Memory check failed after retries event_type={} exception={}",
                        LogEventType.MEMORY_PROCESS.getValue(),
                        exception.toString()
                );
                return addAllNewMemories(newMemories);
            }
        }
        if (!parsedSuccessfully) {
            return addAllNewMemories(newMemories);
        }

        List<MemoryActionItem> actionItems = new ArrayList<>();
        Set<String> processedNewIds = new HashSet<>();
        for (MemCheckItem checkItem : checkResults) {
            String newId = checkItem.infoId();
            processedNewIds.add(newId);
            if (checkItem.result() == CheckResult.REDUNDANT) {
                Loggers.MEMORY.debug(
                        "Memory {} is redundant, skipping event_type={}",
                        newId,
                        LogEventType.MEMORY_PROCESS.getValue()
                );
            } else if (checkItem.result() == CheckResult.CONFLICTING) {
                String newContent = newMemories.getOrDefault(newId, checkItem.infoText());
                actionItems.add(new MemoryActionItem(newId, newContent, MemoryStatus.ADD));
                for (Map.Entry<String, String> entry : checkItem.relatedInfos().entrySet()) {
                    if (oldMemories.containsKey(entry.getKey())) {
                        actionItems.add(new MemoryActionItem(entry.getKey(), entry.getValue(), MemoryStatus.DELETE));
                    }
                }
            } else if (checkItem.result() == CheckResult.NONE) {
                String newContent = newMemories.getOrDefault(newId, checkItem.infoText());
                actionItems.add(new MemoryActionItem(newId, newContent, MemoryStatus.ADD));
            }
        }

        Loggers.MEMORY.debug(
                "Memory check completed, returning {} action items event_type={} metadata={}",
                actionItems.size(),
                LogEventType.MEMORY_PROCESS.getValue(),
                Map.of("action_count", actionItems.size())
        );
        return actionItems;
    }

    private static List<?> normalizeParsedResult(Object parsedResult) {
        if (parsedResult instanceof Map<?, ?> map) {
            return List.of(map);
        }
        if (parsedResult instanceof List<?> list) {
            return list;
        }
        return null;
    }

    private static List<MemoryActionItem> addAllNewMemories(Map<String, String> newMemories) {
        List<MemoryActionItem> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : newMemories.entrySet()) {
            result.add(new MemoryActionItem(entry.getKey(), entry.getValue(), MemoryStatus.ADD));
        }
        return result;
    }

    private static Map<String, String> copyStringMap(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /**
     * Formatted prompt inputs for new and old memories.
     *
     * <p>Mirrors Python's {@code _format_input} tuple return in
     * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
     */
    public record FormatInputResult(String newInfo, String oldInfo) {
    }
}
