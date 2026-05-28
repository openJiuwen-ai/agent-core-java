/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill rewriter: integrate evolution experiences into SKILL.md content.
 * <p>
 * This rewriter uses LLM to deeply integrate experiences into the
 * SKILL.md body for a more natural, coherent document.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call.skill_rewriter.SkillRewriter}.
 */
public class SkillRewriter {

    private Model llm;
    private String model;
    private String language;

    // Minimum content ratio to prevent truncation
    private static final double MIN_CONTENT_RATIO = 0.5;
    // Max retry attempts for malformed output
    private static final int MAX_RETRIES = 1;

    /**
     * Result of a skill rewrite operation.
     */
    public static class SkillRewriteResult {
        private final String skillName;
        private final String originalContent;
        private final String rewrittenContent;
        private final List<String> consumedRecordIds;
        private int recordsCleaned;
        private final String summary;

        public SkillRewriteResult(String skillName, String originalContent, String rewrittenContent,
                                   List<String> consumedRecordIds, int recordsCleaned, String summary) {
            this.skillName = skillName;
            this.originalContent = originalContent;
            this.rewrittenContent = rewrittenContent;
            this.consumedRecordIds = consumedRecordIds;
            this.recordsCleaned = recordsCleaned;
            this.summary = summary;
        }

        public String getSkillName() {
            return skillName;
        }

        public String getOriginalContent() {
            return originalContent;
        }

        public String getRewrittenContent() {
            return rewrittenContent;
        }

        public List<String> getConsumedRecordIds() {
            return consumedRecordIds;
        }

        public int getRecordsCleaned() {
            return recordsCleaned;
        }

        public void setRecordsCleaned(int recordsCleaned) {
            this.recordsCleaned = recordsCleaned;
        }

        public String getSummary() {
            return summary;
        }
    }

    /**
     * Create SkillRewriter.
     *
     * @param llm LLM model instance
     * @param model Model name
     * @param language Language ("cn" or "en")
     */
    public SkillRewriter(Model llm, String model, String language) {
        this.llm = llm;
        this.model = model;
        this.language = language != null ? language : "cn";
    }

