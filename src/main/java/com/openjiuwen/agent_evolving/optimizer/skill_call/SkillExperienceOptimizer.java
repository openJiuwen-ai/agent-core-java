/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionContext;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Online Skill experience optimizer.
 *
 * <p>Mirrors Python's
 * {@code openjiuwen.agent_evolving.optimizer.skill_call.experience_optimizer.SkillExperienceOptimizer}.
 */
public class SkillExperienceOptimizer extends BaseOptimizer {

    static final Map<String, Double> INITIAL_SCORE_BY_SIGNAL = Map.of(
            "execution_failure", 0.65,
            "user_correction", 0.70,
            "script_artifact", 0.60,
            "conversation_review", 0.50
    );
    public static final LlmResilience.LLMInvokePolicy GENERATE_RECORDS_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(60, 180, 3, 0.0, true);
    static final int RETRY_PARSE_TIMEOUT_SECS = 20;

    public static final Map<String, String> SKILL_EXPERIENCE_GENERATE_PROMPT = Map.of(
            "cn", """
                    你是一个 Skill 优化专家。根据对话中发现的问题信号和对话历史，为 Skill 生成演进经验。

                    ## 输出格式（最重要）
                    你的回复必须是一个合法的 JSON 数组，不要任何其他内容。

                    ## 角色约束
                    演进经验必须遵从 Agent 的角色能力和主要任务目标。

                    ## 输入信息
                    ### 当前 Skill 内容
                    {skill_content}

                    ### 预检测信号
                    {signals_json}

                    ### 对话历史
                    {conversation_snippet}

                    ### 已有 description 经验
                    {existing_desc_summary}

                    ### 已有 body 经验
                    {existing_body_summary}

                    ### 用户主动描述的优化方向（可选）
                    {user_query}

                    ## 数量限制
                    文本经验不超过 2 条，脚本经验不超过 1 条，二者独立计数。

                    ## section 选择参考
                    execution_failure / workaround 通常归入 Troubleshooting；
                    script_artifact 归入 Scripts；
                    collaboration_send / collaboration_claim / collaboration_view / collaboration_receive /
                    collaboration_failure 归入 Collaboration。
                    """,
            "en", """
                    You are a Skill optimization expert. Based on problem signals discovered in the conversation
                    and the conversation history, generate evolution experiences for the Skill.

                    ## Output Format (MOST IMPORTANT)
                    Your response must be a valid JSON array, nothing else.

                    ## Role Constraints
                    Evolution experiences must respect the Agent's role capabilities and primary objectives.

                    ## Input Information
                    ### Current Skill Content
                    {skill_content}

                    ### Pre-detected Signals
                    {signals_json}

                    ### Conversation History
                    {conversation_snippet}

                    ### Existing description experiences
                    {existing_desc_summary}

                    ### Existing body experiences
                    {existing_body_summary}

                    ### User-specified optimization direction (optional)
                    {user_query}

                    ## Quantity Limit
                    Text experiences must not exceed 2; script experiences must not exceed 1.

                    ## Section selection reference
                    execution_failure / workaround types usually belong to Troubleshooting;
                    script_artifact types belong to Scripts;
                    collaboration_send / collaboration_claim / collaboration_view / collaboration_receive /
                    collaboration_failure types belong to Collaboration.
                    """
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SKILL_CONTENT_MAX_CHARS = 6000;
    private static final Pattern HEADING_RE = Pattern.compile("^#{1,4}\\s+");
    private static final int SECTION_PREVIEW_CHARS = 200;
    private static final int CONTEXT_MAX_CHARS = 500;
    private static final Set<String> VALID_SECTIONS = new LinkedHashSet<>(Arrays.asList(
            "Instructions", "Examples", "Troubleshooting", "Scripts", "Collaboration", "Roles", "Constraints"
    ));
    private static final String JSON_FIX_PROMPT = """
            你上次的输出不是合法 JSON，请修复并重新输出。
            只输出修复后的 JSON 数组，不要任何解释文字。

            ## 解析错误
            {parse_error}

            ## 原始输出（请从中提取并修复）
            {broken_output}
            """;
    private static final String JSON_FIX_PROMPT_STRICT = """
            你的 JSON 输出多次解析失败。请完全重新生成。

            ## 解析错误
            {parse_error}

            ## 上次输出预览
            {broken_preview}

            ## 严格要求
            1. 只输出一个 JSON 数组，以 [ 开头，以 ] 结尾
            2. 不要任何解释文字，不要 Markdown 代码块
            3. 所有字符串内的换行必须写成 \\n
            4. 所有字符串内的引号必须写成 \\"
            5. 不要用单引号，只用双引号
            """;

    private Model llm;
    private String model;
    private final String language;
    private final LlmResilience.LLMInvokePolicy generateRecordsLlmPolicy;

    public SkillExperienceOptimizer(Model llm, String model) {
        this(llm, model, "cn", GENERATE_RECORDS_LLM_POLICY);
    }

    public SkillExperienceOptimizer(Model llm, String model, String language) {
        this(llm, model, language, GENERATE_RECORDS_LLM_POLICY);
    }

    public SkillExperienceOptimizer(Model llm, String model, String language,
                                    LlmResilience.LLMInvokePolicy generateRecordsLlmPolicy) {
        this.domain = "skill_experience";
        this.llm = llm;
        this.model = model;
        this.language = language != null ? language : "cn";
        this.generateRecordsLlmPolicy = generateRecordsLlmPolicy != null
                ? generateRecordsLlmPolicy : GENERATE_RECORDS_LLM_POLICY;
    }

    public LlmResilience.LLMInvokePolicy getGenerateRecordsLlmPolicy() {
        return generateRecordsLlmPolicy;
    }

    @Override
    public List<String> defaultTargets() {
        return List.of("experiences");
    }

    public List<EvolutionRecord> generateRecords(EvolutionContext ctx) {
        if (ctx == null || ctx.getSignals() == null || ctx.getSignals().isEmpty()) {
            return Collections.emptyList();
        }

        String conversationSnippet = buildConversationSnippet(ctx.getMessages(), 30, 300, language);
        String signalsJson = signalsToJson(ctx.getSignals(), true);
        String descSummary = buildExistingSummary(ctx.getExistingDescRecords(), "description");
        String bodySummary = buildExistingSummary(ctx.getExistingBodyRecords(), "body");
        String prompt = buildPrompt(ctx, signalsJson, conversationSnippet, descSummary, bodySummary,
                summarizeSkillContent(ctx.getSkillContent(), SKILL_CONTENT_MAX_CHARS));
        String retryPrompt = buildPrompt(
                ctx,
                signalsToJson(ctx.getSignals(), false),
                buildConversationSnippet(ctx.getMessages(), 10, 100, language).strip(),
                limitSummaryLines(descSummary, 2),
                limitSummaryLines(bodySummary, 2),
                summarizeSkillContent(ctx.getSkillContent(), 2500),
                ctx.getUserQuery() == null || ctx.getUserQuery().isEmpty()
                        ? defaultUserQuery() : ctx.getUserQuery().substring(0, Math.min(500, ctx.getUserQuery().length()))
        );

        InvokeTextResult invokeResult;
        try {
            invokeResult = invokeTextWithRetryAndPrompt(prompt, retryPrompt);
        } catch (Exception exc) {
            return Collections.emptyList();
        }

        ExtractJsonResult parsed = extractJsonWithError(invokeResult.raw());
        List<EvolutionPatch> patches;
        if (parsed.data() != null) {
            patches = parsePatches(parsed.data());
        } else {
            patches = null;
        }

        String lastRaw = invokeResult.raw();
        String lastError = parsed.error();
        for (int attempt = 2; attempt < 4; attempt++) {
            if (patches != null) {
                break;
            }
            RetryParseResult result = retryParse(lastRaw, invokeResult.promptUsed(), attempt, lastError);
            patches = result.patches();
            if (result.retryRaw() != null && !result.retryRaw().isEmpty()) {
                lastRaw = result.retryRaw();
                lastError = extractJsonWithError(lastRaw).error();
            }
        }
        if (patches == null) {
            return Collections.emptyList();
        }

        String source = ctx.getSignals().getFirst().getSignalType();
        String mergedContext = buildContext(ctx.getSignals(), CONTEXT_MAX_CHARS);
        List<EvolutionRecord> textRecords = new ArrayList<>();
        List<EvolutionRecord> scriptRecords = new ArrayList<>();
        for (EvolutionPatch patch : patches) {
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
            EvolutionRecord record = EvolutionRecord.make(source, mergedContext, patch, initialScore, null);
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

    public RetryParseResult retryParse(String brokenRaw, String originalPrompt) {
        return retryParse(brokenRaw, originalPrompt, 1, "");
    }

    public RetryParseResult retryParse(String brokenRaw, String originalPrompt, int attemptNumber) {
        return retryParse(brokenRaw, originalPrompt, attemptNumber, "");
    }

    public RetryParseResult retryParse(String brokenRaw, String originalPrompt,
                                       int attemptNumber, String parseError) {
        String raw = brokenRaw != null ? brokenRaw : "";
        boolean truncated = looksTruncated(raw);
        String retryPrompt;
        if (truncated) {
            if (attemptNumber >= 3) {
                return new RetryParseResult(null, raw);
            }
            retryPrompt = originalPrompt;
        } else if (attemptNumber >= 3) {
            retryPrompt = JSON_FIX_PROMPT_STRICT
                    .replace("{parse_error}", parseError == null || parseError.isEmpty()
                            ? "无法解析为合法 JSON" : parseError)
                    .replace("{broken_preview}", raw.substring(0, Math.min(500, raw.length())));
        } else {
            retryPrompt = JSON_FIX_PROMPT
                    .replace("{parse_error}", parseError == null || parseError.isEmpty()
                            ? "JSON 解析失败" : parseError)
                    .replace("{broken_output}", raw);
        }

        String retryRaw;
        try {
            AssistantMessage response = llm.invoke(
                    Collections.singletonList(new UserMessage(retryPrompt)),
                    null,
                    0.1f,
                    null,
                    model,
                    null,
                    null,
                    null,
                    (float) RETRY_PARSE_TIMEOUT_SECS,
                    null);
            retryRaw = responseToText(response);
        } catch (Exception exc) {
            return new RetryParseResult(null, "");
        }

        List<EvolutionPatch> patches = parseLlmResponse(retryRaw);
        if (patches == null) {
            return new RetryParseResult(null, retryRaw);
        }
        return new RetryParseResult(patches, retryRaw);
    }

    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    Model getLlm() {
        return llm;
    }

    String getModelName() {
        return model;
    }

    @Override
    protected Updates doStep() {
        return new Updates();
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        // Online skill experience generation is driven through generateRecords in this Java slice.
    }

    static String buildConversationSnippet(List<Map<String, Object>> messages) {
        return buildConversationSnippet(messages, 30, 300, "cn");
    }

    @SuppressWarnings("unchecked")
    static String buildConversationSnippet(List<Map<String, Object>> messages, int maxMessages,
                                           int contentPreviewChars, String language) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> recent = messages.subList(Math.max(0, messages.size() - maxMessages), messages.size());
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
                List<String> names = new ArrayList<>();
                for (Object call : calls) {
                    if (call instanceof Map<?, ?> map) {
                        Object name = map.get("name");
                        names.add(name != null ? String.valueOf(name) : "");
                    }
                }
                lines.add("[assistant] (tool_calls: " + String.join(", ", names) + ")\n  " + text);
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
        String value = raw != null ? raw : "";
        if (value.length() <= maxChars) {
            return value;
        }
        List<String> sections = splitIntoSections(value);
        if (sections.isEmpty()) {
            return value.substring(0, maxChars) + "\n... [已截断，原始共 " + value.length() + " 字符]";
        }

        List<String> parts = new ArrayList<>();
        parts.add(sections.getFirst());
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
        String value = text != null ? text : "";
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
        String value = section != null ? section : "";
        String[] lines = value.split("\n", -1);
        String heading = lines.length > 0 ? lines[0] : "";
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
        String value = text != null ? text.strip() : "";
        value = value.replaceAll("(?m)^```(?:json)?\\s*", "");
        value = value.replaceAll("(?m)```\\s*$", "");
        value = value.replaceAll("//[^\\n]*", "");
        value = value.replaceAll(",\\s*([}\\]])", "$1");
        return value.strip();
    }

    static Object extractJson(String raw) {
        return extractJsonWithError(raw).data();
    }

    static ExtractJsonResult extractJsonWithError(String raw) {
        String value = raw != null ? raw.strip() : "";
        if (value.isEmpty()) {
            return new ExtractJsonResult(null, "empty response");
        }

        ParseAttempt direct = tryParse(value);
        if (direct.data() != null) {
            return new ExtractJsonResult(direct.data(), "");
        }

        String fixed = fixJsonText(value);
        ParseAttempt fixedAttempt = tryParse(fixed);
        if (fixedAttempt.data() != null) {
            return new ExtractJsonResult(fixedAttempt.data(), "");
        }

        String lastError = fixedAttempt.error() != null ? fixedAttempt.error() : "unknown";
        for (String pattern : List.of("\\[[\\s\\S]*\\]", "\\{[\\s\\S]*\\}")) {
            Matcher matcher = Pattern.compile(pattern).matcher(fixed);
            if (matcher.find()) {
                String candidate = matcher.group(0);
                ParseAttempt parsed = tryParse(candidate);
                if (parsed.data() != null) {
                    return new ExtractJsonResult(parsed.data(), "");
                }
                ParseAttempt refixed = tryParse(fixJsonText(candidate));
                if (refixed.data() != null) {
                    return new ExtractJsonResult(refixed.data(), "");
                }
                if (refixed.error() != null) {
                    lastError = refixed.error();
                }
            }
        }
        return new ExtractJsonResult(null, lastError);
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
        for (EvolutionSignal sig : signals) {
            String excerpt = sig.getExcerpt() != null ? sig.getExcerpt().strip() : "";
            if (excerpt.length() > perSignal) {
                excerpt = excerpt.substring(0, perSignal) + "...";
            }
            parts.add("[" + sig.getSignalType() + "] " + excerpt);
        }
        return String.join(" | ", parts);
    }

    static boolean looksTruncated(String text) {
        String value = text != null ? text : "";
        long opens = value.chars().filter(ch -> ch == '{' || ch == '[').count();
        long closes = value.chars().filter(ch -> ch == '}' || ch == ']').count();
        return opens > closes + 1;
    }

    static EvolutionPatch parseSinglePatch(Map<String, Object> data) {
        Map<String, Object> payload = data != null ? data : Map.of();
        String action = String.valueOf(payload.getOrDefault("action", "append"));
        if ("skip".equals(action)) {
            return EvolutionPatch.builder()
                    .section("")
                    .action("skip")
                    .content("")
                    .skipReason(String.valueOf(payload.getOrDefault("skip_reason", "unknown")))
                    .build();
        }

        String section = String.valueOf(payload.getOrDefault("section", "Troubleshooting"));
        if (!VALID_SECTIONS.contains(section)) {
            section = "Troubleshooting";
        }
        Object rawTarget = payload.getOrDefault("target", "body");
        EvolutionTarget target = EvolutionTarget.fromValue(rawTarget != null ? String.valueOf(rawTarget) : "body");
        Object mergeTarget = payload.get("merge_target");
        String normalizedMergeTarget = mergeTarget == null || "null".equals(String.valueOf(mergeTarget))
                ? null : String.valueOf(mergeTarget);

        return EvolutionPatch.builder()
                .section(section)
                .action("append")
                .content(String.valueOf(payload.getOrDefault("content", "")))
                .target(target)
                .mergeTarget(normalizedMergeTarget)
                .scriptFilename(optionalString(payload.get("script_filename")))
                .scriptLanguage(optionalString(payload.get("script_language")))
                .scriptPurpose(optionalString(payload.get("script_purpose")))
                .build();
    }

    static List<EvolutionPatch> parseLlmResponse(String raw) {
        Object data = extractJson(raw);
        if (data == null) {
            return null;
        }
        return parsePatches(data);
    }

    @SuppressWarnings("unchecked")
    private static List<EvolutionPatch> parsePatches(Object data) {
        List<?> items = data instanceof List<?> list ? list : List.of(data);
        List<EvolutionPatch> patches = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                patches.add(parseSinglePatch((Map<String, Object>) map));
            }
        }
        return patches;
    }

