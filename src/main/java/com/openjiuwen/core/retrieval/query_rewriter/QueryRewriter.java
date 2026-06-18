/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.query_rewriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Query Rewriter (QR): rewrites short user query into a standalone query suitable for retrieval.
 *
 * <p>Mirrors Python's {@code QueryRewriter} in
 * {@code openjiuwen/core/retrieval/query_rewriter/query_rewriter.py}.</p>
 */
public class QueryRewriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final String PROMPT_RESOURCE_DIR = "/com/openjiuwen/core/retrieval/query_rewriter/prompts/";

    private final ModelConfig modelConfig;
    private final int compressRange;
    private final ModelContext context;
    private final String promptLang;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Model llm;
    private final JsonOutputParser jsonOutputParser = new JsonOutputParser();

    public QueryRewriter(ModelConfig cfg, ModelContext ctx) {
        this(cfg, ctx, 20, "zh");
    }

    public QueryRewriter(ModelConfig cfg, ModelContext ctx, int compressRange, String promptLang) {
        this(cfg, ctx, compressRange, promptLang, createModel(cfg));
    }

    QueryRewriter(Model llm, ModelContext ctx, int compressRange, String promptLang) {
        this(null, ctx, compressRange, promptLang, llm);
    }

    private QueryRewriter(ModelConfig cfg, ModelContext ctx, int compressRange, String promptLang, Model llm) {
        this.modelConfig = cfg;
        this.context = ctx;
        this.compressRange = Math.max(compressRange, 1);
        this.promptLang = promptLang == null || promptLang.isBlank() ? "zh" : promptLang;
        this.llm = llm;
    }

    public ModelConfig getModelConfig() {
        return modelConfig;
    }

    public int getCompressRange() {
        return compressRange;
    }

    public ModelContext getContext() {
        return context;
    }

    public String getPromptLang() {
        return promptLang;
    }

    /**
     * Load prompt template file with language suffix from bundled query rewriter resources.
     *
     * @param promptBase template base name, for example {@code compression}
     * @return template text
     */
    public String loadTemplate(String promptBase) {
        String cacheKey = promptBase + "_" + promptLang;
        return templateCache.computeIfAbsent(cacheKey, key -> {
            String resource = PROMPT_RESOURCE_DIR + key + ".md";
            try (InputStream input = QueryRewriter.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw queryRewriterError(
                            StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND,
                            "prompt file not found: " + resource);
                }
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw queryRewriterError(
                        StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND,
                        "prompt file read failed: " + resource + ", reason: " + exception,
                        exception);
            }
        });
    }

    /**
     * Format message list into plain text {@code role: content} lines.
     *
     * @param messages messages to format; when null, reads the full context history
     * @return formatted text
     */
    public String msgToText(List<BaseMessage> messages) {
        List<BaseMessage> source = messages;
        if (source == null) {
            source = context == null ? List.of() : safeMessages(context.getMessages(null, true));
        }
        List<String> lines = new ArrayList<>(source.size());
        for (BaseMessage message : source) {
            lines.add(message.getRole() + ": " + stringifyContent(message.getContent()));
        }
        return String.join("\n", lines).trim();
    }

    /**
     * Use LLM to compress a segment of history messages into structured summary JSON.
     *
     * @param raw message list to compress
     * @return repaired JSON object with {@code theme} and {@code summary}
     */
    public Map<String, Object> compress(List<BaseMessage> raw) {
        String rawText = msgToText(raw);
        String compressionPrompt = fillTemplate(loadTemplate("compression"), Map.of("history", rawText));
        AssistantMessage compressed = invokeLlm(List.of(new SystemMessage(compressionPrompt)));
        String contentJson = extractJson(compressed.getContentAsString().trim());
        Map<String, Object> compressedJson = parseAndRepairLlmJson(
                contentJson,
                compressSchema(),
                "compress");
        LOGGER.info("Compress completed: {}", compressedJson);
        return compressedJson;
    }

    /**
     * Rewrite user query into a standalone query suitable for retrieval in the current session context.
     *
     * @param query user's raw query
     * @return repaired rewrite JSON object
     */
    public Map<String, Object> rewrite(String query) {
        if (query == null || query.isBlank()) {
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID,
                    "query must be a non-empty string");
        }
        if (context == null) {
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID,
                    "context is required for query rewriting");
        }

        String promptTemplate = loadTemplate("intention_completion");
        List<BaseMessage> historyFull = safeMessages(context.getMessages(null, true));

        if (historyFull.size() >= compressRange) {
            try {
                Map<String, Object> compressedJson = compress(historyFull);
                String historyText = writeJson(compressedJson);
                context.setMessages(List.of(new SystemMessage(historyText, "compressed_history")), true);
            } catch (BaseError exception) {
                LOGGER.warning(
                        "Query rewriter compress failed, falling back to original history: {}",
                        exception.toString());
                String historyText = msgToText(historyFull);
                context.setMessages(List.of(new SystemMessage(historyText, "original_history")), true);
            }
        }

        List<BaseMessage> historyForRewrite = safeMessages(context.getMessages(compressRange, true));
        String historyText = msgToText(historyForRewrite);
        String completionPrompt = fillTemplate(
                promptTemplate,
                Map.of("history", historyText, "query", query));
        AssistantMessage rewrote = invokeLlm(List.of(new SystemMessage(completionPrompt)));
        String contentJson = extractJson(rewrote.getContentAsString().trim());
        return parseAndRepairLlmJson(contentJson, rewriteSchema(), "rewrite");
    }

    static String fillTemplate(String template, Map<String, String> values) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return output;
    }

    static String extractJson(String modelOutput) {
        if (modelOutput == null) {
            return "";
        }
        int start = modelOutput.indexOf('{');
        int end = modelOutput.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            LOGGER.debug("No JSON object found in model output");
            return "";
        }
        LOGGER.debug("JSON object from output extracted");
        return modelOutput.substring(start, end + 1);
    }

    static Map<String, Object> parseLlmJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> parsed = readJsonObject(json);
        if (parsed != null) {
            return parsed;
        }
        return readJsonObject(repairJson(json));
    }

    static String forceString(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            try {
                return OBJECT_MAPPER.writeValueAsString(normalizeMap(map));
            } catch (JsonProcessingException exception) {
                LOGGER.debug("json serialization failed when forcing value to string, using String.valueOf");
                return String.valueOf(raw);
            }
        }
        return String.valueOf(raw);
    }

    static List<Object> forceList(Object raw) {
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        List<Object> result = new ArrayList<>();
        result.add(raw);
        return result;
    }

    static Map<String, Object> forceJson(String key, Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (raw instanceof String string) {
            Object parsed = readAnyJson(string);
            if (parsed instanceof Map<?, ?> map) {
                return normalizeMap(map);
            }
            if (parsed != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put(key, parsed);
                return result;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, raw);
        return result;
    }

    static Map<String, Object> schemaRepair(Map<String, Object> output, Map<String, Class<?>> outputSchema) {
        if (output == null) {
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                    "output must be a dict");
        }

        Map<String, Object> repaired = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> entry : outputSchema.entrySet()) {
            String field = entry.getKey();
            Class<?> expectedType = entry.getValue();
            Object value = output.get(field);

            if (value == null) {
                value = defaultValue(field, expectedType);
            }

            if ("typo".equals(field)) {
                value = repairTypo(value);
            }

            if (!expectedType.isInstance(value)) {
                value = coerce(field, value, expectedType);
            }
            repaired.put(field, value);
        }
        LOGGER.debug("Schema check and repair done");
        return repaired;
    }

    static Map<String, Class<?>> compressSchema() {
        Map<String, Class<?>> schema = new LinkedHashMap<>();
        schema.put("theme", List.class);
        schema.put("summary", String.class);
        return schema;
    }

    static Map<String, Class<?>> rewriteSchema() {
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

    private static Model createModel(ModelConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        BaseModelInfo modelInfo = cfg.getModelInfo() == null ? new BaseModelInfo() : cfg.getModelInfo();
        Map<String, Object> extraFields = modelInfo.getExtraFields() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(modelInfo.getExtraFields());
        String sslCert = stringValue(extraFields.get("ssl_cert"));
        boolean verifySsl = booleanValue(extraFields.get("verify_ssl"), true);
        if (verifySsl && sslCert == null) {
            verifySsl = false;
        }

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(cfg.getModelProvider())
                .apiKey(modelInfo.getApiKey())
                .apiBase(modelInfo.getApiBase())
                .timeout(modelInfo.getTimeout())
                .verifySsl(verifySsl)
                .sslCert(sslCert)
                .customHeaders(modelInfo.getCustomHeaders())
                .extraFields(extraFields)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelInfo.getModelName())
                .temperature(0.0D)
                .topP(modelInfo.getTopP())
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private AssistantMessage invokeLlm(List<BaseMessage> messages) {
        if (llm == null) {
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED,
                    "llm is required");
        }
        try {
            AssistantMessage response = llm.invoke(messages, invokeOptions()).toCompletableFuture().join();
            if (response == null) {
                throw queryRewriterError(
                        StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED,
                        "LLM returned null");
            }
            return response;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED,
                    String.valueOf(cause.getMessage()),
                    cause);
        } catch (BaseError exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED,
                    String.valueOf(exception.getMessage()),
                    exception);
        }
    }

    private ModelInvokeOptions invokeOptions() {
        ModelInvokeOptions.ModelInvokeOptionsBuilder builder = ModelInvokeOptions.builder();
        if (modelConfig != null && modelConfig.getModelInfo() != null) {
            builder.model(modelConfig.getModelInfo().getModelName());
        }
        if (llm.getModelConfig() != null) {
            builder.temperature((float) llm.getModelConfig().getTemperature());
        }
        return builder.build();
    }

    private Map<String, Object> parseAndRepairLlmJson(
            String contentJson,
            Map<String, Class<?>> schema,
            String phase) {
        try {
            Map<String, Object> parsedJson = parseLlmJson(contentJson);
            if (parsedJson == null) {
                Object parserResult = jsonOutputParser.parse(contentJson).toCompletableFuture().join();
                if (parserResult instanceof Map<?, ?> map) {
                    parsedJson = normalizeMap(map);
                }
            }
            if (parsedJson == null) {
                throw queryRewriterError(
                        StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                        "LLM " + phase + " output is not valid JSON (parse returned non-dict); content: "
                                + abbreviate(contentJson));
            }
            return schemaRepair(parsedJson, schema);
        } catch (BaseError exception) {
            throw exception;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                    "LLM " + phase + " output parsing failed: " + cause + "; content: " + abbreviate(contentJson),
                    cause);
        } catch (RuntimeException exception) {
            throw queryRewriterError(
                    StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                    "LLM " + phase + " output parsing failed: " + exception + "; content: "
                            + abbreviate(contentJson),
                    exception);
        }
    }

    private static Object defaultValue(String field, Class<?> expectedType) {
        if (expectedType == String.class) {
            return "";
        }
        if (expectedType == List.class) {
            return new ArrayList<>();
        }
        if (expectedType == Map.class) {
            return new LinkedHashMap<>();
        }
        throw queryRewriterError(
                StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                "Cannot auto-repair field '" + field + "' with type " + expectedType.getSimpleName());
    }

    private static Object coerce(String field, Object value, Class<?> expectedType) {
        if (expectedType == String.class) {
            return forceString(value);
        }
        if (expectedType == List.class) {
            return forceList(value);
        }
        if (expectedType == Map.class) {
            return forceJson(field, value);
        }
        throw queryRewriterError(
                StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID,
                "Field '" + field + "' expected type " + expectedType.getSimpleName()
                        + ", got " + (value == null ? "null" : value.getClass().getSimpleName()));
    }

    private static List<Map<String, Object>> repairTypo(Object value) {
        List<Object> rawList = forceList(value);
        List<Map<String, Object>> repairedTypo = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> itemMap = item instanceof Map<?, ?> map
                    ? normalizeMap(map)
                    : forceJson("typo", item);

            Map<String, Object> repairedItem = new LinkedHashMap<>();
            for (String key : List.of("original", "corrected", "reason")) {
                Object rawValue = itemMap.get(key);
                if (rawValue == null) {
                    rawValue = "";
                } else if (!(rawValue instanceof String)) {
                    rawValue = forceString(rawValue);
                }
                repairedItem.put(key, rawValue);
            }
            repairedTypo.add(repairedItem);
        }
        return repairedTypo;
    }

    private static List<BaseMessage> safeMessages(List<BaseMessage> messages) {
        return messages == null ? List.of() : new ArrayList<>(messages);
    }

    private static String stringifyContent(Object content) {
        if (content instanceof Map<?, ?> || content instanceof List<?>) {
            return writeJson(content);
        }
        return content == null ? "" : String.valueOf(content);
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private static Map<String, Object> readJsonObject(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object readAnyJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String repairJson(String json) {
        return json == null ? null : json.replaceAll(",\\s*([}\\]])", "$1");
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static BaseError queryRewriterError(StatusCode status, String message) {
        return ErrorHelper.buildError(status, "error_msg", message);
    }

    private static BaseError queryRewriterError(StatusCode status, String message, Throwable cause) {
        return ErrorHelper.buildError(status, null, null, cause, Map.of("error_msg", message));
    }
}