    /**
     * Update runtime llm/model for hot reload.
     *
     * @param llm New LLM instance
     * @param model New model name
     */
    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    /**
     * Rewrite SKILL.md by integrating evolution experiences.
     *
     * @param skillName Name of the skill to rewrite
     * @param store EvolutionStore instance for reading/writing
     * @param minScore Minimum score threshold for experiences to include
     * @param dryRun If true, only return result without writing to disk
     * @param userQuery Optional user-specified optimization direction
     * @return SkillRewriteResult on success, null if no valid experiences or rewrite not needed
     */
    public SkillRewriteResult rewrite(
            String skillName,
            EvolutionStore store,
            double minScore,
            boolean dryRun,
            String userQuery) throws Exception {

        // Load current skill content
        String skillContent = store.readSkillContent(skillName);
        if (skillContent == null || skillContent.isEmpty()) {
            Loggers.AGENT.warn("[SkillRewriter] skill '{}' has no content to rewrite", skillName);
            return null;
        }

        // Load all evolution records
        EvolutionLog evoLog = store.loadEvolutionLog(skillName);
        if (evoLog == null || evoLog.getEntries() == null || evoLog.getEntries().isEmpty()) {
            Loggers.AGENT.info("[SkillRewriter] skill '{}' has no evolution records", skillName);
            return null;
        }

        // Filter valid records by score
        List<EvolutionRecord> validRecords = new ArrayList<>();
        for (EvolutionRecord record : evoLog.getEntries()) {
            if (record.getScore() >= minScore && record.getChange() != null
                    && (record.getChange().getSkipReason() == null || record.getChange().getSkipReason().isEmpty())) {
                validRecords.add(record);
            }
        }
        if (validRecords.isEmpty()) {
            Loggers.AGENT.info("[SkillRewriter] skill '{}' has no valid records above min_score={}",
                    skillName, minScore);
            return null;
        }

        // Group records by target and section for structured prompt
        String experiencesText = formatExperiencesBySection(validRecords);

        // Build prompt
        String prompt = SkillRewriterPrompts.getPrompt(language)
                .replace("{skill_content}", skillContent)
                .replace("{experiences_by_section}", experiencesText)
                .replace("{user_query}", userQuery != null && !userQuery.isEmpty()
                        ? userQuery : ("cn".equals(language) ? "无" : "None"));

        Loggers.AGENT.info("[SkillRewriter] rewriting skill='{}' with {} valid records",
                skillName, validRecords.size());

        // Call LLM
        String raw;
        try {
            Object response = llm.invoke(
                    Collections.singletonList(new UserMessage(prompt)),
                    null, null, null, model, null, null, null, null, null);
            raw = extractContent(response);
        } catch (Exception exc) {
            Loggers.AGENT.error("[SkillRewriter] LLM call failed: {}", exc.getMessage());
            return null;
        }

        // Parse and validate output
        String rewritten = extractMarkdown(raw);
        if (rewritten == null) {
            rewritten = retryParse(raw, prompt);
        }

        if (rewritten == null) {
            Loggers.AGENT.warn("[SkillRewriter] failed to parse LLM output for skill='{}'", skillName);
            return null;
        }

        // Validate output
        if (!validateOutput(skillContent, rewritten)) {
            Loggers.AGENT.warn("[SkillRewriter] validation failed for skill='{}'", skillName);
            return null;
        }

        // Prepare result
        List<String> consumedIds = new ArrayList<>();
        for (EvolutionRecord record : validRecords) {
            consumedIds.add(record.getId());
        }
        String summary = generateSummary(validRecords, skillContent, rewritten);

        SkillRewriteResult result = new SkillRewriteResult(
                skillName, skillContent, rewritten, consumedIds, 0, summary);

        if (dryRun) {
            Loggers.AGENT.info("[SkillRewriter] dry_run completed for skill='{}'", skillName);
            return result;
        }

        // Write new content
        boolean writeSuccess = store.writeSkillContent(skillName, rewritten);
        if (!writeSuccess) {
            Loggers.AGENT.error("[SkillRewriter] failed to write skill content for '{}'", skillName);
            return null;
        }

        // Clean up consumed records
        int cleanedCount = store.deleteRecords(skillName, consumedIds);
        result.setRecordsCleaned(cleanedCount);

        Loggers.AGENT.info("[SkillRewriter] successfully rewrote skill='{}', cleaned {} records",
                skillName, cleanedCount);
        return result;
    }

    /**
     * Format records grouped by target and section for prompt.
     */
    private String formatExperiencesBySection(List<EvolutionRecord> records) {
        // Group by (target, section)
        Map<String, List<EvolutionRecord>> groups = new LinkedHashMap<>();
        for (EvolutionRecord record : records) {
            String target = record.getChange() != null && record.getChange().getTarget() != null
                    ? record.getChange().getTarget().getValue() : "unknown";
            String section = record.getChange() != null ? record.getChange().getSection() : "unknown";
            String key = target + "/" + section;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<EvolutionRecord>> entry : groups.entrySet()) {
            lines.add("### " + entry.getKey());
            lines.add("");

            // Sort by score descending
            List<EvolutionRecord> sortedRecords = new ArrayList<>(entry.getValue());
            sortedRecords.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

            for (EvolutionRecord record : sortedRecords) {
                String content = record.getChange() != null ? record.getChange().getContent() : "";
                String contentPreview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                lines.add("- **[" + record.getId() + "]** (score=" + String.format("%.2f", record.getScore())
                        + ", source=" + record.getSource() + ")");
                lines.add("  " + contentPreview);
                lines.add("");
            }
        }

        return lines.isEmpty()
                ? ("cn".equals(language) ? "无有效经验记录" : "No valid experience records")
                : String.join("\n", lines);
    }

