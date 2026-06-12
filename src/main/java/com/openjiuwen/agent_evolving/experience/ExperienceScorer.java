/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-based experience scorer and maintainer.
 *
 * <p>Mirrors Python's {@code ExperienceScorer} and module scoring helpers in
 * {@code openjiuwen/agent_evolving/experience/scorer.py}.</p>
 */
public class ExperienceScorer {

    public static final double W_E = 0.5d;
    public static final double W_U = 0.3d;
    public static final double W_F = 0.2d;
    public static final int FRESHNESS_HALF_LIFE_DAYS = 90;
    public static final double STALE_VERSION_PENALTY = 0.7d;
    public static final LlmInvokePolicy EVALUATE_LLM_POLICY = new LlmInvokePolicy(60.0d, 120.0d, 2);
    public static final LlmInvokePolicy SIMPLIFY_LLM_POLICY = new LlmInvokePolicy(150.0d, 300.0d, 2);

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CODE_BLOCK_START = Pattern.compile("^```(?:json)?\\s*", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_END = Pattern.compile("```\\s*$", Pattern.MULTILINE);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[[\\s\\S]*\\]");

    private LlmClient llm;
    private String model;
    private final String language;
    private final LlmInvokePolicy evaluateLlmPolicy;
    private final LlmInvokePolicy simplifyLlmPolicy;

    public ExperienceScorer(LlmClient llm, String model) {
        this(llm, model, "cn", EVALUATE_LLM_POLICY, SIMPLIFY_LLM_POLICY);
    }

    public ExperienceScorer(
            LlmClient llm,
            String model,
            String language,
            LlmInvokePolicy evaluateLlmPolicy,
            LlmInvokePolicy simplifyLlmPolicy
    ) {
        this.llm = llm;
        this.model = model;
        this.language = language == null ? "cn" : language;
        this.evaluateLlmPolicy = evaluateLlmPolicy == null ? EVALUATE_LLM_POLICY : evaluateLlmPolicy;
        this.simplifyLlmPolicy = simplifyLlmPolicy == null ? SIMPLIFY_LLM_POLICY : simplifyLlmPolicy;
    }

    public static double calcEffectiveness(UsageStats stats) {
        UsageStats safeStats = stats == null ? new UsageStats() : stats;
        int total = safeStats.getTimesPositive() + safeStats.getTimesNegative();
        if (total == 0) {
            return 0.5d;
        }
        return (safeStats.getTimesPositive() + 1.0d) / (total + 2.0d);
    }

    public static double calcUtilization(UsageStats stats) {
        UsageStats safeStats = stats == null ? new UsageStats() : stats;
        if (safeStats.getTimesPresented() == 0) {
            return 0.5d;
        }
        return safeStats.getTimesUsed() / (double) safeStats.getTimesPresented();
    }

    public static double calcFreshness(EvolutionRecord record) {
        return calcFreshness(record, null);
    }

    public static double calcFreshness(EvolutionRecord record, String currentSkillVersion) {
        if (record == null || record.getTimestamp() == null || record.getTimestamp().isEmpty()) {
            return 0.5d;
        }
        Instant recordInstant;
        try {
            recordInstant = parsePythonIsoInstant(record.getTimestamp());
        } catch (DateTimeException | IllegalArgumentException exception) {
            return 0.5d;
        }

        long daysOld = ChronoUnit.DAYS.between(recordInstant, Instant.now());
        double decayFactor = 0.5d * Math.pow(2.0d, -daysOld / (double) FRESHNESS_HALF_LIFE_DAYS);
        double freshness = 0.5d + decayFactor;
        if (currentSkillVersion != null
                && record.getSkillVersion() != null
                && !record.getSkillVersion().equals(currentSkillVersion)) {
            freshness *= STALE_VERSION_PENALTY;
        }
        return Math.max(0.0d, Math.min(1.0d, freshness));
    }

    public static double calcScore(EvolutionRecord record) {
        return calcScore(record, null);
    }

    public static double calcScore(EvolutionRecord record, String currentSkillVersion) {
        UsageStats stats = record == null || record.getUsageStats() == null ? new UsageStats() : record.getUsageStats();
        double e = calcEffectiveness(stats);
        double u = calcUtilization(stats);
        double f = calcFreshness(record, currentSkillVersion);
        return W_E * e + W_U * u + W_F * f;
    }

    public static double updateScore(EvolutionRecord record, Map<String, Object> evalResult) {
        return updateScore(record, evalResult, null);
    }

    public static double updateScore(
            EvolutionRecord record,
            Map<String, Object> evalResult,
            String currentSkillVersion
    ) {
        if (record.getUsageStats() == null) {
            record.setUsageStats(new UsageStats());
        }
        UsageStats stats = record.getUsageStats();
        Map<String, Object> result = evalResult == null ? Map.of() : evalResult;
        if (Boolean.TRUE.equals(result.get("used"))) {
            stats.setTimesUsed(stats.getTimesUsed() + 1);
        }
        if (Boolean.TRUE.equals(result.get("positive"))) {
            stats.setTimesPositive(stats.getTimesPositive() + 1);
        }
        if (Boolean.TRUE.equals(result.get("negative"))) {
            stats.setTimesNegative(stats.getTimesNegative() + 1);
        }
        stats.setLastEvaluatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
        record.setScore(calcScore(record, currentSkillVersion));
        return record.getScore();
    }

    public LlmInvokePolicy getEvaluateLlmPolicy() {
        return evaluateLlmPolicy;
    }

    public LlmInvokePolicy getSimplifyLlmPolicy() {
        return simplifyLlmPolicy;
    }

    public void updateLlm(LlmClient llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    public CompletionStage<List<Map<String, Object>>> evaluate(
            String conversationSnippet,
            List<EvolutionRecord> presentedRecords
    ) {
        if (presentedRecords == null || presentedRecords.isEmpty()) {
            return CompletableFuture.completedFuture(List.<Map<String, Object>>of());
        }
        String prompt = buildEvaluatePrompt(
                formatPresentedExperiences(presentedRecords),
                truncate(conversationSnippet, 4000)
        );
        return invokeTextWithRetry(
                prompt,
                evaluateLlmPolicy,
                text -> parseLlmJson(text) != null
        ).thenApply(raw -> {
            List<Map<String, Object>> results = parseLlmJson(raw);
            if (results == null) {
                LOGGER.warning("[ExperienceScorer] evaluate: failed to parse LLM response");
                return List.<Map<String, Object>>of();
            }
            return results;
        }).exceptionally(failure -> {
            LOGGER.error("[ExperienceScorer] evaluate LLM call failed: {}", unwrap(failure).getMessage());
            return List.<Map<String, Object>>of();
        });
    }

    public CompletionStage<List<Map<String, Object>>> simplify(
            String skillName,
            String skillSummary,
            List<EvolutionRecord> records
    ) {
        return simplify(skillName, skillSummary, records, null);
    }

    public CompletionStage<List<Map<String, Object>>> simplify(
            String skillName,
            String skillSummary,
            List<EvolutionRecord> records,
            String userIntent
    ) {
        if (records == null || records.isEmpty()) {
            LOGGER.info("[ExperienceScorer] simplify skipped for skill={}: no records", skillName);
            return CompletableFuture.completedFuture(List.<Map<String, Object>>of());
        }
        String prompt = buildSimplifyPrompt(skillName, truncate(skillSummary, 1000), formatScoredExperiences(records));
        if (userIntent != null && !userIntent.isEmpty()) {
            prompt += "\n\n**用户意图**: " + userIntent;
        }
        LOGGER.info(
                "[ExperienceScorer] simplify LLM call start: skill={} records={} prompt_chars={} attempt_timeout={} "
                        + "total_budget={} max_attempts={}",
                skillName,
                records.size(),
                prompt.length(),
                simplifyLlmPolicy.attemptTimeoutSecs(),
                simplifyLlmPolicy.totalBudgetSecs(),
                simplifyLlmPolicy.maxAttempts()
        );
        return invokeTextWithRetry(
                prompt,
                simplifyLlmPolicy,
                text -> parseLlmJson(text) != null
        ).thenApply(raw -> {
            List<Map<String, Object>> actions = parseLlmJson(raw);
            if (actions == null) {
                LOGGER.warning(
                        "[ExperienceScorer] simplify: failed to parse LLM response for skill={} response_chars={}",
                        skillName,
                        raw == null ? 0 : raw.length()
                );
                return List.<Map<String, Object>>of();
            }
            LOGGER.info("[ExperienceScorer] simplify parsed actions: skill={} actions={}", skillName, actions.size());
            return actions;
        }).exceptionally(failure -> {
            LOGGER.error("[ExperienceScorer] simplify LLM call failed: skill={} error={}",
                    skillName,
                    unwrap(failure).getMessage());
            return List.<Map<String, Object>>of();
        });
    }

    public static String formatPresentedExperiences(List<EvolutionRecord> records) {
        List<String> lines = new ArrayList<>();
        for (EvolutionRecord record : records == null ? List.<EvolutionRecord>of() : records) {
            EvolutionPatch change = record == null ? null : record.getChange();
            String content = change == null ? "" : truncate(change.getContent(), 200);
            lines.add("[" + (record == null ? null : record.getId()) + "] " + content);
        }
        return String.join("\n", lines);
    }

    public static String formatScoredExperiences(List<EvolutionRecord> records) {
        List<String> lines = new ArrayList<>();
        for (EvolutionRecord record : records == null ? List.<EvolutionRecord>of() : records) {
            UsageStats stats = record.getUsageStats() == null ? new UsageStats() : record.getUsageStats();
            EvolutionPatch change = record.getChange();
            String content = change == null ? "" : truncate(change.getContent(), 150);
            lines.add(String.format(
                    java.util.Locale.ROOT,
                    "[%s] score=%.2f | presented=%d used=%d | %s",
                    record.getId(),
                    record.getScore(),
                    stats.getTimesPresented(),
                    stats.getTimesUsed(),
                    content
            ));
        }
        return String.join("\n", lines);
    }

    public static List<Map<String, Object>> parseLlmJson(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        cleaned = CODE_BLOCK_START.matcher(cleaned).replaceAll("");
        cleaned = CODE_BLOCK_END.matcher(cleaned).replaceAll("");
        cleaned = LINE_COMMENT.matcher(cleaned).replaceAll("");
        cleaned = TRAILING_COMMA.matcher(cleaned).replaceAll("$1").strip();
        List<Map<String, Object>> parsed = parseJsonListOrDict(cleaned);
        if (parsed != null) {
            return parsed;
        }
        Matcher matcher = JSON_ARRAY.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }
        return parseJsonListOrDict(matcher.group(0));
    }

    private CompletionStage<String> invokeTextWithRetry(
            String prompt,
            LlmInvokePolicy policy,
            Predicate<String> isResultUsable
    ) {
        int maxAttempts = Math.max(policy.maxAttempts(), 1);
        return invokeTextAttempt(prompt, policy, isResultUsable, maxAttempts, 1);
    }

    private CompletionStage<String> invokeTextAttempt(
            String prompt,
            LlmInvokePolicy policy,
            Predicate<String> isResultUsable,
            int maxAttempts,
            int attempt
    ) {
        if (llm == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM client is not configured"));
        }
        return llm.invoke(model, prompt, policy.attemptTimeoutSecs())
                .thenCompose(raw -> {
                    String text = raw == null ? "" : raw;
                    boolean emptyRetry = policy.retryEmptyResponse() && text.strip().isEmpty();
                    boolean unusable = isResultUsable != null && !isResultUsable.test(text);
                    if ((emptyRetry || unusable) && attempt < maxAttempts) {
                        return invokeTextAttempt(prompt, policy, isResultUsable, maxAttempts, attempt + 1);
                    }
                    if (emptyRetry || unusable) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("unusable_response"));
                    }
                    return CompletableFuture.completedFuture(text);
                });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseJsonListOrDict(String raw) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        return null;
                    }
                    result.add((Map<String, Object>) map);
                }
                return result;
            }
            if (parsed instanceof Map<?, ?> map) {
                return List.of((Map<String, Object>) map);
            }
            return null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Instant parsePythonIsoInstant(String timestamp) {
        String normalized = timestamp.replace("Z", "+00:00");
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeException ignored) {
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
        }
    }

    private String buildEvaluatePrompt(String presentedExperiences, String conversationSnippet) {
        if ("en".equalsIgnoreCase(language)) {
            return "You are an experience evaluation expert. Based on the conversation snippet, evaluate whether "
                    + "the previously presented experiences were effectively used by the Agent.\n\n"
                    + "## Experiences Presented to Agent\n"
                    + presentedExperiences
                    + "\n\n## Conversation Snippet (after presenting experiences)\n"
                    + conversationSnippet
                    + "\n\n## Evaluation Task\n"
                    + "For each presented experience, determine whether it was used, produced positive effects, "
                    + "or produced negative effects.\n\n"
                    + "Output a JSON array only, one object per experience, with record_id, used, positive, "
                    + "negative, and reason.";
        }
        if (!"en".equalsIgnoreCase(language)) {
            return "你是一个经验评估专家。根据对话片段，评估之前展示给 Agent 的经验是否被有效使用。\n\n"
                    + "## 展示给 Agent 的经验\n"
                    + presentedExperiences
                    + "\n\n## 对话片段（展示经验之后的部分）\n"
                    + conversationSnippet
                    + "\n\n## 评估任务\n"
                    + "对于每条展示的经验，判断该经验是否被 Agent 理解和采纳、是否产生积极效果、是否产生消极效果。\n\n"
                    + "只输出 JSON 数组，每个对象包含 record_id、used、positive、negative 和 reason。";
        }
        if ("en".equalsIgnoreCase("")) {
            return "";
        }
        return "## Experiences Presented to Agent\n"
                + presentedExperiences
                + "\n\n## Conversation Snippet\n"
                + conversationSnippet
                + "\n\nOutput JSON only.";
    }

    private String buildSimplifyPrompt(String skillName, String skillSummary, String scoredExperiences) {
        if ("en".equalsIgnoreCase(language)) {
            return "You are an experience library maintenance expert. Based on current experience scores and usage, "
                    + "generate organization suggestions.\n\n"
                    + "## Skill Name\n"
                    + skillName
                    + "\n\n## Skill Summary\n"
                    + skillSummary
                    + "\n\n## Current Experience List (sorted by score)\n"
                    + scoredExperiences
                    + "\n\nOutput a JSON array only with action, record_id, reason, merge_remove_ids, and new_content "
                    + "when relevant.";
        }
        if (!"en".equalsIgnoreCase(language)) {
            return "你是一个经验库维护专家。根据当前经验的评分和使用情况，生成整理建议。\n\n"
                    + "## Skill 名称\n"
                    + skillName
                    + "\n\n## Skill 摘要\n"
                    + skillSummary
                    + "\n\n## 当前经验列表（按分数排序）\n"
                    + scoredExperiences
                    + "\n\n只输出 JSON 数组，每个对象包含 action、record_id、reason，并在 MERGE 或 REFINE 时包含相关字段。";
        }
        String heading = "en".equals(language) ? "Skill Name" : "Skill 名称";
        return "## " + heading + "\n"
                + skillName
                + "\n\n## Skill Summary\n"
                + skillSummary
                + "\n\n## Current Experience List\n"
                + scoredExperiences
                + "\n\nOutput JSON only.";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    /**
     * Policy for a single evolution-layer LLM invocation.
     */
    public record LlmInvokePolicy(
            double attemptTimeoutSecs,
            double totalBudgetSecs,
            int maxAttempts,
            double backoffBaseSecs,
            boolean retryEmptyResponse
    ) {
        public LlmInvokePolicy(double attemptTimeoutSecs, double totalBudgetSecs, int maxAttempts) {
            this(attemptTimeoutSecs, totalBudgetSecs, maxAttempts, 1.0d, true);
        }
    }

    /**
     * Typed LLM client port used by this scorer.
     */
    @FunctionalInterface
    public interface LlmClient {
        CompletionStage<String> invoke(String model, String prompt, double timeoutSecs);
    }
}
