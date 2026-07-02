/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges optimizer keyword output and sharing query keyword extraction.
 *
 * <p>Mirrors Python's {@code KeywordExtractor} in
 * {@code openjiuwen/agent_evolving/sharing/keyword_extractor.py}.</p>
 */
public class KeywordExtractor {

    public static final LlmResilience.LLMInvokePolicy QUERY_KEYWORDS_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(1500.0, 4000.0, 5, 1.0, true);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final String LANGUAGE_CN = "cn";
    private static final String LANGUAGE_EN = "en";
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("keyword-extractor-io");
    private static final Map<String, String> PROMPTS = Map.of(
            LANGUAGE_CN,
            """
            你是一个检索关键词抽取器。下面是从对话中提取的关键信息片段，包含用户查询、工具执行结果（特别是出错的）等，请提取用于"跨用户经验检索"的关键词。

            ## 输入
            {excerpt}

            ## 当前 Skill 提示（可能为空）
            {skill_hint}

            ## 输出要求
            - 关键词 10-20 个，覆盖问题的核心概念，避免主语/口头语
            - 优先输出英文标识符 / 报错关键字；同时给出对应中文术语，提升召回
            - 同时给出 <=40 字的查询意图描述
            - 严格输出以下 JSON，不要任何其它内容（包括 Markdown 代码块）：

            {{
              "keywords": ["..."],
              "intent": "..."
            }}""",
            LANGUAGE_EN,
            """
            You are a retrieval keyword extractor. The text below is an excerpt from a conversation,
            containing user queries, tool execution results (especially failed ones), etc.
            Extract keywords useful for *cross-user experience retrieval*.

            ## Input
            {excerpt}

            ## Current skill hint (may be empty)
            {skill_hint}

            ## Output requirements
            - 10-15 keywords covering the core concepts; avoid pronouns / fillers
            - Prefer English code identifiers / error keywords; you may add the matching Chinese term to widen recall
            - Plus an intent string of <= 40 characters
            - Output ONLY this JSON (no Markdown, no explanation):

            {{
              "keywords": ["..."],
              "intent": "..."
            }}""");

    private Model llm;
    private String model;
    private String language;
    private LlmResilience.LLMInvokePolicy queryLlmPolicy;

    public KeywordExtractor() {
        this(null, null, LANGUAGE_CN, QUERY_KEYWORDS_LLM_POLICY);
    }

    public KeywordExtractor(Model llm, String model) {
        this(llm, model, LANGUAGE_CN, QUERY_KEYWORDS_LLM_POLICY);
    }

    public KeywordExtractor(
            Model llm,
            String model,
            String language,
            LlmResilience.LLMInvokePolicy queryLlmPolicy
    ) {
        this.llm = llm;
        this.model = model;
        this.language = PROMPTS.containsKey(language) ? language : LANGUAGE_CN;
        this.queryLlmPolicy = queryLlmPolicy != null ? queryLlmPolicy : QUERY_KEYWORDS_LLM_POLICY;
    }

    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    public static KeywordSummary parseFromOptimizerOutput(Object rawPatch) {
        List<String> keywords = new ArrayList<>();
        String summary = "";

        if (rawPatch instanceof EvolutionPatch patch) {
            keywords.addAll(cleanKeywords(patch.getKeywords()));
            summary = trimToEmpty(patch.getSummary());
        } else if (rawPatch instanceof Map<?, ?> rawMap) {
            Map<String, Object> data = normalizeMap(rawMap);
            Object rawKeywords = data.get("keywords");
            if (rawKeywords instanceof List<?> list) {
                keywords.addAll(cleanKeywords(list));
            }
            Object rawSummary = data.get("summary");
            if (rawSummary instanceof String text) {
                summary = text.strip();
            }
        }
        return new KeywordSummary(List.copyOf(keywords), summary);
    }

    public CompletionStage<QueryKeywords> extractQueryKeywords(String feedbackExcerpt) {
        return extractQueryKeywords(feedbackExcerpt, null);
    }

