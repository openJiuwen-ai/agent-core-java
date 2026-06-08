/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code PromptReport} in
 * {@code openjiuwen/harness/prompts/report.py}.
 */
public class PromptReport {

    private static final double CN_CHARS_PER_TOKEN = 2.5;
    private static final double EN_CHARS_PER_TOKEN = 4.0;

    private int totalChars;
    private int estimatedTokens;
    private int sectionCount;
    private List<SectionInfo> sections = new ArrayList<>();
    private String mode = "full";
    private String language = "cn";

    public static PromptReport fromBuilder(SystemPromptBuilder builder) {
        PromptReport report = new PromptReport();
        if (builder == null) {
            return report;
        }

        report.setLanguage(builder.getLanguage() == null ? "cn" : builder.getLanguage());
        report.setMode(resolveMode(builder));

        List<PromptSection> sortedSections = new ArrayList<>(builder.getAllSections().values());
        sortedSections.sort(Comparator.comparingInt(PromptSection::getPriority));

        int total = 0;
        List<SectionInfo> sectionInfos = new ArrayList<>();
        for (PromptSection section : sortedSections) {
            int charCount = section.charCount(report.getLanguage());
            sectionInfos.add(new SectionInfo(section.getName(), section.getPriority(), charCount));
            total += charCount;
        }

        report.setSections(sectionInfos);
        report.setTotalChars(total);
        report.setEstimatedTokens(estimateTokens(total, report.getLanguage()));
        report.setSectionCount(sectionInfos.size());
        return report;
    }

    public static int estimateTokens(int totalChars, String language) {
        double charsPerToken = "cn".equals(language) ? CN_CHARS_PER_TOKEN : EN_CHARS_PER_TOKEN;
        return totalChars > 0 ? (int) (totalChars / charsPerToken) : 0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_chars", totalChars);
        result.put("estimated_tokens", estimatedTokens);
        result.put("section_count", sectionCount);

        List<Map<String, Object>> serializedSections = new ArrayList<>();
        for (SectionInfo section : sections) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", section.getName());
            entry.put("priority", section.getPriority());
            entry.put("char_count", section.getCharCount());
            serializedSections.add(entry);
        }
        result.put("sections", serializedSections);
        result.put("mode", mode);
        result.put("language", language);
        return result;
    }

    public Map<String, Object> toDict() {
        return toMap();
    }

    public String summary() {
        return String.format(
                "[PromptReport] mode=%s lang=%s sections=%d chars=%d est_tokens~%d",
                mode,
                language,
                sectionCount,
                totalChars,
                estimatedTokens
        );
    }

    private static String resolveMode(SystemPromptBuilder builder) {
        try {
            Method getter = builder.getClass().getMethod("getMode");
            Object mode = getter.invoke(builder);
            if (mode == null) {
                return "full";
            }

            try {
                Method valueGetter = mode.getClass().getMethod("value");
                Object value = valueGetter.invoke(mode);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to enum/string name below.
            }

            return String.valueOf(mode).toLowerCase();
        } catch (ReflectiveOperationException ignored) {
            return "full";
        }
    }

    public int getTotalChars() {
        return totalChars;
    }

    public void setTotalChars(int totalChars) {
        this.totalChars = totalChars;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }

    public void setEstimatedTokens(int estimatedTokens) {
        this.estimatedTokens = estimatedTokens;
    }

    public int getSectionCount() {
        return sectionCount;
    }

    public void setSectionCount(int sectionCount) {
        this.sectionCount = sectionCount;
    }

    public List<SectionInfo> getSections() {
        return sections;
    }

    public void setSections(List<SectionInfo> sections) {
        this.sections = sections == null ? new ArrayList<>() : new ArrayList<>(sections);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "full" : mode;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language == null ? "cn" : language;
    }
}
