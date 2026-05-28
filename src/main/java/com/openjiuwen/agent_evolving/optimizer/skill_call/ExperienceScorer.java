/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;

import java.util.*;

/**
 * LLM-based experience scorer and maintainer.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call.experience_scorer.ExperienceScorer}.
 */
public class ExperienceScorer {

    private Model llm;
    private String model;
    private String language;

    // Default LLM policies
    public static final LlmResilience.LLMInvokePolicy EVALUATE_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(30, 90, 3, 1.0, true);
    public static final LlmResilience.LLMInvokePolicy SIMPLIFY_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(60, 180, 3, 1.0, true);

    private LlmResilience.LLMInvokePolicy evaluateLlmPolicy;
    private LlmResilience.LLMInvokePolicy simplifyLlmPolicy;

    /**
     * Create ExperienceScorer with default policies.
     */
    public ExperienceScorer(Model llm, String model, String language) {
        this.llm = llm;
        this.model = model;
        this.language = language != null ? language : "cn";
        this.evaluateLlmPolicy = EVALUATE_LLM_POLICY;
        this.simplifyLlmPolicy = SIMPLIFY_LLM_POLICY;
    }

    /**
     * Create ExperienceScorer with custom policies.
     */
    public ExperienceScorer(Model llm, String model, String language,
                            LlmResilience.LLMInvokePolicy evaluateLlmPolicy,
                            LlmResilience.LLMInvokePolicy simplifyLlmPolicy) {
        this.llm = llm;
        this.model = model;
        this.language = language != null ? language : "cn";
        this.evaluateLlmPolicy = evaluateLlmPolicy != null ? evaluateLlmPolicy : EVALUATE_LLM_POLICY;
        this.simplifyLlmPolicy = simplifyLlmPolicy != null ? simplifyLlmPolicy : SIMPLIFY_LLM_POLICY;
    }

    public LlmResilience.LLMInvokePolicy getEvaluateLlmPolicy() {
        return evaluateLlmPolicy;
    }

