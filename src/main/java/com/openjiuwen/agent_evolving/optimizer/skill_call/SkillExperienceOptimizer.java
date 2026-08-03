/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.experience.EvolutionContext;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Online Skill experience optimizer.
 *
 * <p>Mirrors Python's {@code SkillExperienceOptimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
 */
public class SkillExperienceOptimizer extends BaseOptimizer {

    static final Map<String, Double> INITIAL_SCORE_BY_SIGNAL = Map.of(
            "execution_failure", 0.65,
            "user_correction", 0.70,
            "script_artifact", 0.60,
            "conversation_review", 0.50
    );
    public static final LlmResilience.LLMInvokePolicy GENERATE_RECORDS_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(150, 300, 2, 1.0, true);
    static final int RETRY_PARSE_TIMEOUT_SECS = 20;
    public static final Map<String, String> SKILL_EXPERIENCE_GENERATE_PROMPT =
            SkillCallTemplates.SKILL_EXPERIENCE_GENERATE_PROMPT;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SKILL_CONTENT_MAX_CHARS = 6000;
    private static final Pattern HEADING_RE = Pattern.compile("^#{1,4}\\s+");
    private static final int SECTION_PREVIEW_CHARS = 200;
    private static final int CONTEXT_MAX_CHARS = 500;

    private Model llm;
    private String model;
    private final String language;
    private final LlmResilience.LLMInvokePolicy generateRecordsLlmPolicy;
    private Map<String, EvolutionContext> onlineContexts = new LinkedHashMap<>();

    public SkillExperienceOptimizer(Model llm, String model) {
        this(llm, model, "cn", GENERATE_RECORDS_LLM_POLICY);
    }

    public SkillExperienceOptimizer(Model llm, String model, String language) {
        this(llm, model, language, GENERATE_RECORDS_LLM_POLICY);
    }

    public SkillExperienceOptimizer(
            Model llm,
            String model,
            String language,
            LlmResilience.LLMInvokePolicy generateRecordsLlmPolicy
    ) {
        this.domain = "skill_experience";
        this.llm = llm;
        this.model = model;
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.generateRecordsLlmPolicy = generateRecordsLlmPolicy == null
                ? GENERATE_RECORDS_LLM_POLICY
                : generateRecordsLlmPolicy;
    }

    public LlmResilience.LLMInvokePolicy getGenerateRecordsLlmPolicy() {
        return generateRecordsLlmPolicy;
    }

    Model getLlm() {
        return llm;
    }

