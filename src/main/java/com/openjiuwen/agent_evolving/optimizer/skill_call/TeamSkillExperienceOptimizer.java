/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.experience.EvolutionContext;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.operator.Operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-based patch generation for team skill evolution.
 *
 * <p>Mirrors Python's {@code TeamSkillExperienceOptimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
 */
public class TeamSkillExperienceOptimizer extends BaseOptimizer {

    public static final LlmResilience.LLMInvokePolicy TEAM_SKILL_RECORD_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(120, 420, 3, 1.0, true);
    static final int PATCH_RETRY_SKILL_CONTENT_CHARS = 3000;
    static final int PATCH_RETRY_TRAJECTORY_CHARS = 6000;
    static final int TRAJECTORY_ISSUES_RETRY_CHARS = 2000;
    static final int USER_INTENT_RETRY_CHARS = 500;
    static final int SUMMARY_RETRY_CHARS = 200;
    static final int TEAM_SKILL_CONTENT_MAX_CHARS = 6000;
    static final int TEAM_EVOLUTION_PREVIEW_CHARS = 200;
    static final int TEAM_EVOLUTION_MAX_RECORDS = 6;
    static final int TEAM_RETRY_PARSE_TIMEOUT_SECS = 20;
    static final Map<String, Double> TEAM_INITIAL_SCORE_BY_SIGNAL = Map.of(
            "trajectory_issue", 0.65,
            "user_intent", 0.70,
            "team_skill_mixed", 0.68
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK_RE = Pattern.compile("```(?:json)?\\s*\\n([\\s\\S]*?)```");
    private static final String TEAM_TRAJECTORY_ISSUES_KEY = "trajectory_issues";
    private static final String TEAM_SKILL_CONTENT_KEY = "skill_content";

    private Model llm;
    private String model;
    private final String language;
    private final String debugDir;
    private final LlmResilience.LLMInvokePolicy recordLlmPolicy;
    private final EvolutionStore evolutionStore;
    private Map<String, EvolutionContext> onlineContexts = new LinkedHashMap<>();

    public TeamSkillExperienceOptimizer(Model llm, String model) {
        this(llm, model, "cn", null, TEAM_SKILL_RECORD_LLM_POLICY, null);
    }

    public TeamSkillExperienceOptimizer(Model llm, String model, String language) {
        this(llm, model, language, null, TEAM_SKILL_RECORD_LLM_POLICY, null);
    }

    public TeamSkillExperienceOptimizer(
            Model llm,
            String model,
            String language,
            String debugDir,
            LlmResilience.LLMInvokePolicy recordLlmPolicy,
            EvolutionStore evolutionStore
    ) {
        this.domain = "team_skill_experience";
        this.llm = llm;
        this.model = model;
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.debugDir = debugDir;
        this.recordLlmPolicy = recordLlmPolicy == null ? TEAM_SKILL_RECORD_LLM_POLICY : recordLlmPolicy;
        this.evolutionStore = evolutionStore;
    }

    @Override
    public List<String> defaultTargets() {
        return List.of(Protocols.EXPERIENCES_TARGET);
    }

    @Override
    public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
        this.onlineContexts = extractOnlineContexts(config);
        return super.bind(operators, targets, config);
    }