    /**
     * Extract content from LLM response.
     */
    private String extractContent(Object response) {
        if (response == null) {
            return "";
        }
        // Check for content attribute (AssistantMessage)
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            Object content = map.get("content");
            if (content != null) {
                return String.valueOf(content);
            }
        }
        return String.valueOf(response);
    }

    /**
     * Extract markdown content from LLM output.
     */
    private static String extractMarkdown(String raw) {
        if (raw == null) {
            return null;
        }
        raw = raw.strip();
        if (raw.isEmpty()) {
            return null;
        }

        // Try to extract from ```markdown ... ``` block
        Pattern pattern = Pattern.compile("```markdown\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher match = pattern.matcher(raw);
        if (match.find()) {
            return match.group(1).strip();
        }

        // Try generic code block
        pattern = Pattern.compile("```\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        match = pattern.matcher(raw);
        if (match.find()) {
            return match.group(1).strip();
        }

        // If no code block but content looks like markdown with front-matter
        if (raw.startsWith("---")) {
            return raw;
        }

        return null;
    }

    /**
     * Retry once if output parsing failed.
     */
    private String retryParse(String brokenRaw, String originalPrompt) throws Exception {
        String preview = brokenRaw.length() > 500 ? brokenRaw.substring(0, 500) : brokenRaw;
        String retryPrompt = SkillRewriterPrompts.getRetryPrompt(language)
                .replace("{broken_preview}", preview);

        Loggers.AGENT.warn("[SkillRewriter] retrying parse after failure");
        try {
            Object response = llm.invoke(
                    Collections.singletonList(new UserMessage(retryPrompt)),
                    null, null, null, model, null, null, null, null, null);
            String retryRaw = extractContent(response);
            return extractMarkdown(retryRaw);
        } catch (Exception exc) {
            Loggers.AGENT.error("[SkillRewriter] retry LLM call failed: {}", exc.getMessage());
            return null;
        }
    }

    /**
     * Validate rewritten content.
     */
    private static boolean validateOutput(String original, String rewritten) {
        // Check front-matter preserved
        if (original.startsWith("---")) {
            if (!rewritten.startsWith("---")) {
                Loggers.AGENT.warn("[SkillRewriter] validation: front-matter missing");
                return false;
            }
        }

        // Check not too short (possible truncation)
        int origLen = original.length();
        int newLen = rewritten.length();
        if (origLen > 0 && newLen < origLen * MIN_CONTENT_RATIO) {
            Loggers.AGENT.warn("[SkillRewriter] validation: content too short ({:.1f}% of original)",
                    (newLen * 100.0 / origLen));
            return false;
        }

        // Check has some structure (headings)
        if (!rewritten.contains("#")) {
            Loggers.AGENT.warn("[SkillRewriter] validation: no headings found");
            return false;
        }

        return true;
    }

    /**
     * Generate a summary of the rewrite.
     */
    private String generateSummary(List<EvolutionRecord> records, String original, String rewritten) {
        Map<String, Integer> targetCounts = new LinkedHashMap<>();
        Map<String, Integer> sectionCounts = new LinkedHashMap<>();
        for (EvolutionRecord record : records) {
            String target = record.getChange() != null && record.getChange().getTarget() != null
                    ? record.getChange().getTarget().getValue() : "unknown";
            targetCounts.merge(target, 1, Integer::sum);
            String section = record.getChange() != null ? record.getChange().getSection() : "unknown";
            sectionCounts.merge(section, 1, Integer::sum);
        }

        int origLines = original.split("\n").length;
        int newLines = rewritten.split("\n").length;

        List<String> parts = new ArrayList<>();
        if ("cn".equals(language)) {
            parts.add("整合了 " + records.size() + " 条经验记录");
            parts.add("目标分布: " + formatCounts(targetCounts));
            parts.add("章节分布: " + formatCounts(sectionCounts));
            parts.add("行数变化: " + origLines + " -> " + newLines);
        } else {
            parts.add("Integrated " + records.size() + " experience records");
            parts.add("Target distribution: " + formatCounts(targetCounts));
            parts.add("Section distribution: " + formatCounts(sectionCounts));
            parts.add("Line count: " + origLines + " -> " + newLines);
        }

        return String.join("; ", parts);
    }

    private static String formatCounts(Map<String, Integer> counts) {
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            items.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", items);
    }
}