    String getModelName() {
        return model;
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
        for (Map.Entry<String, Operator> entry : operators.entrySet()) {
            String opId = entry.getKey();
            Operator op = entry.getValue();
            String skillName = removePrefix(opId, "skill_experience_");
            List<EvolutionSignal> skillSignals = selectedSignals.stream()
                    .filter(signal -> Objects.equals(signal.getSkillName(), skillName) || isBlank(signal.getSkillName()))
                    .toList();
            if (skillSignals.isEmpty()) {
                continue;
            }
            EvolutionContext ctx = buildEvolutionContext(skillName, op, skillSignals);
            List<EvolutionRecord> records = generateRecords(ctx);
            if (records.isEmpty()) {
                continue;
            }
            List<EvolutionRecord> existing = existingRecords(opId);
            existing.addAll(records);
            parameters.get(opId).setGradient(Protocols.EXPERIENCES_TARGET, existing);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected Updates doStep() {
        Updates updates = new Updates();
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            Object records = parameters.get(entry.getKey()).getGradient(Protocols.EXPERIENCES_TARGET);
            if (records instanceof List<?> list && !list.isEmpty()) {
                updates.put(entry.getKey(), Protocols.EXPERIENCES_TARGET, records);
            }
        }
        return updates;
    }

    /**
     * Generate and parse evolution records from LLM output.
     *
     * @param ctx evolution context
     * @return generated records
     */
    public List<EvolutionRecord> generateRecords(EvolutionContext ctx) {
        if (ctx == null || ctx.getSignals().isEmpty()) {
            return List.of();
        }

        String conversationSnippet = buildConversationSnippet(ctx.getMessages(), 30, 300, language);
        String signalsJson = signalsToJson(ctx.getSignals(), true);
        String descSummary = buildExistingSummary(ctx.getExistingDescRecords(), "description");
        String bodySummary = buildExistingSummary(ctx.getExistingBodyRecords(), "body");
        String skillContent = summarizeSkillContent(ctx.getSkillContent(), SKILL_CONTENT_MAX_CHARS);
        String prompt = buildPrompt(ctx, signalsJson, conversationSnippet, descSummary, bodySummary, skillContent);
        String retryPrompt = buildPrompt(
                ctx,
                signalsToJson(ctx.getSignals(), false),
                buildConversationSnippet(ctx.getMessages(), 10, 100, language).strip(),
                limitSummaryLines(descSummary, 2),
                limitSummaryLines(bodySummary, 2),
                summarizeSkillContent(ctx.getSkillContent(), 2500),
                shortUserQuery(ctx.getUserQuery())
        );

        LlmResilience.InvokeResult invokeResult;
        try {
            invokeResult = LlmResilience.invokeTextWithRetryAndPrompt(
                    llm,
                    model,
                    prompt,
                    generateRecordsLlmPolicy,
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

    /**
     * Retry parsing and return patches for compatibility with Python's {@code retry_parse}.
     *
     * <p>Mirrors Python's {@code retry_parse} in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
     *
     * @param brokenRaw raw failed LLM response
     * @param originalPrompt original prompt used for generation
     * @return parse retry result
     */
    public RetryParseResult retryParse(String brokenRaw, String originalPrompt) {
        return retryParse(brokenRaw, originalPrompt, 1, "");
    }

    public RetryParseResult retryParse(String brokenRaw, String originalPrompt, int attemptNumber) {
        return retryParse(brokenRaw, originalPrompt, attemptNumber, "");
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

    /**
     * Retry parsing while preserving per-record summaries.
     *
     * <p>Mirrors Python's {@code retry_parse_drafts} in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
     *
     * @param brokenRaw raw failed LLM response
     * @param originalPrompt original prompt used for generation
     * @param attemptNumber retry attempt number
     * @param parseError parse error detail
     * @return parse retry result
     */
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
                                    .timeout((float) RETRY_PARSE_TIMEOUT_SECS)
                                    .build()
                    )
                    .toCompletableFuture()
                    .get(RETRY_PARSE_TIMEOUT_SECS, TimeUnit.SECONDS);
            retryRaw = responseToText(response);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            return new RetryParseDraftsResult(null, "");
        } catch (ExecutionException | CompletionException | java.util.concurrent.TimeoutException exc) {
            return new RetryParseDraftsResult(null, "");
        }

        ExperienceDraftParser.DraftsWithError parsed = ExperienceDraftParser.parseExperienceDraftsWithError(
                retryRaw,
                SkillExperienceOptimizer::extractJsonWithError
        );
        if (parsed.drafts() == null) {
            return new RetryParseDraftsResult(null, retryRaw);
        }
        return new RetryParseDraftsResult(parsed.drafts(), retryRaw);
    }

    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    static String buildConversationSnippet(List<Map<String, Object>> messages) {
        return buildConversationSnippet(messages, 30, 300, "cn");
    }

    static String buildConversationSnippet(
            List<Map<String, Object>> messages,
            int maxMessages,
            int contentPreviewChars,
            String language
    ) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int start = Math.max(0, messages.size() - maxMessages);
        List<Map<String, Object>> recent = messages.subList(start, messages.size());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < recent.size(); i++) {
            Map<String, Object> message = recent.get(i);
            String role = String.valueOf(message.getOrDefault("role", "unknown"));
            String text = extractMessageText(message.get("content")).strip();
            if (text.isEmpty()) {
                text = "cn".equals(language) ? "(无文本)" : "(No text)";
            }
            int budget = i >= recent.size() - 5 ? contentPreviewChars * 2 : contentPreviewChars;
            if (text.length() > budget) {
                int origLen = text.length();
                text = text.substring(0, budget) + ("cn".equals(language)
                        ? "\n... [已截断，原始长度 " + origLen + " 字符]"
                        : "\n... [truncated, original " + origLen + " chars]");
            }
            Object toolCalls = message.get("tool_calls");
            if ("assistant".equals(role) && toolCalls instanceof List<?> calls) {
                lines.add("[assistant] (tool_calls: " + String.join(", ", toolCallNames(calls)) + ")\n  " + text);
            } else {
                lines.add("[" + role + "] " + text);
            }
        }
        return String.join("\n", lines);
    }