    private String buildPrompt(EvolutionContext ctx, String signalsJson, String conversationSnippet,
                               String descSummary, String bodySummary, String skillContent) {
        return buildPrompt(ctx, signalsJson, conversationSnippet, descSummary, bodySummary,
                skillContent, ctx.getUserQuery() == null || ctx.getUserQuery().isEmpty()
                        ? defaultUserQuery() : ctx.getUserQuery());
    }

    private String buildPrompt(EvolutionContext ctx, String signalsJson, String conversationSnippet,
                               String descSummary, String bodySummary, String skillContent,
                               String userQuery) {
        String template = SKILL_EXPERIENCE_GENERATE_PROMPT.getOrDefault(
                language, SKILL_EXPERIENCE_GENERATE_PROMPT.get("cn"));
        return template
                .replace("{skill_content}", skillContent != null ? skillContent : "")
                .replace("{signals_json}", signalsJson != null ? signalsJson : "")
                .replace("{conversation_snippet}", conversationSnippet != null ? conversationSnippet.strip() : "")
                .replace("{existing_desc_summary}", descSummary == null || descSummary.isEmpty()
                        ? defaultExistingRecords() : descSummary)
                .replace("{existing_body_summary}", bodySummary == null || bodySummary.isEmpty()
                        ? defaultExistingRecords() : bodySummary)
                .replace("{user_query}", userQuery != null ? userQuery : defaultUserQuery());
    }