    public CompletionStage<QueryKeywords> extractQueryKeywords(String feedbackExcerpt, String skillHint) {
        String excerpt = trimToEmpty(feedbackExcerpt);
        if (excerpt.isEmpty()) {
            return CompletableFuture.completedFuture(queryKeywords(List.of(), "", ""));
        }

        if (llm == null || model == null || model.isBlank()) {
            Loggers.AGENT.debug("[KeywordExtractor] no LLM bound, skipping query keyword extraction");
            return CompletableFuture.completedFuture(queryKeywords(List.of(), head(excerpt, 40), excerpt));
        }

        Loggers.AGENT.info("[KeywordExtractor] query before keyword extraction:\n{}", excerpt);
        String prompt = PROMPTS.get(language)
                .replace("{excerpt}", excerpt)
                .replace("{skill_hint}", resolvedSkillHint(skillHint));

        return CompletableFuture.supplyAsync(() -> {
            String raw;
            try {
                raw = LlmResilience.invokeTextWithRetry(
                        llm,
                        model,
                        prompt,
                        queryLlmPolicy,
                        null,
                        0.2f,
                        null);
            } catch (BaseError exc) {
                Loggers.AGENT.warning("[KeywordExtractor] LLM call failed ({})", exc.getMessage());
                return queryKeywords(List.of(), head(excerpt, 40), excerpt);
            } catch (Exception exc) {
                Loggers.AGENT.warning("[KeywordExtractor] unexpected LLM error ({})", exc.getMessage());
                return queryKeywords(List.of(), head(excerpt, 40), excerpt);
            }

            Optional<Map<String, Object>> data = extractQueryJson(raw);
            if (data.isEmpty()) {
                Loggers.AGENT.warning("[KeywordExtractor] LLM JSON parse failed");
                return queryKeywords(List.of(), head(excerpt, 40), excerpt);
            }

            Object rawKeywords = data.get().get("keywords");
            List<String> keywords = rawKeywords instanceof List<?> list ? cleanKeywords(list) : List.of();
            Object rawIntent = data.get().get("intent");
            String intent = head(String.valueOf(rawIntent != null ? rawIntent : "").strip(), 80);
            return queryKeywords(keywords.size() > 20 ? keywords.subList(0, 20) : keywords, intent, excerpt);
        }, IO_EXECUTOR);
    }

    private String resolvedSkillHint(String skillHint) {
        String resolved = trimToEmpty(skillHint);
        if (!resolved.isEmpty()) {
            return resolved;
        }
        return LANGUAGE_CN.equals(language) ? "无" : "None";
    }

    private static Optional<Map<String, Object>> extractQueryJson(String raw) {
        String text = trimToEmpty(raw);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> direct = parseObject(text);
        if (direct.isPresent()) {
            return direct;
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return parseObject(matcher.group(0));
    }

    private static Optional<Map<String, Object>> parseObject(String raw) {
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return Optional.of(parsed);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static List<String> cleanKeywords(List<?> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (Object value : values) {
            String text = String.valueOf(value).strip();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private static QueryKeywords queryKeywords(List<String> keywords, String intent, String rawExcerpt) {
        QueryKeywords queryKeywords = new QueryKeywords();
        queryKeywords.setKeywords(keywords);
        queryKeywords.setIntent(intent);
        queryKeywords.setRawExcerpt(rawExcerpt);
        return queryKeywords;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static String head(String value, int maxLength) {
        String resolved = value != null ? value : "";
        return resolved.length() <= maxLength ? resolved : resolved.substring(0, maxLength);
    }

    /**
     * Parsed upload-path keyword and summary data.
     *
     * <p>Mirrors Python's {@code KeywordExtractor.parse_from_optimizer_output} return tuple in
     * {@code openjiuwen/agent_evolving/sharing/keyword_extractor.py}.</p>
     */
    public record KeywordSummary(List<String> keywords, String summary) {
        public KeywordSummary {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
            summary = summary != null ? summary : "";
        }
    }
}