    @Override
    protected CompletionStage<Void> doBackward(List<EvolutionSignal> signals) {
        List<Trajectory> trajectories = getTrajectories();
        Trajectory defaultTrajectory = trajectories.isEmpty()
                ? defaultTrajectory()
                : trajectories.get(trajectories.size() - 1);
        for (Map.Entry<String, Operator> entry : operators.entrySet()) {
            String opId = entry.getKey();
            String skillName = removePrefix(opId, "skill_experience_");
            List<EvolutionSignal> skillSignals = selectedSignals.stream()
                    .filter(signal -> Objects.equals(signal.getSkillName(), skillName) || isBlank(signal.getSkillName()))
                    .toList();
            if (skillSignals.isEmpty()) {
                continue;
            }
            EvolutionContext ctx = buildEvolutionContext(skillName, entry.getValue(), skillSignals, defaultTrajectory);
            List<EvolutionRecord> generated = generateRecords(ctx);
            if (generated.isEmpty()) {
                continue;
            }
            List<EvolutionRecord> existing = existingRecords(opId);
            existing.addAll(generated);
            parameters.get(opId).setGradient(Protocols.EXPERIENCES_TARGET, existing);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected Updates doStep() {
        Updates updates = new Updates();
        for (String opId : parameters.keySet()) {
            Object records = parameters.get(opId).getGradient(Protocols.EXPERIENCES_TARGET);
            if (records instanceof List<?> list && !list.isEmpty()) {
                updates.put(opId, Protocols.EXPERIENCES_TARGET, records);
            }
        }
        return updates;
    }

    public String getLanguage() {
        return language;
    }

    Model getLlm() {
        return llm;
    }

    String getModelName() {
        return model;
    }

    public LlmResilience.LLMInvokePolicy getRecordLlmPolicy() {
        return recordLlmPolicy;
    }

    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    /**
     * Generate zero or more team evolution records from aggregated context.
     *
     * @param ctx evolution context
     * @return generated records
     */
    public List<EvolutionRecord> generateRecords(EvolutionContext ctx) {
        if (ctx == null || ctx.getSignals().isEmpty()) {
            return List.of();
        }

        Trajectory trajectory = ctx.getTrajectory() instanceof Trajectory current ? current : defaultTrajectory();
        if (hasNonJavaTrajectorySteps(trajectory)) {
            return generateLegacyRecords(ctx, trajectory);
        }

        String trajectorySummary = buildTeamTrajectorySummary(trajectory);
        String prompt = buildAggregatedPrompt(
                ctx,
                trajectorySummary,
                signalsToJson(ctx.getSignals(), true),
                summarizeSkillContent(ctx.getSkillContent(), TEAM_SKILL_CONTENT_MAX_CHARS),
                summarizeExistingEvolutions(ctx.getExistingDescRecords(), language),
                summarizeExistingEvolutions(ctx.getExistingBodyRecords(), language),
                summarizeExistingEvolutions(ctx.getExistingScriptRecords(), language),
                defaultIfBlank(ctx.getUserQuery(), defaultNoneText())
        );
        String retryPrompt = buildAggregatedPrompt(
                ctx,
                isBlank(trajectorySummary)
                        ? defaultTrajectorySummaryText()
                        : limit(trajectorySummary, PATCH_RETRY_TRAJECTORY_CHARS),
                signalsToJson(ctx.getSignals(), false),
                defaultIfBlank(summarizeSkillContent(ctx.getSkillContent(), 2500), defaultNoneText()),
                shortenExistingEvolutionsSummary(summarizeExistingEvolutions(ctx.getExistingDescRecords(), language), 2),
                shortenExistingEvolutionsSummary(summarizeExistingEvolutions(ctx.getExistingBodyRecords(), language), 2),
                shortenExistingEvolutionsSummary(summarizeExistingEvolutions(ctx.getExistingScriptRecords(), language), 1),
                shortUserQuery(ctx.getUserQuery())
        );

        LlmResilience.InvokeResult invokeResult;
        try {
            invokeResult = LlmResilience.invokeTextWithRetryAndPrompt(
                    llm,
                    model,
                    prompt,
                    recordLlmPolicy,
                    retryPrompt,
                    null,
                    null
            );
        } catch (Exception exc) {
            throw propagate(exc);
        }

        List<ParsedExperienceDraft> drafts = generateDraftsWithRepairs(invokeResult.raw(), invokeResult.promptUsed());
        if (drafts == null) {
            return List.of();
        }
        return recordsFromDrafts(ctx, drafts);
    }

    public EvolutionRecord generateUserPatch(Trajectory trajectory, String skillName, String userIntent) {
        String summary = buildTeamTrajectorySummary(trajectory == null ? defaultTrajectory() : trajectory);
        String rolesSummary = summary.toLowerCase().contains("role") ? limit(summary, SUMMARY_RETRY_CHARS) : "N/A";
        String workflowSummary = summary.toLowerCase().contains("workflow") ? "Present in trajectory" : "N/A";
        String skillContent = loadSkillContent(skillName);
        String existingEvolutions = loadExistingEvolutionsSummary(skillName);
        String template = SkillCallTemplates.USER_PATCH_PROMPT.getOrDefault(
                language,
                SkillCallTemplates.USER_PATCH_PROMPT_EN
        );
        String prompt = template
                .replace("{skill_name}", nullToEmpty(skillName))
                .replace("{description}", "team-skill")
                .replace("{roles_summary}", rolesSummary)
                .replace("{workflow_summary}", workflowSummary)
                .replace("{skill_content}", skillContent)
                .replace("{existing_evolutions}", existingEvolutions)
                .replace("{user_intent}", nullToEmpty(userIntent));
        String retryPrompt = template
                .replace("{skill_name}", nullToEmpty(skillName))
                .replace("{description}", "team-skill")
                .replace("{roles_summary}", limit(rolesSummary, SUMMARY_RETRY_CHARS))
                .replace("{workflow_summary}", limit(workflowSummary, SUMMARY_RETRY_CHARS))
                .replace("{skill_content}", summarizeSkillContent(skillContent, 2500))
                .replace("{existing_evolutions}", shortenExistingEvolutionsSummary(existingEvolutions, 2))
                .replace("{user_intent}", limit(nullToEmpty(userIntent), USER_INTENT_RETRY_CHARS));
        String raw = callLlm(prompt, retryPrompt, recordLlmPolicy, text -> parseJson(text) instanceof Map<?, ?>);
        PatchResponse parsed = parsePatchResponse(raw);
        if (parsed.data() == null) {
            throw new IllegalArgumentException("TeamSkillExperienceOptimizer response could not be parsed");
        }
        Map<String, Object> data = parsed.data();
        if (!boolValue(data.get("need_patch"), true) || "skip".equals(data.get("action"))) {
            return null;
        }
        String section = stringValue(data.getOrDefault("section", "Instructions"));
        String content = stringValue(data.getOrDefault("content", ""));
        if (content.strip().isEmpty()) {
            throw new IllegalArgumentException("TeamSkill user patch response contained empty content");
        }
        return EvolutionRecord.make(
                "team_skill_user_patch",
                "User intent: " + limit(nullToEmpty(userIntent), 200),
                EvolutionPatch.builder()
                        .section(section)
                        .action("append")
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build(),
                0.6,
                null,
                ExperienceDraftParser.normalizeSummary(data.get("summary"))
        );
    }

    public EvolutionRecord generateTrajectoryPatch(
            Trajectory trajectory,
            String skillName,
            String currentSkillContent,
            List<Map<String, Object>> trajectoryIssues
    ) {
        String summary = buildTeamTrajectorySummary(trajectory == null ? defaultTrajectory() : trajectory);
        String issuesText = toJson(trajectoryIssues == null ? List.of() : trajectoryIssues, true);
        String existingEvolutions = loadExistingEvolutionsSummary(skillName);
        String template = SkillCallTemplates.TRAJECTORY_PATCH_PROMPT.getOrDefault(
                language,
                SkillCallTemplates.TRAJECTORY_PATCH_PROMPT_EN
        );
        String prompt = template
                .replace("{skill_content}", limit(nullToEmpty(currentSkillContent), 15000))
                .replace("{existing_evolutions}", existingEvolutions)
                .replace("{trajectory_summary}", summary)
                .replace("{trajectory_issues}", limit(issuesText, 5000));
        String retryPrompt = template
                .replace("{skill_content}", limit(nullToEmpty(currentSkillContent), PATCH_RETRY_SKILL_CONTENT_CHARS))
                .replace("{existing_evolutions}", shortenExistingEvolutionsSummary(existingEvolutions, 2))
                .replace("{trajectory_summary}", limit(summary, PATCH_RETRY_TRAJECTORY_CHARS))
                .replace("{trajectory_issues}", limit(issuesText, TRAJECTORY_ISSUES_RETRY_CHARS));
        String raw = callLlm(prompt, retryPrompt, recordLlmPolicy, text -> parseJson(text) instanceof Map<?, ?>);
        PatchResponse parsed = parsePatchResponse(raw);
        if (parsed.data() == null) {
            throw new IllegalArgumentException("TeamSkillExperienceOptimizer response could not be parsed");
        }
        Map<String, Object> data = parsed.data();
        if (!boolValue(data.get("need_patch"), false)) {
            return null;
        }
        String section = stringValue(data.getOrDefault("section", "Workflow"));
        String content = stringValue(data.getOrDefault("content", ""));
        if (content.strip().isEmpty()) {
            throw new IllegalArgumentException("TeamSkill trajectory patch response contained empty content");
        }
        return EvolutionRecord.make(
                "team_skill_trajectory_patch",
                "Trajectory issues: " + limit(issuesText, 200),
                EvolutionPatch.builder()
                        .section(section)
                        .action("append")
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build(),
                0.6,
                null,
                ExperienceDraftParser.normalizeSummary(data.get("summary"))
        );
    }

    public String regenerateBody(
            String skillName,
            String currentBody,
            List<EvolutionRecord> evolutionRecords,
            String userIntent
    ) {
        StringBuilder evolutionSummary = new StringBuilder();
        List<EvolutionRecord> records = evolutionRecords == null ? List.of() : evolutionRecords;
        for (int i = 0; i < Math.min(records.size(), 20); i++) {
            EvolutionRecord record = records.get(i);
            EvolutionPatch change = record.getChange();
            evolutionSummary.append("- [")
                    .append(record.getId())
                    .append("] ")
                    .append(change == null ? "?" : change.getSection())
                    .append(": ")
                    .append(limit(change == null ? "" : change.getContent(), 200))
                    .append('\n');
        }
        String prompt = "Regenerate team skill body for " + skillName + "\n\n"
                + limit(nullToEmpty(currentBody), 8000)
                + "\n\nEvolutions:\n" + (evolutionSummary.isEmpty() ? "(no evolutions)" : evolutionSummary)
                + (isBlank(userIntent) ? "" : "\n\nUser intent:\n" + userIntent)
                + "\n\nOutput Markdown body only.";
        String body = callLlm(prompt, null, null, null).strip();
        return body.length() < 50 ? null : body;
    }

    public RetryParseResult retryParse(String brokenRaw, String originalPrompt) {
        return retryParse(brokenRaw, originalPrompt, 1, "");
    }

    public RetryParseResult retryParse(
            String brokenRaw,
            String originalPrompt,
            int attemptNumber,
            String parseError
    ) {
        RetryParseDraftsResult result = retryParseDrafts(brokenRaw, originalPrompt, attemptNumber, parseError);
        List<EvolutionPatch> patches = null;
        if (result.drafts() != null) {
            patches = result.drafts().stream().map(ParsedExperienceDraft::getPatch).toList();
        }
        return new RetryParseResult(patches, result.retryRaw());
    }

    public RetryParseDraftsResult retryParseDrafts(
            String brokenRaw,
            String originalPrompt,
            int attemptNumber,
            String parseError
    ) {
        String raw = brokenRaw == null ? "" : brokenRaw;
        String retryPrompt = buildRetryPrompt(raw, originalPrompt, attemptNumber, parseError);
        if (retryPrompt == null) {
            return new RetryParseDraftsResult(null, raw);
        }
        String retryRaw;
        try {
            AssistantMessage response = llm.invoke(
                            List.of(new UserMessage(retryPrompt)),
                            ModelInvokeOptions.builder()
                                    .model(model)
                                    .temperature(0.1f)
                                    .timeout((float) TEAM_RETRY_PARSE_TIMEOUT_SECS)
                                    .build()
                    )
                    .toCompletableFuture()
                    .get(TEAM_RETRY_PARSE_TIMEOUT_SECS, TimeUnit.SECONDS);
            retryRaw = responseToText(response);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            return new RetryParseDraftsResult(null, "");
        } catch (ExecutionException | CompletionException | java.util.concurrent.TimeoutException exc) {
            return new RetryParseDraftsResult(null, "");
        }

        ExperienceDraftParser.DraftsWithError parsed = ExperienceDraftParser.parseExperienceDraftsWithError(
                retryRaw,
                TeamSkillExperienceOptimizer::extractJsonWithError
        );
        if (parsed.drafts() == null) {
            return new RetryParseDraftsResult(null, retryRaw);
        }
        return new RetryParseDraftsResult(parsed.drafts(), retryRaw);
    }

    static Object parseJson(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        Matcher blockMatcher = JSON_BLOCK_RE.matcher(raw);
        if (blockMatcher.find()) {
            candidates.add(blockMatcher.group(1).strip());
        }
        candidates.add(raw.strip());
        candidates.add(fixJsonText(raw));
        String arrayJson = extractBalancedJson(raw, '[', ']');
        if (arrayJson != null) {
            candidates.add(arrayJson);
            candidates.add(fixJsonText(arrayJson));
        }
        String objectJson = extractBalancedJson(raw, '{', '}');
        if (objectJson != null) {
            candidates.add(objectJson);
            candidates.add(fixJsonText(objectJson));
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (isBlank(candidate) || !seen.add(candidate)) {
                continue;
            }
            ParseAttempt parsed = tryParse(candidate);
            if (parsed.data() instanceof Map<?, ?> || parsed.data() instanceof List<?>) {
                return parsed.data();
            }
        }
        return null;
    }

    static ExperienceDraftParser.JsonExtractionResult extractJsonWithError(String raw) {
        if (isBlank(raw)) {
            return new ExperienceDraftParser.JsonExtractionResult(null, "empty response");
        }
        ParseAttempt direct = tryParse(raw);
        if (direct.data() != null) {
            return new ExperienceDraftParser.JsonExtractionResult(direct.data(), "");
        }

        String lastError = isBlank(direct.error()) ? "unknown" : direct.error();
        String cleaned = raw.strip();
        for (String pattern : List.of("\\[[\\s\\S]*\\]", "\\{[\\s\\S]*\\}")) {
            Matcher matcher = Pattern.compile(pattern).matcher(cleaned);
            if (!matcher.find()) {
                continue;
            }
            ParseAttempt candidate = tryParse(matcher.group(0));
            if (candidate.data() != null) {
                return new ExperienceDraftParser.JsonExtractionResult(candidate.data(), "");
            }
            if (!isBlank(candidate.error())) {
                lastError = candidate.error();
            }
        }
        return new ExperienceDraftParser.JsonExtractionResult(null, lastError);
    }

    static boolean looksTruncated(String text) {
        String value = text == null ? "" : text;
        long opens = value.chars().filter(ch -> ch == '{' || ch == '[').count();
        long closes = value.chars().filter(ch -> ch == '}' || ch == ']').count();
        return opens > closes + 1;
    }

    static String buildTeamTrajectorySummary(Trajectory trajectory) {
        int toolBudget = 20000;
        int llmBudget = 10000;
        Set<String> keyTools = Set.of("spawn_member", "create_task", "build_team", "view_task", "send_message");
        List<String> toolLines = new ArrayList<>();
        List<String> llmLines = new ArrayList<>();
        int toolCount = 0;
        int llmCount = 0;
        List<TrajectoryStep> steps = trajectory == null || trajectory.getSteps() == null ? List.of() : trajectory.getSteps();
        for (TrajectoryStep step : steps) {
            if ("tool".equals(step.getKind()) && step.getDetail() != null) {
                toolCount++;
                String toolName = toolName(step.getDetail());
                boolean isKey = keyTools.contains(toolName);
                int argsLimit = isKey ? 500 : 150;
                int resultLimit = isKey ? 500 : 200;
                toolLines.add("[Tool:" + toolName + "] args="
                        + limit(String.valueOf(callArgs(step.getDetail())), argsLimit)
                        + " result="
                        + limit(String.valueOf(callResult(step.getDetail())), resultLimit));
            } else if ("llm".equals(step.getKind()) && step.getDetail() != null) {
                llmCount++;
                Object response = llmResponse(step.getDetail());
                if (response != null) {
                    llmLines.add("[LLM] " + limit(String.valueOf(response), 300));
                }
            }
        }
        String toolSection = limit(String.join("\n", toolLines), toolBudget);
        String llmSection = limit(String.join("\n", llmLines), llmBudget);
        return "### Tool Calls (" + toolCount + ")\n" + toolSection
                + "\n\n### LLM Responses (" + llmCount + ")\n" + llmSection;
    }

    static List<Map<String, Object>> getTeamTrajectoryIssues(EvolutionSignal signal) {
        Object issues = signal == null || signal.getContext() == null
                ? null
                : signal.getContext().get(TEAM_TRAJECTORY_ISSUES_KEY);
        if (!(issues instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                normalized.add(stringMap(map));
            }
        }
        return normalized;
    }

    static String getTeamSignalSkillContent(EvolutionSignal signal) {
        Object content = signal == null || signal.getContext() == null
                ? null
                : signal.getContext().get(TEAM_SKILL_CONTENT_KEY);
        return content == null ? null : String.valueOf(content);
    }

    private List<EvolutionRecord> generateLegacyRecords(EvolutionContext ctx, Trajectory trajectory) {
        List<EvolutionRecord> generated = new ArrayList<>();
        for (EvolutionSignal signal : ctx.getSignals()) {
            EvolutionRecord record;
            if ("user_intent".equals(signal.getSignalType())) {
                record = generateUserPatch(trajectory, ctx.getSkillName(), defaultIfBlank(signal.getExcerpt(), ctx.getUserQuery()));
            } else {
                record = generateTrajectoryPatch(
                        trajectory,
                        ctx.getSkillName(),
                        defaultIfBlank(getTeamSignalSkillContent(signal), ctx.getSkillContent()),
                        getTeamTrajectoryIssues(signal)
                );
            }
            if (record != null) {
                generated.add(record);
            }
        }
        return generated;
    }

    private EvolutionContext buildEvolutionContext(
            String skillName,
            Operator operator,
            List<EvolutionSignal> skillSignals,
            Trajectory defaultTrajectory
    ) {
        EvolutionContext onlineContext = onlineContexts.get(skillName);
        if (onlineContext != null) {
            if (onlineContext.getTrajectory() == null) {
                return new EvolutionContext(
                        onlineContext.getSkillName(),
                        onlineContext.getSignals(),
                        onlineContext.getSkillContent(),
                        onlineContext.getMessages(),
                        onlineContext.getExistingDescRecords(),
                        onlineContext.getExistingBodyRecords(),
                        onlineContext.getUserQuery(),
                        defaultTrajectory,
                        onlineContext.getExistingScriptRecords(),
                        onlineContext.getMetadata()
                );
            }
            return onlineContext;
        }
        throw ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                "error_msg",
                "online_contexts missing entry for skill " + skillName
                        + "; TeamSkillExperienceOptimizer requires EvolutionContext"
        );
    }

    private List<EvolutionRecord> recordsFromDrafts(EvolutionContext ctx, List<ParsedExperienceDraft> drafts) {
        Set<String> sources = new LinkedHashSet<>();
        for (EvolutionSignal signal : ctx.getSignals()) {
            sources.add(signal.getSignalType());
        }
        String source = sources.size() == 1 ? sources.iterator().next() : "team_skill_mixed";
        double initialScore = TEAM_INITIAL_SCORE_BY_SIGNAL.getOrDefault(source, 0.6);
        String mergedContext = buildContext(ctx.getSignals());
        List<EvolutionRecord> textRecords = new ArrayList<>();
        List<EvolutionRecord> scriptRecords = new ArrayList<>();
        for (ParsedExperienceDraft draft : drafts) {
            EvolutionPatch patch = draft.getPatch();
            if ("skip".equals(patch.getAction())) {
                continue;
            }
            if (patch.getContent() == null || patch.getContent().strip().isEmpty()) {
                continue;
            }
            boolean isScript = EvolutionTarget.SCRIPT.equals(patch.getTarget());
            if (isScript && scriptRecords.size() >= 1) {
                continue;
            }
            if (!isScript && textRecords.size() >= 2) {
                continue;
            }
            EvolutionRecord record = EvolutionRecord.make(source, mergedContext, patch, initialScore, null, draft.getSummary());
            if (isScript) {
                scriptRecords.add(record);
            } else {
                textRecords.add(record);
            }
        }
        List<EvolutionRecord> records = new ArrayList<>(textRecords);
        records.addAll(scriptRecords);
        return records;
    }

    private List<ParsedExperienceDraft> generateDraftsWithRepairs(String raw, String promptUsed) {
        ExperienceDraftParser.DraftsWithError parsed = ExperienceDraftParser.parseExperienceDraftsWithError(
                raw,
                TeamSkillExperienceOptimizer::extractJsonWithError
        );
        if (parsed.drafts() != null) {
            return parsed.drafts();
        }

        String lastRaw = raw;
        String lastError = parsed.lastError();
        for (int attempt = 2; attempt < 4; attempt++) {
            RetryParseDraftsResult repaired = retryParseDrafts(lastRaw, promptUsed, attempt, lastError);
            if (repaired.drafts() != null) {
                return repaired.drafts();
            }
            if (!isBlank(repaired.retryRaw())) {
                lastRaw = repaired.retryRaw();
                lastError = ExperienceDraftParser.parseExperienceDraftsWithError(
                        lastRaw,
                        TeamSkillExperienceOptimizer::extractJsonWithError
                ).lastError();
            }
        }
        return null;
    }

    private String buildAggregatedPrompt(
            EvolutionContext ctx,
            String trajectorySummary,
            String signalsJson,
            String skillContent,
            String descSummary,
            String bodySummary,
            String scriptSummary,
            String userQuery
    ) {
        String template = SkillCallTemplates.TEAM_EXPERIENCE_GENERATE_PROMPT.getOrDefault(
                language,
                SkillCallTemplates.TEAM_EXPERIENCE_GENERATE_PROMPT_EN
        );
        return template
                .replace("{skill_content}", defaultIfBlank(skillContent, defaultNoneText()))
                .replace("{trajectory_summary}", defaultIfBlank(trajectorySummary, defaultTrajectorySummaryText()))
                .replace("{signals_json}", signalsJson)
                .replace("{existing_desc_summary}", descSummary)
                .replace("{existing_body_summary}", bodySummary)
                .replace("{existing_script_summary}", scriptSummary)
                .replace("{user_query}", defaultIfBlank(userQuery, defaultNoneText()));
    }

    private String buildRetryPrompt(String raw, String originalPrompt, int attemptNumber, String parseError) {
        if (looksTruncated(raw)) {
            return attemptNumber >= 3 ? null : originalPrompt;
        }
        if (attemptNumber >= 3) {
            return SkillCallTemplates.TEAM_JSON_FIX_PROMPT_STRICT
                    .replace("{parse_error}", isBlank(parseError) ? "无法解析为合法 JSON" : parseError)
                    .replace("{broken_preview}", limit(raw, 500));
        }
        return SkillCallTemplates.TEAM_JSON_FIX_PROMPT
                .replace("{parse_error}", isBlank(parseError) ? "JSON 解析失败" : parseError)
                .replace("{broken_output}", raw);
    }

    private String callLlm(
            String prompt,
            String retryPrompt,
            LlmResilience.LLMInvokePolicy policy,
            java.util.function.Predicate<String> isResultUsable
    ) {
        try {
            if (policy == null) {
                AssistantMessage response = llm.invoke(
                                List.of(new UserMessage(prompt)),
                                ModelInvokeOptions.builder().model(model).build()
                        )
                        .toCompletableFuture()
                        .get();
                return responseToText(response);
            }
            return LlmResilience.invokeTextWithRetry(
                    llm,
                    model,
                    prompt,
                    policy,
                    retryPrompt,
                    null,
                    isResultUsable
            );
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exc);
        } catch (ExecutionException exc) {
            throw new RuntimeException(exc.getCause());
        } catch (Exception exc) {
            throw propagate(exc);
        }
    }