    private InvokeTextResult invokeTextWithRetryAndPrompt(String prompt, String retryPrompt) throws Exception {
        Exception lastError = null;
        boolean useRetryPrompt = false;
        int maxAttempts = Math.max(generateRecordsLlmPolicy.getMaxAttempts(), 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String currentPrompt = useRetryPrompt && retryPrompt != null ? retryPrompt : prompt;
            float timeoutSecs = (float) generateRecordsLlmPolicy.getAttemptTimeoutSecs();
            try {
                AssistantMessage response = llm.invoke(
                        Collections.singletonList(new UserMessage(currentPrompt)),
                        null,
                        null,
                        null,
                        model,
                        null,
                        null,
                        null,
                        timeoutSecs,
                        null);
                String raw = responseToText(response);
                if (generateRecordsLlmPolicy.isRetryEmptyResponse()
                        && raw.strip().isEmpty()
                        && attempt < maxAttempts) {
                    continue;
                }
                return new InvokeTextResult(raw, currentPrompt);
            } catch (Exception exc) {
                lastError = exc;
                if (retryPrompt != null && attempt < maxAttempts && isTimeoutLike(exc)) {
                    useRetryPrompt = true;
                    continue;
                }
                throw exc;
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("LLM invoke failed");
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
        } catch (JsonProcessingException exc) {
            return payload.toString();
        }
    }

    private static String buildExistingSummary(List<EvolutionRecord> records, String label) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (EvolutionRecord record : records) {
            String prefix = label != null && !label.isEmpty() ? "[" + label + "] " : "";
            EvolutionPatch change = record.getChange();
            String section = change != null ? change.getSection() : "";
            String content = change != null ? change.getContent() : "";
            lines.add("- " + prefix + "[" + record.getId() + "] [" + section + "] " + content);
        }
        return String.join("\n", lines);
    }

    private static String limitSummaryLines(String summary, int maxLines) {
        if (summary == null || summary.isEmpty() || maxLines <= 0) {
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
                    parts.add(text != null ? String.valueOf(text) : "");
                } else if (block instanceof String text) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        return String.valueOf(content);
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
        return content instanceof String text ? text : String.valueOf(content);
    }

    private static boolean isTimeoutLike(Exception exc) {
        if (exc instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        String typeName = exc.getClass().getSimpleName().toLowerCase();
        String message = exc.getMessage() != null ? exc.getMessage().toLowerCase() : "";
        return typeName.contains("timeout") || message.contains("timeout") || message.contains("timed out");
    }

    private String defaultExistingRecords() {
        return "cn".equals(language) ? "无已有记录" : "No existing records";
    }

    private String defaultUserQuery() {
        return "cn".equals(language) ? "无" : "None";
    }

    private static String optionalString(Object value) {
        if (value == null || "null".equals(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value);
    }

    record InvokeTextResult(String raw, String promptUsed) {
    }

    record ParseAttempt(Object data, String error) {
    }

    record ExtractJsonResult(Object data, String error) {
    }

    public record RetryParseResult(List<EvolutionPatch> patches, String retryRaw) {
    }
}