    static String summarizeSkillContent(String raw) {
        return summarizeSkillContent(raw, SKILL_CONTENT_MAX_CHARS);
    }

    static String summarizeSkillContent(String raw, int maxChars) {
        String value = raw == null ? "" : raw;
        if (value.length() <= maxChars) {
            return value;
        }

        List<String> sections = splitIntoSections(value);
        if (sections.isEmpty()) {
            return value.substring(0, maxChars) + "\n... [已截断，原始共 " + value.length() + " 字符]";
        }

        List<String> parts = new ArrayList<>();
        parts.add(sections.get(0));
        if (sections.size() > 1) {
            parts.add("\n[以下章节仅保留标题与开头摘要，完整内容已省略]\n");
            for (int i = 1; i < sections.size(); i++) {
                parts.add(previewSection(sections.get(i), SECTION_PREVIEW_CHARS));
            }
        }
        String summary = String.join("\n", parts);
        if (summary.length() > maxChars) {
            summary = summary.substring(0, maxChars)
                    + "\n... [已截断，原始 SKILL.md 共 " + value.length() + " 字符]";
        }
        return summary;
    }

    static List<String> splitIntoSections(String text) {
        String value = text == null ? "" : text;
        String[] lines = value.split("\n", -1);
        List<String> sections = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (HEADING_RE.matcher(line).find() && !current.isEmpty()) {
                sections.add(String.join("\n", current));
                current = new ArrayList<>();
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            sections.add(String.join("\n", current));
        }
        return sections;
    }

    static String previewSection(String section) {
        return previewSection(section, SECTION_PREVIEW_CHARS);
    }

    static String previewSection(String section, int previewChars) {
        String value = section == null ? "" : section;
        String[] lines = value.split("\n", -1);
        String heading = lines.length == 0 ? "" : lines[0];
        String body = lines.length > 1
                ? String.join("\n", Arrays.copyOfRange(lines, 1, lines.length)).strip()
                : "";
        if (body.isEmpty()) {
            return heading;
        }
        if (body.length() <= previewChars) {
            return value;
        }
        return heading + "\n" + body.substring(0, previewChars) + "...";
    }

    static String fixJsonText(String text) {
        String value = text == null ? "" : text.strip();
        value = value.replaceAll("(?m)^```(?:json)?\\s*", "");
        value = value.replaceAll("(?m)```\\s*$", "");
        value = value.replaceAll("//[^\\n]*", "");
        value = value.replaceAll(",\\s*([}\\]])", "$1");
        return value.strip();
    }

    static Object extractJson(String raw) {
        return extractJsonWithError(raw).data();
    }

    static ExperienceDraftParser.JsonExtractionResult extractJsonWithError(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return new ExperienceDraftParser.JsonExtractionResult(null, "empty response");
        }

        ParseAttempt direct = tryParse(value);
        if (direct.data() != null) {
            return new ExperienceDraftParser.JsonExtractionResult(direct.data(), "");
        }

        String fixed = fixJsonText(value);
        ParseAttempt fixedAttempt = tryParse(fixed);
        if (fixedAttempt.data() != null) {
            return new ExperienceDraftParser.JsonExtractionResult(fixedAttempt.data(), "");
        }

