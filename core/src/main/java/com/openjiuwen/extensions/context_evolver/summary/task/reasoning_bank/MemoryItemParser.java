/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses LLM markdown responses into ReasoningBank memories.
 * <p>
 * Mirrors Python's {@code MemoryItemParser} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 * </p>
 */
public final class MemoryItemParser {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final Pattern SECTION_SPLIT = Pattern.compile("\\n\\s*#\\s*Memory\\s+Item\\s+\\d+");
    private static final Pattern TITLE_PATTERN = Pattern.compile("^##\\s*Title\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^##\\s*Description\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_PATTERN = Pattern.compile("^##\\s*Content\\s+", Pattern.CASE_INSENSITIVE);

    private MemoryItemParser() {
    }

    public static List<ReasoningBankMemory> parse(String llmResponse, String query, Boolean label) {
        String cleanedResponse = cleanResponse(llmResponse);
        List<String> sections = splitIntoSections(cleanedResponse);

        List<ReasoningBankMemoryItem> memoryItems = new ArrayList<>();
        for (String section : sections) {
            ReasoningBankMemoryItem item = extractMemoryItem(section);
            if (item != null) {
                memoryItems.add(item);
                LOGGER.debug("Extracted memory: %s", item.getTitle());
            }
        }
        ReasoningBankMemory memory = new ReasoningBankMemory();
        memory.setQuery(query);
        memory.setMemory(memoryItems);
        memory.setLabel(label);
        return List.of(memory);
    }

    static String cleanResponse(String llmResponse) {
        String response = llmResponse == null ? "" : llmResponse.strip();
        if (response.startsWith("```")) {
            int newlineIndex = response.indexOf('\n');
            response = newlineIndex >= 0 ? response.substring(newlineIndex + 1) : "";
        }
        if (response.endsWith("```")) {
            int fenceIndex = response.lastIndexOf("```");
            response = fenceIndex >= 0 ? response.substring(0, fenceIndex) : response;
        }
        return response.strip();
    }

    static List<String> splitIntoSections(String llmResponse) {
        String[] rawSections = SECTION_SPLIT.split(llmResponse);
        List<String> sections = new ArrayList<>();
        for (String section : rawSections) {
            String trimmed = section.strip();
            if (!trimmed.isEmpty()) {
                sections.add(trimmed);
            }
        }
        return sections;
    }

    private static ReasoningBankMemoryItem extractMemoryItem(String section) {
        String title = "";
        String description = "";
        String content = "";

        String[] lines = section.split("\\n");
        int index = 0;
        while (index < lines.length) {
            String line = lines[index].strip();
            if (line.isEmpty() || "```".equals(line)) {
                index += 1;
                continue;
            }
            if (TITLE_PATTERN.matcher(line).find()) {
                FieldResult result = extractField(lines, index, TITLE_PATTERN);
                title = result.value();
                index = result.nextIndex();
                continue;
            }
            if (DESCRIPTION_PATTERN.matcher(line).find()) {
                FieldResult result = extractField(lines, index, DESCRIPTION_PATTERN);
                description = result.value();
                index = result.nextIndex();
                continue;
            }
            if (CONTENT_PATTERN.matcher(line).find()) {
                FieldResult result = extractField(lines, index, CONTENT_PATTERN);
                content = result.value();
                index = result.nextIndex();
                continue;
            }
            index += 1;
        }

        if (!title.isEmpty() && !description.isEmpty() && !content.isEmpty()) {
            return new ReasoningBankMemoryItem(title, description, content);
        }
        LOGGER.warning("Incomplete memory item extracted (missing fields)");
        return null;
    }

    private static FieldResult extractField(String[] lines, int startIndex, Pattern fieldPattern) {
        String line = lines[startIndex].strip();
        StringBuilder fieldValue = new StringBuilder(fieldPattern.matcher(line).replaceFirst("").strip());
        int index = startIndex + 1;
        while (index < lines.length) {
            String nextLine = lines[index].strip();
            if (nextLine.isEmpty() || "```".equals(nextLine)) {
                index += 1;
                continue;
            }
            if (nextLine.startsWith("##") || nextLine.startsWith("# Memory Item")) {
                break;
            }
            fieldValue.append(" ").append(nextLine);
            index += 1;
        }
        return new FieldResult(fieldValue.toString().strip(), index);
    }

    private record FieldResult(String value, int nextIndex) {
    }
}