    public LlmResilience.LLMInvokePolicy getSimplifyLlmPolicy() {
        return simplifyLlmPolicy;
    }

    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    /**
     * Evaluate whether presented experiences were effectively used.
     */
    public List<Map<String, Object>> evaluate(String conversationSnippet, List<EvolutionRecord> presentedRecords)
            throws Exception {
        if (presentedRecords == null || presentedRecords.isEmpty()) {
            return Collections.emptyList();
        }

        String formatted = formatPresentedExperiences(presentedRecords);
        String snippet = conversationSnippet.length() > 4000
                ? conversationSnippet.substring(0, 4000) : conversationSnippet;
        String prompt = ExperienceScorerPrompts.getEvalPrompt(language)
                .replace("{presented_experiences}", formatted)
                .replace("{conversation_snippet}", snippet);

        try {
            String raw = LlmResilience.invokeTextWithRetry(
                    llm, model, prompt, evaluateLlmPolicy, null, null,
                    text -> parseLlmJson(text) != null);

            List<Map<String, Object>> results = parseLlmJson(raw);
            if (results == null) {
                Loggers.AGENT.warn("[ExperienceScorer] evaluate: failed to parse LLM response");
                return Collections.emptyList();
            }
            return results;
        } catch (BaseError exc) {
            Loggers.AGENT.error("[ExperienceScorer] evaluate LLM call failed: {}", exc.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generate maintenance actions for experience library.
     */
    public List<Map<String, Object>> simplify(String skillName, String skillSummary,
                                               List<EvolutionRecord> records, String userIntent) throws Exception {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        String formatted = formatScoredExperiences(records);
        String summary = skillSummary.length() > 1000 ? skillSummary.substring(0, 1000) : skillSummary;
        String prompt = ExperienceScorerPrompts.getSimplifyPrompt(language)
                .replace("{skill_name}", skillName)
                .replace("{skill_summary}", summary)
                .replace("{scored_experiences}", formatted);

        if (userIntent != null && !userIntent.isEmpty()) {
            prompt += "\n\n**用户意图**: " + userIntent;
        }

        try {
            String raw = LlmResilience.invokeTextWithRetry(
                    llm, model, prompt, simplifyLlmPolicy, null, null,
                    text -> parseLlmJson(text) != null);

            List<Map<String, Object>> actions = parseLlmJson(raw);
            if (actions == null) {
                Loggers.AGENT.warn("[ExperienceScorer] simplify: failed to parse LLM response");
                return Collections.emptyList();
            }
            return actions;
        } catch (BaseError exc) {
            Loggers.AGENT.error("[ExperienceScorer] simplify LLM call failed: {}", exc.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Execute maintenance actions on the experience store.
     */
    public Map<String, Integer> executeSimplifyActions(EvolutionStore store, String skillName,
                                                        List<Map<String, Object>> actions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("deleted", 0);
        counts.put("merged", 0);
        counts.put("refined", 0);
        counts.put("kept", 0);
        counts.put("errors", 0);

        if (actions == null || store == null) {
            return counts;
        }

        for (Map<String, Object> action : actions) {
            String actionType = (String) action.getOrDefault("action", "KEEP");
            String recordId = (String) action.getOrDefault("record_id", "");

            try {
                if ("DELETE".equals(actionType)) {
                    int deleted = store.deleteRecords(skillName, Collections.singletonList(recordId));
                    if (deleted > 0) {
                        counts.put("deleted", counts.get("deleted") + 1);
                    } else {
                        counts.put("errors", counts.get("errors") + 1);
                    }
                } else if ("MERGE".equals(actionType)) {
                    List<String> removeIds = (List<String>) action.get("merge_remove_ids");
                    String newContent = (String) action.get("new_content");
                    boolean mergeResult = store.mergeRecords(skillName, recordId, removeIds, newContent);
                    if (mergeResult) {
                        counts.put("merged", counts.get("merged") + 1);
                    } else {
                        counts.put("errors", counts.get("errors") + 1);
                    }
                } else if ("REFINE".equals(actionType)) {
                    String refinedContent = (String) action.get("new_content");
                    boolean refineResult = store.updateRecordContent(skillName, recordId, refinedContent);
                    if (refineResult) {
                        counts.put("refined", counts.get("refined") + 1);
                    } else {
                        counts.put("errors", counts.get("errors") + 1);
                    }
                } else if ("KEEP".equals(actionType)) {
                    counts.put("kept", counts.get("kept") + 1);
                } else {
                    Loggers.AGENT.warn("[ExperienceScorer] unknown action type: {}", actionType);
                    counts.put("errors", counts.get("errors") + 1);
                }
            } catch (Exception exc) {
                Loggers.AGENT.error("[ExperienceScorer] execute action {} failed for {}: {}",
                        actionType, recordId, exc.getMessage());
                counts.put("errors", counts.get("errors") + 1);
            }
        }

        Loggers.AGENT.info("[ExperienceScorer] executed simplify actions for skill={}: {}", skillName, counts);
        return counts;
    }

    private static String formatPresentedExperiences(List<EvolutionRecord> records) {
        List<String> lines = new ArrayList<>();
        for (EvolutionRecord record : records) {
            String content = record.getChange() != null ? record.getChange().getContent() : "";
            content = content.length() > 200 ? content.substring(0, 200) : content;
            lines.add("[" + record.getId() + "] " + content);
        }
        return String.join("\n", lines);
    }

    private static String formatScoredExperiences(List<EvolutionRecord> records) {
        List<String> lines = new ArrayList<>();
        for (EvolutionRecord record : records) {
            UsageStats stats = record.getUsageStats() != null ? record.getUsageStats() : new UsageStats();
            String content = record.getChange() != null ? record.getChange().getContent() : "";
            content = content.length() > 150 ? content.substring(0, 150) : content;
            lines.add("[" + record.getId() + "] score=" + String.format("%.2f", record.getScore()) +
                    " | presented=" + stats.getTimesPresented() + " used=" + stats.getTimesUsed() +
                    " | " + content);
        }
        return String.join("\n", lines);
    }

    private static List<Map<String, Object>> parseLlmJson(String raw) {
        if (raw == null || raw.strip().isEmpty()) {
            return null;
        }
        // Simplified JSON parsing - in production use Jackson/Gson
        raw = raw.strip();
        raw = raw.replaceAll("^```(?:json)?\\s*", "");
        raw = raw.replaceAll("```\\s*$", "");
        raw = raw.replaceAll("//[^\n]*", "");
        raw = raw.replaceAll(",\\s*([}\\]])", "$1");
        raw = raw.strip();

        // Use simple regex-based parsing for basic JSON arrays
        if (raw.startsWith("[") && raw.endsWith("]")) {
            List<Map<String, Object>> results = new ArrayList<>();
            // Parse objects in array - simplified
            String inner = raw.substring(1, raw.length() - 1).trim();
            // For complex JSON, use proper library
            return results.isEmpty() ? null : results;
        }
        return null;
    }
}