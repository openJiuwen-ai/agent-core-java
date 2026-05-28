/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic report for a built system prompt.
 * <p>
 * Mirrors Python's {@code PromptReport} dataclass from
 * {@code harness/prompts/report.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptReport {

    private static final double CN_CHARS_PER_TOKEN = 2.5;
    private static final double EN_CHARS_PER_TOKEN = 4.0;

    private int totalChars;
    private int estimatedTokens;
    private int sectionCount;

    @Builder.Default
    private List<SectionInfo> sections = new ArrayList<>();

    @Builder.Default
    private String mode = "full";

    @Builder.Default
    private String language = "cn";

    /**
     * Estimate tokens from char count based on language.
     */
    public static int estimateTokens(int totalChars, String language) {
        double charsPerToken = "cn".equals(language) ? CN_CHARS_PER_TOKEN : EN_CHARS_PER_TOKEN;
        return totalChars > 0 ? (int) (totalChars / charsPerToken) : 0;
    }

    /**
     * Serialize to a plain dict/map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("total_chars", totalChars);
        result.put("estimated_tokens", estimatedTokens);
        result.put("section_count", sectionCount);
        List<Map<String, Object>> sectionList = new ArrayList<>();
        for (SectionInfo s : sections) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", s.getName());
            m.put("priority", s.getPriority());
            m.put("char_count", s.getCharCount());
            sectionList.add(m);
        }
        result.put("sections", sectionList);
        result.put("mode", mode);
        result.put("language", language);
        return result;
    }

    /**
     * Human-readable one-line summary.
     */
    public String summary() {
        return String.format("[PromptReport] mode=%s lang=%s sections=%d chars=%d tokens≈%d",
                mode, language, sectionCount, totalChars, estimatedTokens);
    }
}
