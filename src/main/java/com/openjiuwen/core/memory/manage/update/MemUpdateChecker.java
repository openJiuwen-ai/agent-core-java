/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.memory.prompt.PromptApplier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Memory update checker for detecting redundancy and conflicts between memories.
 * Uses LLM with a prompt template to analyze whether new memories are redundant,
 * conflicting, or can coexist with existing memories.
 *
 * <p>Mirrors Python's {@code MemUpdateChecker} in
 * {@code openjiuwen.core.memory.manage.update.mem_update_checker}.
 */
public class MemUpdateChecker {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private final PromptApplier promptApplier;

    public MemUpdateChecker() {
        this.promptApplier = PromptApplier.getInstance();
    }

    /**
     * Check for redundancy and conflicts between new and old memories.
     *
     * @param newMemories    dictionary of new memories {id: content}
     * @param oldMemories    dictionary of existing memories {id: content}
     * @param baseChatModel  pair of (modelName, modelClient), nullable
     * @return list of action items
     */
    public List<MemoryActionItem> check(Map<String, String> newMemories,
                                         Map<String, String> oldMemories,
                                         Map.Entry<String, Model> baseChatModel) {
        return check(newMemories, oldMemories, baseChatModel, 3);
    }

    public List<MemoryActionItem> check(Map<String, String> newMemories,
                                         Map<String, String> oldMemories,
                                         Map.Entry<String, Model> baseChatModel,
                                         int retries) {
        Map<String, String> safeNewMemories = newMemories == null ? Map.of() : newMemories;
        Map<String, String> safeOldMemories = oldMemories == null ? Map.of() : oldMemories;

        // Python only short-circuits when no model is available.
        if (baseChatModel == null) {
            return safeNewMemories.entrySet().stream()
                    .map(e -> MemoryActionItem.builder()
                            .id(e.getKey()).content(e.getValue()).status(MemoryStatus.ADD).build())
                    .collect(Collectors.toList());
        }

        // Format input for prompt
        String[] formattedInput = formatInput(safeNewMemories, safeOldMemories);
        String newInfoStr = formattedInput[0];
        String oldInfoStr = formattedInput[1];

        String userPrompt = promptApplier.apply("memory_update_check",
                Map.of("new_information", newInfoStr, "old_information", oldInfoStr));

        String modelName = baseChatModel.getKey();
        Model modelClient = baseChatModel.getValue();

        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", userPrompt));
        JsonOutputParser parser = new JsonOutputParser();
        List<MemCheckItem> checkResults = new ArrayList<>();
        boolean parsedSuccessfully = false;

        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                AssistantMessage response = modelClient.invoke(messages, null, null, null,
                        modelName, null, null, null, null, null);
                Object parsed = parser.parse(response.getContentAsString());

                List<Map<String, Object>> parsedList;
                if (parsed instanceof Map) {
                    parsedList = List.of(asMap(parsed));
                } else if (parsed instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> rawList = (List<Object>) parsed;
                    parsedList = rawList.stream().map(this::asMap).collect(Collectors.toList());
                } else {
                    throw new IllegalArgumentException("parsed result must be a JSON object or array");
                }

                for (Map<String, Object> item : parsedList) {
                    MemCheckItem checkItem = parseCheckItem(item);
                    checkResults.add(checkItem);
                }
                parsedSuccessfully = true;
                break;
            } catch (Exception e) {
                if (attempt < retries - 1) {
                    MEMORY_LOGGER.warn("[{}] Memory check parse error, retrying ({}/{}): {}",
                            LogEventType.MEMORY_PROCESS, attempt + 1, retries, e.getMessage());
                } else {
                    MEMORY_LOGGER.error("[{}] Memory check failed after retries: {}",
                            LogEventType.MEMORY_PROCESS, e.getMessage());
                    return buildAddActions(safeNewMemories);
                }
            }
        }

        if (!parsedSuccessfully && !safeNewMemories.isEmpty()) {
            return buildAddActions(safeNewMemories);
        }

        // Map check results to action items
        List<MemoryActionItem> actionItems = new ArrayList<>();
        for (MemCheckItem checkItem : checkResults) {
            String newId = checkItem.getInfoId();

            if (checkItem.getResult() == CheckResult.REDUNDANT) {
                continue;
            } else if (checkItem.getResult() == CheckResult.CONFLICTING) {
                String newContent = safeNewMemories.getOrDefault(newId, checkItem.getInfoText());
                actionItems.add(MemoryActionItem.builder()
                        .id(newId).content(newContent).status(MemoryStatus.ADD).build());
                for (Map.Entry<String, String> relEntry : checkItem.getRelatedInfos().entrySet()) {
                    if (safeOldMemories.containsKey(relEntry.getKey())) {
                        actionItems.add(MemoryActionItem.builder()
                                .id(relEntry.getKey()).content(relEntry.getValue())
                                .status(MemoryStatus.DELETE).build());
                    }
                }
            } else {
                String newContent = safeNewMemories.getOrDefault(newId, checkItem.getInfoText());
                actionItems.add(MemoryActionItem.builder()
                        .id(newId).content(newContent).status(MemoryStatus.ADD).build());
            }
        }

        return actionItems;
    }

    public static String[] formatInput(Map<String, String> newMemories, Map<String, String> oldMemories) {
        return new String[]{formatMemories(newMemories, true), formatMemories(oldMemories, false)};
    }

    private static String formatMemories(Map<String, String> memories, boolean reverse) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(memories.entrySet());
        if (reverse) {
            Collections.reverse(entries);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : entries) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    private static List<MemoryActionItem> buildAddActions(Map<String, String> memories) {
        return memories.entrySet().stream()
                .map(en -> MemoryActionItem.builder()
                        .id(en.getKey()).content(en.getValue()).status(MemoryStatus.ADD).build())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        return (Map<String, Object>) obj;
    }

    @SuppressWarnings("unchecked")
    private MemCheckItem parseCheckItem(Map<String, Object> item) {
        String infoId = String.valueOf(item.getOrDefault("info_id", ""));
        String infoText = String.valueOf(item.getOrDefault("info_text", ""));
        String resultStr = String.valueOf(item.getOrDefault("result", "none"));
        CheckResult result = CheckResult.fromValue(resultStr);
        Map<String, String> relatedInfos = new LinkedHashMap<>();
        Object ri = item.get("related_infos");
        if (ri instanceof Map) {
            Map<String, Object> riMap = (Map<String, Object>) ri;
            for (Map.Entry<String, Object> e : riMap.entrySet()) {
                relatedInfos.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        return MemCheckItem.builder()
                .infoId(infoId)
                .infoText(infoText)
                .result(result)
                .relatedInfos(relatedInfos)
                .build();
    }
}