        String lastError = fixedAttempt.error() == null || fixedAttempt.error().isEmpty()
                ? "unknown"
                : fixedAttempt.error();
        for (String pattern : List.of("\\[[\\s\\S]*\\]", "\\{[\\s\\S]*\\}")) {
            Matcher matcher = Pattern.compile(pattern).matcher(fixed);
            if (!matcher.find()) {
                continue;
            }
            String candidate = matcher.group(0);
            ParseAttempt parsed = tryParse(candidate);
            if (parsed.data() != null) {
                return new ExperienceDraftParser.JsonExtractionResult(parsed.data(), "");
            }
            ParseAttempt refixed = tryParse(fixJsonText(candidate));
            if (refixed.data() != null) {
                return new ExperienceDraftParser.JsonExtractionResult(refixed.data(), "");
            }
            if (refixed.error() != null && !refixed.error().isEmpty()) {
                lastError = refixed.error();
            }
        }
        return new ExperienceDraftParser.JsonExtractionResult(null, lastError);
    }

    static String buildContext(List<EvolutionSignal> signals) {
        return buildContext(signals, CONTEXT_MAX_CHARS);
    }

    static String buildContext(List<EvolutionSignal> signals, int maxChars) {
        if (signals == null || signals.isEmpty()) {
            return "";
        }
        int perSignal = Math.max(80, maxChars / signals.size());
        List<String> parts = new ArrayList<>();
        for (EvolutionSignal signal : signals) {
            String excerpt = signal.getExcerpt() == null ? "" : signal.getExcerpt().strip();
            if (excerpt.length() > perSignal) {
                excerpt = excerpt.substring(0, perSignal) + "...";
            }
            parts.add("[" + signal.getSignalType() + "] " + excerpt);
        }
        return String.join(" | ", parts);
    }

    static boolean looksTruncated(String text) {
        String value = text == null ? "" : text;
        long opens = value.chars().filter(ch -> ch == '{' || ch == '[').count();
        long closes = value.chars().filter(ch -> ch == '}' || ch == ']').count();
        return opens > closes + 1;
    }

    static List<EvolutionPatch> parseLlmResponse(String raw) {
        ExperienceDraftParser.DraftsWithError parsed = ExperienceDraftParser.parseExperienceDraftsWithError(
                raw,
                SkillExperienceOptimizer::extractJsonWithError
        );
        if (parsed.drafts() == null) {
            return null;
        }
        return parsed.drafts().stream().map(ParsedExperienceDraft::getPatch).toList();
    }

    private EvolutionContext buildEvolutionContext(String skillName, Operator operator, List<EvolutionSignal> signals) {
        EvolutionContext onlineContext = onlineContexts.get(skillName);
        if (onlineContext != null) {
            return onlineContext;
        }
        throw ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                "error_msg",
                "online_contexts missing entry for skill " + skillName
                        + "; SkillExperienceOptimizer requires EvolutionContext"
        );
    }

    @SuppressWarnings("unchecked")
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

    private List<ParsedExperienceDraft> generateDraftsWithRepairs(String raw, String promptUsed) {
        ExperienceDraftParser.DraftsWithError parsed = ExperienceDraftParser.parseExperienceDraftsWithError(
                raw,
                SkillExperienceOptimizer::extractJsonWithError
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
            if (repaired.retryRaw() != null && !repaired.retryRaw().isEmpty()) {
                lastRaw = repaired.retryRaw();
                lastError = ExperienceDraftParser.parseExperienceDraftsWithError(
                        lastRaw,
                        SkillExperienceOptimizer::extractJsonWithError
                ).lastError();
            }
        }
        return null;
    }

    private List<EvolutionRecord> recordsFromDrafts(EvolutionContext ctx, List<ParsedExperienceDraft> drafts) {
        String source = ctx.getSignals().get(0).getSignalType();
        String mergedContext = buildContext(ctx.getSignals(), CONTEXT_MAX_CHARS);
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
            double initialScore = INITIAL_SCORE_BY_SIGNAL.getOrDefault(source, 0.6);
            EvolutionRecord record = EvolutionRecord.make(
                    source,
                    mergedContext,
                    patch,
                    initialScore,
                    null,
                    draft.getSummary()
            );
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

    private String buildPrompt(
            EvolutionContext ctx,
            String signalsJson,
            String conversationSnippet,
            String descSummary,
            String bodySummary,
            String skillContent
    ) {
        String userQuery = ctx.getUserQuery() == null || ctx.getUserQuery().isEmpty()
                ? defaultUserQuery()
                : ctx.getUserQuery();
        return buildPrompt(ctx, signalsJson, conversationSnippet, descSummary, bodySummary, skillContent, userQuery);
    }

    private String buildPrompt(
            EvolutionContext ctx,
            String signalsJson,
            String conversationSnippet,
            String descSummary,
            String bodySummary,
            String skillContent,
            String userQuery
    ) {
        String template = SKILL_EXPERIENCE_GENERATE_PROMPT.getOrDefault(
                language,
                SKILL_EXPERIENCE_GENERATE_PROMPT.get("cn")
        );
        return template
                .replace("{skill_content}", skillContent == null ? "" : skillContent)
                .replace("{signals_json}", signalsJson == null ? "" : signalsJson)
                .replace("{conversation_snippet}", conversationSnippet == null ? "" : conversationSnippet.strip())
                .replace("{existing_desc_summary}", isBlank(descSummary) ? defaultExistingRecords() : descSummary)
                .replace("{existing_body_summary}", isBlank(bodySummary) ? defaultExistingRecords() : bodySummary)
                .replace("{user_query}", userQuery == null ? defaultUserQuery() : userQuery);
    }

    private String buildRetryPrompt(String raw, String originalPrompt, int attemptNumber, String parseError) {
        boolean truncated = looksTruncated(raw);
        if (truncated) {
            if (attemptNumber >= 3) {
                return null;
            }
            return originalPrompt;
        }
        if (attemptNumber >= 3) {
            return SkillCallTemplates.JSON_FIX_PROMPT_STRICT
                    .replace("{parse_error}", isBlank(parseError) ? "无法解析为合法 JSON" : parseError)
                    .replace("{broken_preview}", raw.substring(0, Math.min(500, raw.length())));
        }
        return SkillCallTemplates.JSON_FIX_PROMPT
                .replace("{parse_error}", isBlank(parseError) ? "JSON 解析失败" : parseError)
                .replace("{broken_output}", raw);
    }

    private String signalsToJson(List<EvolutionSignal> signals, boolean pretty) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (EvolutionSignal signal : signals) {
            payload.add(signal.toDict());
        }
        try {
            return pretty
                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                    : MAPPER.writeValueAsString(payload);
        } catch (Exception exc) {
            return payload.toString();
        }
    }

    private static String buildExistingSummary(List<EvolutionRecord> records, String label) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (EvolutionRecord record : records) {
            String prefix = isBlank(label) ? "" : "[" + label + "] ";
            EvolutionPatch change = record.getChange();
            String section = change == null ? "" : change.getSection();
            String content = change == null ? "" : change.getContent();
            lines.add("- " + prefix + "[" + record.getId() + "] [" + section + "] " + content);
        }
        return String.join("\n", lines);
    }

    private static String limitSummaryLines(String summary, int maxLines) {
        if (isBlank(summary) || maxLines <= 0) {
            return "";
        }
        String[] lines = summary.split("\n");
        return String.join("\n", Arrays.copyOfRange(lines, 0, Math.min(maxLines, lines.length)));
    }

    private static String extractMessageText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object block : list) {
                if (block instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    parts.add(text == null ? "" : String.valueOf(text));
                } else if (block instanceof String text) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        return String.valueOf(content);
    }

    private static List<String> toolCallNames(List<?> calls) {
        List<String> names = new ArrayList<>();
        for (Object call : calls) {
            if (!(call instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("name");
            if (name == null && map.get("function") instanceof Map<?, ?> function) {
                name = function.get("name");
            }
            names.add(name == null ? "" : String.valueOf(name));
        }
        return names;
    }

    private static ParseAttempt tryParse(String text) {
        try {
            return new ParseAttempt(MAPPER.readValue(text, new TypeReference<>() {
            }), "");
        } catch (Exception exc) {
            return new ParseAttempt(null, exc.getMessage());
        }
    }

    private static String responseToText(AssistantMessage response) {
        if (response == null) {
            return "";
        }
        Object content = response.getContent();
        return content == null ? "" : String.valueOf(content);
    }

    private static RuntimeException propagate(Exception exc) {
        if (exc instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(exc);
    }

    private String defaultExistingRecords() {
        return "cn".equals(language) ? "无已有记录" : "No existing records";
    }

    private String defaultUserQuery() {
        return "cn".equals(language) ? "无" : "None";
    }

    private static String shortUserQuery(String userQuery) {
        if (userQuery == null || userQuery.isEmpty()) {
            return "";
        }
        return userQuery.substring(0, Math.min(500, userQuery.length()));
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

    /**
     * JSON parse attempt tuple.
     *
     * <p>Mirrors Python's helper return values in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
     *
     * @param data parsed JSON data
     * @param error parse error
     */
    record ParseAttempt(Object data, String error) {
    }

    /**
     * Retry parse result.
     *
     * <p>Mirrors Python's {@code retry_parse} return tuple in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
     *
     * @param patches parsed patches
     * @param retryRaw retry LLM raw response
     */
    public record RetryParseResult(List<EvolutionPatch> patches, String retryRaw) {
    }

    /**
     * Draft retry parse result.
     *
     * <p>Mirrors Python's {@code retry_parse_drafts} return tuple in
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
     *
     * @param drafts parsed drafts
     * @param retryRaw retry LLM raw response
     */
    public record RetryParseDraftsResult(List<ParsedExperienceDraft> drafts, String retryRaw) {
    }
}