    private String loadSkillContent(String skillName) {
        if (evolutionStore == null) {
            return "en".equals(language) ? "N/A" : "无";
        }
        try {
            String content = evolutionStore.readSkillContent(skillName).toCompletableFuture().join();
            return isBlank(content) ? ("en".equals(language) ? "N/A" : "无") : summarizeSkillContent(content);
        } catch (RuntimeException exc) {
            return "en".equals(language) ? "N/A" : "无";
        }
    }

    private String loadExistingEvolutionsSummary(String skillName) {
        if (evolutionStore == null) {
            return "en".equals(language) ? "No existing evolution records" : "无已有演进经验";
        }
        try {
            EvolutionLog log = evolutionStore.loadFullEvolutionLog(skillName).toCompletableFuture().join();
            return summarizeExistingEvolutions(log.getEntries(), language);
        } catch (RuntimeException exc) {
            return "en".equals(language) ? "No existing evolution records" : "无已有演进经验";
        }
    }

    private String signalsToJson(List<EvolutionSignal> signals, boolean pretty) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (EvolutionSignal signal : signals) {
            payload.add(signal.toDict());
        }
        return toJson(payload, pretty);
    }

    private static String toJson(Object data, boolean pretty) {
        try {
            return pretty
                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data)
                    : MAPPER.writeValueAsString(data);
        } catch (Exception exc) {
            return String.valueOf(data);
        }
    }

    private static String summarizeSkillContent(String raw) {
        return summarizeSkillContent(raw, TEAM_SKILL_CONTENT_MAX_CHARS);
    }

    private static String summarizeSkillContent(String raw, int maxChars) {
        String value = raw == null ? "" : raw;
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n... [truncated, original " + value.length() + " chars]";
    }

    private static String shortenExistingEvolutionsSummary(String summary, int maxRecords) {
        if (isBlank(summary)) {
            return summary;
        }
        List<String> kept = new ArrayList<>();
        int recordCount = 0;
        for (String line : summary.split("\n")) {
            if (line.startsWith("- [")) {
                recordCount++;
                if (recordCount > maxRecords) {
                    break;
                }
            }
            kept.add(line);
        }
        String result = String.join("\n", kept).strip();
        return result.isEmpty() ? summary : result;
    }

    private static String summarizeExistingEvolutions(List<EvolutionRecord> records, String language) {
        List<EvolutionRecord> activeRecords = new ArrayList<>();
        for (EvolutionRecord record : records == null ? List.<EvolutionRecord>of() : records) {
            EvolutionPatch change = record.getChange();
            if (change == null || isBlank(change.getSkipReason())) {
                activeRecords.add(record);
            }
        }
        if (activeRecords.isEmpty()) {
            return "en".equals(language) ? "No existing evolution records" : "无已有演进经验";
        }
        List<String> lines = new ArrayList<>();
        lines.add("en".equals(language) ? "Existing evolution records:" : "已有演进经验：");
        int start = Math.max(0, activeRecords.size() - TEAM_EVOLUTION_MAX_RECORDS);
        for (EvolutionRecord record : activeRecords.subList(start, activeRecords.size())) {
            EvolutionPatch change = record.getChange();
            String content = change == null ? "" : change.getContent();
            content = content == null ? "" : content.replaceAll("\\s+", " ").strip();
            lines.add("- [" + record.getId() + "] [" + (change == null ? "?" : change.getSection()) + "] "
                    + limit(content, TEAM_EVOLUTION_PREVIEW_CHARS));
        }
        return String.join("\n", lines);
    }

    private static String buildContext(List<EvolutionSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "";
        }
        int perSignal = Math.max(80, 500 / signals.size());
        List<String> parts = new ArrayList<>();
        for (EvolutionSignal signal : signals) {
            String excerpt = signal.getExcerpt() == null ? "" : signal.getExcerpt().strip();
            parts.add("[" + signal.getSignalType() + "] " + limit(excerpt, perSignal));
        }
        return String.join(" | ", parts);
    }

    private static PatchResponse parsePatchResponse(String raw) {
        Object parsed = parseJson(raw);
        if (parsed instanceof Map<?, ?> map) {
            return new PatchResponse(stringMap(map), "");
        }
        return new PatchResponse(null, "response is not a JSON object");
    }

    private static ParseAttempt tryParse(String text) {
        try {
            return new ParseAttempt(MAPPER.readValue(text, new TypeReference<>() {
            }), "");
        } catch (Exception exc) {
            return new ParseAttempt(null, exc.getMessage());
        }
    }

    private static String fixJsonText(String text) {
        String value = text == null ? "" : text.strip();
        value = value.replaceAll("(?m)^```(?:json)?\\s*", "");
        value = value.replaceAll("(?m)```\\s*$", "");
        value = value.replaceAll("//[^\\n]*", "");
        value = value.replaceAll(",\\s*([}\\]])", "$1");
        return value.strip();
    }

    private static String extractBalancedJson(String text, char opener, char closer) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf(opener);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == opener) {
                depth++;
            } else if (ch == closer) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static boolean hasNonJavaTrajectorySteps(Trajectory trajectory) {
        return false;
    }

    private static Trajectory defaultTrajectory() {
        return Trajectory.builder()
                .executionId("team-skill-evolution")
                .sessionId("team-skill-evolution")
                .source("online")
                .steps(List.of())
                .build();
    }

    private List<EvolutionRecord> existingRecords(String opId) {
        Object existing = parameters.get(opId).getGradient(Protocols.EXPERIENCES_TARGET);
        if (!(existing instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<EvolutionRecord> copied = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof EvolutionRecord record) {
                copied.add(record);
            }
        }
        return copied;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EvolutionContext> extractOnlineContexts(Map<String, Object> config) {
        if (config == null || !(config.get("online_contexts") instanceof Map<?, ?> rawContexts)) {
            return new LinkedHashMap<>();
        }
        Map<String, EvolutionContext> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawContexts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() instanceof EvolutionContext context) {
                result.put(String.valueOf(entry.getKey()), context);
            }
        }
        return result;
    }

    private static String buildFrontmatter(String name, String description, List<Map<String, Object>> roles) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("name: " + name);
        lines.add("description: |");
        lines.add("  " + description);
        lines.add("kind: team-skill");
        lines.add("teammate_mode: build_mode");
        if (roles != null && !roles.isEmpty()) {
            lines.add("roles:");
            for (Map<String, Object> role : roles) {
                lines.add("  - id: " + role.getOrDefault("id", "unknown"));
                lines.add("    skills: " + toJson(role.getOrDefault("skills", List.of()), false));
                lines.add("    tools: " + toJson(role.getOrDefault("tools", List.of()), false));
            }
        }
        lines.add("provenance:");
        lines.add("  origin: auto-generated");
        lines.add("---");
        return String.join("\n", lines);
    }

    private static String toolName(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail && toolCallDetail.getToolName() != null) {
            return toolCallDetail.getToolName();
        }
        if (detail instanceof Map<?, ?> map && map.get("tool_name") != null) {
            return String.valueOf(map.get("tool_name"));
        }
        return "unknown";
    }

    private static Object callArgs(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail) {
            return toolCallDetail.getCallArgs();
        }
        if (detail instanceof Map<?, ?> map) {
            return map.get("call_args");
        }
        return "";
    }

    private static Object callResult(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail) {
            return toolCallDetail.getCallResult();
        }
        if (detail instanceof Map<?, ?> map) {
            return map.get("call_result");
        }
        return "";
    }

    private static Object llmResponse(Object detail) {
        if (detail instanceof LLMCallDetail llmCallDetail) {
            return llmCallDetail.getResponse();
        }
        if (detail instanceof Map<?, ?> map) {
            return map.get("response");
        }
        return null;
    }

    private static String responseToText(AssistantMessage response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        return String.valueOf(response.getContent());
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean boolValue(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private static String shortUserQuery(String userQuery) {
        if (isBlank(userQuery)) {
            return "";
        }
        return limit(userQuery, USER_INTENT_RETRY_CHARS);
    }

    private String defaultNoneText() {
        return "en".equals(language) ? "None" : "无";
    }

    private String defaultTrajectorySummaryText() {
        return "en".equals(language) ? "No trajectory summary" : "无轨迹摘要";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String limit(String value, int maxChars) {
        String text = value == null ? "" : value;
        if (maxChars < 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private static String removePrefix(String value, String prefix) {
        if (value == null || prefix == null || !value.startsWith(prefix)) {
            return value;
        }
        return value.substring(prefix.length());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RuntimeException propagate(Exception exc) {
        if (exc instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(exc);
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * JSON parse attempt tuple.
     *
     * <p>Mirrors Python helper return values in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
     *
     * @param data parsed data
     * @param error parse error
     */
    record ParseAttempt(Object data, String error) {
    }

    /**
     * Team patch parse response.
     *
     * <p>Mirrors Python's {@code _parse_patch_response} return tuple in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
     *
     * @param data parsed patch response
     * @param error parse error
     */
    record PatchResponse(Map<String, Object> data, String error) {
    }

    /**
     * Retry parse result.
     *
     * <p>Mirrors Python's {@code retry_parse} return tuple in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
     *
     * @param patches parsed patches
     * @param retryRaw retry raw response
     */
    public record RetryParseResult(List<EvolutionPatch> patches, String retryRaw) {
    }

    /**
     * Draft retry parse result.
     *
     * <p>Mirrors Python's {@code retry_parse_drafts} return tuple in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
     *
     * @param drafts parsed drafts
     * @param retryRaw retry raw response
     */
    public record RetryParseDraftsResult(List<ParsedExperienceDraft> drafts, String retryRaw) {
    }
}
