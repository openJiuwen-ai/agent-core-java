/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.query_rewriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Query rewriter with template loading, JSON parsing, and optional context-aware compression.
 * 
 * @since 0.1.7
 */
public class QueryRewriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BaseModelClient llmClient;
    private final ModelContext context;
    private final int compressRange;
    private final String promptLang;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * QueryRewriter.
     * 
     * @param llmClient llmClient
     * @since 0.1.7
     */
    public QueryRewriter(BaseModelClient llmClient) {
        this(llmClient, null, 20, "zh");
    }

    /**
     * QueryRewriter.
     * 
     * @param llmClient llmClient
     * @param context context
     * @param compressRange compressRange
     * @param promptLang promptLang
     * @since 0.1.7
     */
    public QueryRewriter(BaseModelClient llmClient, ModelContext context, int compressRange, String promptLang) {
        this.llmClient = llmClient;
        this.context = context;
        this.compressRange = Math.max(1, compressRange);
        this.promptLang = promptLang == null || promptLang.isBlank() ? "zh" : promptLang;
    }

    /**
     * rewrite.
     * 
     * @param query query
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    public String rewrite(String query, List<RetrievalResult> results) {
        if (query == null || query.isBlank()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID,
                    "query must be non-empty");
        }
        if (llmClient == null) {
            return fallbackRewrite(query, results);
        }
        String contextText = results == null
                ? ""
                : results.stream().limit(5).map(RetrievalResult::getText).reduce("",
                        (left, right) -> left.isBlank() ? right : left + "\n" + right);
        String template = """
                Rewrite the query to improve retrieval precision.
                Original query:
                {query}

                Retrieved context:
                {history}

                Return JSON only:
                {"before":"","intention":"","standalone_query":"","references":{},"missing":[],
                "typo":[],"gibberish":[],"from_history":""}
                """;
        String prompt = fillTemplate(template, Map.of("query", query, "history", contextText));
        try {
            Map<String, Object> rewritten = parseAndRepairSchema(llmCall(prompt), rewriteSchema(),
                    StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID);
            String standalone = String.valueOf(rewritten.getOrDefault("standalone_query", ""));
            return standalone.isBlank() ? fallbackRewrite(query, results) : standalone;
        } catch (Exception ex) {
            return fallbackRewrite(query, results);
        }
    }

    /**
     * compress.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> compress(List<BaseMessage> messages) {
        ensureLlm();
        String rawText = msgToText(messages);
        String prompt = fillTemplate(loadTemplate("compression"), Map.of("history", rawText));
        String response = llmCall(prompt);
        return parseAndRepairSchema(response, compressSchema(), StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID);
    }

    /**
     * rewrite.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> rewrite(String query) {
        if (query == null || query.isBlank()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID,
                    "query must be non-empty");
        }
        ensureLlm();
        if (context == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID,
                    "context is required for context-aware rewrite");
        }
        List<BaseMessage> fullHistory = context.getMessages((Integer) null, true);
        if (fullHistory.size() >= compressRange) {
            try {
                Map<String, Object> compressed = compress(fullHistory);
                context.setMessages(List.of(new SystemMessage(writeJson(compressed))), true);
            } catch (RuntimeException ex) {
                context.setMessages(List.of(new SystemMessage(msgToText(fullHistory))), true);
            }
        }
        List<BaseMessage> history = context.getMessages(compressRange, true);
        String prompt =
            fillTemplate(loadTemplate("intention_completion"), Map.of("history", msgToText(history), "query", query));
        return parseAndRepairSchema(llmCall(prompt), rewriteSchema(),
                StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID);
    }

    /**
     * loadTemplate.
     * 
     * @param promptBase promptBase
     * @return the result
     * @since 0.1.7
     */
    public String loadTemplate(String promptBase) {
        String cacheKey = promptBase + "_" + promptLang;
        return templateCache.computeIfAbsent(cacheKey, key -> {
            String resource = "/com/openjiuwen/core/retrieval/query_rewriter/prompts/" + key + ".md";
            try (InputStream input = QueryRewriter.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND,
                            "prompt file not found: " + resource);
                }
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND,
                        "prompt file read failed: " + resource);
            }
        });
    }

    /**
     * msgToText.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public String msgToText(List<BaseMessage> messages) {
        List<BaseMessage> source = messages;
        if (source == null) {
            source = context == null ? List.of() : context.getMessages((Integer) null, true);
        }
        List<String> lines = new ArrayList<>(source.size());
        for (BaseMessage message : source) {
            lines.add(message.getRole() + ": " + stringify(message.getContent()));
        }
        return String.join("\n", lines).trim();
    }

    static String fillTemplate(String template, Map<String, String> values) {
        String output = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return output;
    }

    static String extractJson(String content) {
        if (content == null) {
            return "";
        }
        int objectStart = content.indexOf('{');
        int objectEnd = content.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return content.substring(objectStart, objectEnd + 1);
        }
        return "";
    }

    static Map<String, Object> parseLlmJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException first) {
            try {
                return MAPPER.readValue(repairJson(json), new TypeReference<>() {
                });
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
    }

    static Map<String, Object> schemaRepair(Map<String, Object> output, Map<String, Class<?>> schema) {
        if (output == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                    "output must be a dict");
        }
        Map<String, Object> repaired = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> entry : schema.entrySet()) {
            String field = entry.getKey();
            Class<?> type = entry.getValue();
            Object value = output.get(field);
            if (value == null) {
                value = defaultValue(type);
            }
            if ("typo".equals(field)) {
                value = normalizeTypo(value);
            } else if (!type.isInstance(value)) {
                value = coerce(type, field, value);
            }
            repaired.put(field, value);
        }
        return repaired;
    }

    /**
     * parseAndRepairSchema.
     * 
     * @param response response
     * @param schema schema
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> parseAndRepairSchema(String response, Map<String, Class<?>> schema, StatusCode status) {
        String json = extractJson(response);
        Map<String, Object> parsed = parseLlmJson(json);
        if (parsed == null) {
            throw RetrievalExceptions.error(status, "LLM output is not valid JSON");
        }
        return schemaRepair(parsed, schema);
    }

    /**
     * llmCall.
     * 
     * @param prompt prompt
     * @return the result
     * @since 0.1.7
     */
    private String llmCall(String prompt) {
        try {
            AssistantMessage response = llmClient.invoke(List.of(Map.of("role", "user", "content", prompt)), null, 0.0f,
                    null, null, null, null, null, null, Map.of());
            if (response == null) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED,
                        "LLM returned null");
            }
            return response.getContentAsString();
        } catch (Exception ex) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED, ex.getMessage());
        }
    }

    /**
     * ensureLlm.
     * 
     * @since 0.1.7
     */
    private void ensureLlm() {
        if (llmClient == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED,
                    "llm_client is required");
        }
    }

    /**
     * fallbackRewrite.
     * 
     * @param query query
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    private static String fallbackRewrite(String query, List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return query;
        }
        String first = results.get(0).getText();
        return query + " " + first;
    }

    /**
     * stringify.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String string) {
            return string;
        }
        return writeJson(value);
    }

    /**
     * writeJson.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    /**
     * repairJson.
     * 
     * @param json json
     * @return the result
     * @since 0.1.7
     */
    private static String repairJson(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    /**
     * defaultValue.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static Object defaultValue(Class<?> type) {
        if (type == String.class) {
            return "";
        }
        if (type == List.class) {
            return new ArrayList<>();
        }
        if (type == Map.class) {
            return new LinkedHashMap<>();
        }
        throw RetrievalExceptions.error(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                "unsupported field type: " + type.getSimpleName());
    }

    /**
     * coerce.
     * 
     * @param type type
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Object coerce(Class<?> type, String field, Object value) {
        if (type == String.class) {
            return stringify(value);
        }
        if (type == List.class) {
            return value instanceof List<?> list ? list : List.of(value);
        }
        if (type == Map.class) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> repaired = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    repaired.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return repaired;
            }
            if (value instanceof String string) {
                Map<String, Object> parsed = parseLlmJson(string);
                if (parsed != null) {
                    return parsed;
                }
            }
            return Map.of(field, value);
        }
        return value;
    }

    /**
     * normalizeTypo.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> normalizeTypo(Object value) {
        List<?> rawList = value instanceof List<?> list ? list : List.of(value);
        List<Map<String, Object>> repaired = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> itemMap;
            if (item instanceof Map<?, ?> map) {
                itemMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    itemMap.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else {
                itemMap = new LinkedHashMap<>();
                itemMap.put("original", stringify(item));
                itemMap.put("corrected", "");
                itemMap.put("reason", "");
            }
            itemMap.put("original", stringify(itemMap.get("original")));
            itemMap.put("corrected", stringify(itemMap.get("corrected")));
            itemMap.put("reason", stringify(itemMap.get("reason")));
            repaired.add(itemMap);
        }
        return repaired;
    }

    /**
     * compressSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Class<?>> compressSchema() {
        return Map.of("theme", List.class, "summary", String.class);
    }

    /**
     * rewriteSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Class<?>> rewriteSchema() {
        Map<String, Class<?>> schema = new LinkedHashMap<>();
        schema.put("before", String.class);
        schema.put("intention", String.class);
        schema.put("standalone_query", String.class);
        schema.put("references", Map.class);
        schema.put("missing", List.class);
        schema.put("typo", List.class);
        schema.put("gibberish", List.class);
        schema.put("from_history", String.class);
        return schema;
    }
}
