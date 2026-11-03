/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.skill_call;

import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_draft_parser.py}.
 */
public final class ExperienceDraftParser {

    private ExperienceDraftParser() {
    }

    public static List<String> normalizeKeywords(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return null;
        }
        List<String> keywords = new ArrayList<>();
        for (Object item : rawList) {
            String normalized = String.valueOf(item).trim();
            if (!normalized.isEmpty()) {
                keywords.add(normalized);
            }
        }
        return keywords.isEmpty() ? null : keywords;
    }

    public static String normalizeSummary(Object raw) {
        if (!(raw instanceof String rawString)) {
            return null;
        }
        String summary = rawString.trim().replaceAll("\\s+", " ");
        if (summary.isEmpty() || "null".equalsIgnoreCase(summary)) {
            return null;
        }
        return summary;
    }

    public static ParsedExperienceDraft parseExperienceDraft(Map<String, Object> data) {
        String action = stringValue(data.getOrDefault("action", "append"), "append");
        if ("skip".equals(action)) {
            EvolutionPatch patch = EvolutionPatch.builder()
                .section("")
                .action("skip")
                .content("")
                .skipReason(stringValue(data.getOrDefault("skip_reason", "unknown"), "unknown"))
                .build();
            return new ParsedExperienceDraft(patch, null, null);
        }

        String section = stringValue(data.getOrDefault("section", "Troubleshooting"), "Troubleshooting");
        if (!Protocols.VALID_SECTIONS.contains(section)) {
            section = "Troubleshooting";
        }

        EvolutionTarget target = EvolutionTarget.fromValue(stringValue(data.getOrDefault("target", "body"), "body"));

        Object rawMergeTarget = data.get("merge_target");
        String mergeTarget = rawMergeTarget == null || "null".equals(rawMergeTarget) ? null : String.valueOf(rawMergeTarget);

        List<String> keywords = normalizeKeywords(data.get("keywords"));
        String summary = normalizeSummary(data.get("summary"));

        EvolutionPatch patch = EvolutionPatch.builder()
            .section(section)
            .action("append")
            .content(stringValue(data.get("content"), ""))
            .target(target)
            .mergeTarget(mergeTarget)
            .scriptFilename(stringValue(data.get("script_filename"), null))
            .scriptLanguage(stringValue(data.get("script_language"), null))
            .scriptPurpose(stringValue(data.get("script_purpose"), null))
            .keywords(keywords)
            .summary(summary)
            .build();

        return new ParsedExperienceDraft(patch, summary, keywords);
    }

    public static DraftsWithError parseExperienceDraftsWithError(
            String raw,
            ExtractJsonWithErrorFunction extractJsonWithErrorFn
    ) {
        JsonExtractionResult extracted = extractJsonWithErrorFn.extract(raw);
        if (extracted.data() == null) {
            return new DraftsWithError(null, extracted.lastError());
        }

        List<?> items = extracted.data() instanceof List<?> rawList ? rawList : List.of(extracted.data());
        List<ParsedExperienceDraft> drafts = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> rawMap) {
                drafts.add(parseExperienceDraft(stringMap(rawMap)));
            }
        }
        return new DraftsWithError(drafts, "");
    }

    private static Map<String, Object> stringMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    @FunctionalInterface
    public interface ExtractJsonWithErrorFunction {
        JsonExtractionResult extract(String raw);
    }

    public record JsonExtractionResult(Object data, String lastError) {
    }

    public record DraftsWithError(List<ParsedExperienceDraft> drafts, String lastError) {
    }
}
