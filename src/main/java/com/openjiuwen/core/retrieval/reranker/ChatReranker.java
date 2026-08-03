/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.OpenJiuwenVersion;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.base_reranker.Document;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Chat-based reranker client for chat-completion services that return logprobs.
 * <p>
 * Mirrors Python's {@code ChatReranker} in
 * {@code openjiuwen/core/retrieval/reranker/chat_reranker.py}.
 * </p>
 */
public class ChatReranker extends StandardReranker {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final java.util.concurrent.Executor IO_EXECUTOR = VirtualThreadSupport.newThreadPerTaskExecutor("chat-reranker-io");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String ENDPOINT = "/chat/completions";
    private static final String QUERY_TEMPLATE = "<Instruct>: %s\n<Query>: %s\n";
    private static final String DEFAULT_INSTRUCT =
            "Given a search query, retrieve relevant candidates that answer the query.";
    private static final String DOC_TEMPLATE = "<Document>: %s";
    private static final String SYSTEM_INSTRUCT =
            "Judge whether the Document meets the requirements based on the Query and the Instruct provided. ";

    private final RerankerConfig chatConfig;
    private final String modelName;
    private final String apiUrl;
    private final double timeout;
    private final int maxRetries;
    private final Map<String, String> headers;
    private final HttpClient httpClient;
    private final List<Integer> yesNoIds;
    private final LoggerProtocol logger;

    public ChatReranker(RerankerConfig config) {
        this(config, 3, 0.1d, null, null);
    }

    public ChatReranker(RerankerConfig config,
                        int maxRetries,
                        double retryWait,
                        Map<String, String> extraHeaders,
                        HttpClient httpClient) {
        super(config, maxRetries, retryWait, extraHeaders, httpClient);
        this.logger = Loggers.RETRIEVAL;
        this.logger.warning(
                "ChatReranker support is experimental in openJiuwen %s, you have been warned.",
                OpenJiuwenVersion.version()
        );
        if (!isValidYesNoIds(config.getYesNoIds())) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "error_msg",
                    "chat reranker require \"yes_no_ids\" to be specified in RerankerConfig"
            );
        }
        this.chatConfig = config;
        this.modelName = config.getModelName();
        this.apiUrl = normalizeBaseUrl(config.getApiBase());
        this.timeout = config.getTimeout();
        this.maxRetries = maxRetries;
        this.headers = new LinkedHashMap<>();
        this.headers.put("Content-Type", "application/json");
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            this.headers.put("Authorization", "Bearer " + config.getApiKey());
        }
        if (extraHeaders != null) {
            this.headers.putAll(extraHeaders);
        }
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.yesNoIds = List.copyOf(config.getYesNoIds());
    }

    public boolean testCompatibility() {
        try {
            rerankSync("test", List.of("test"), Boolean.FALSE, Map.of());
            return true;
        } catch (RuntimeException exception) {
            logger.error(
                    "The selected service does not support chat-completion-based reranking: %r",
                    exception
            );
            return false;
        }
    }

    @Override
    public CompletableFuture<Map<String, Double>> rerank(String query,
                                                         List<Object> doc,
                                                         Object instruct,
                                                         Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> rerankSync(query, doc, instruct, kwargs), IO_EXECUTOR);
    }

    @Override
    public Map<String, Double> rerankSync(String query,
                                          List<Object> doc,
                                          Object instruct,
                                          Map<String, Object> kwargs) {
        AssembleResult assembled = assembleParams(query, doc, instruct, kwargs);
        Map<String, Object> responseData = postWithRetry(assembled.params(), assembled.headers());
        return parseResponse(responseData, doc);
    }

    @Override
    protected Map<String, Double> parseResponse(Map<String, Object> responseData, List<Object> doc) {
        List<?> choices = getList(responseData.get("choices"));
        Map<?, ?> choice = choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> map)
                ? Map.of()
                : map;
        Object logprobs = choice.get("logprobs");
        if (!hasLogprobs(logprobs)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                    "error_msg",
                    "the service does not support logprobs for chat reranker to function"
            );
        }

        List<?> topLogprobs = extractTopLogprobs(logprobs);
        double yesScore = 0.0d;
        double noScore = 0.0d;
        for (Object tokenObject : topLogprobs) {
            if (!(tokenObject instanceof Map<?, ?> token)) {
                continue;
            }
            Object tokenValue = token.get("token");
            String tokenText = String.valueOf(tokenValue == null ? "" : tokenValue)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            double probability = Math.exp(asDouble(token.get("logprob"), Double.NEGATIVE_INFINITY));
            if (tokenText.startsWith("yes")) {
                yesScore = Math.max(yesScore, probability);
            } else if (tokenText.startsWith("no")) {
                noScore = Math.max(noScore, probability);
            }
        }

        double totalProbability = yesScore + noScore;
        String docId = documentId(doc);
        Map<String, Double> result = new LinkedHashMap<>();
        result.put(docId, totalProbability == 0.0d ? 0.0d : yesScore / totalProbability);
        return result;
    }

    AssembleResult assembleParams(String query, Object doc, Object instruct, Map<String, Object> kwargs) {
        List<String> documents = extractSingleDocument(doc);
        Map<String, Object> params = requestParams(query, documents, instruct);
        return new AssembleResult(new LinkedHashMap<>(headers), params);
    }

    Map<String, Object> requestParams(String query, List<String> documents, Object instruct) {
        String document = documents.get(0);
        String content = buildQuery(query, instruct) + DOC_TEMPLATE.formatted(document);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_INSTRUCT),
                Map.of("role", "user", "content", content)
        );

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", modelName);
        params.put("messages", messages);
        params.put("temperature", 0);
        params.put("max_tokens", 1);
        params.put("logprobs", true);
        params.put("top_logprobs", 5);
        Map<Integer, Integer> logitBias = new LinkedHashMap<>();
        for (Integer id : yesNoIds) {
            logitBias.put(id, 5);
        }
        params.put("logit_bias", logitBias);
        if (chatConfig.getExtraBody() != null) {
            params.putAll(chatConfig.getExtraBody());
        }
        return params;
    }

    private Map<String, Object> postWithRetry(Map<String, Object> params, Map<String, String> requestHeaders) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String body = OBJECT_MAPPER.writeValueAsString(params);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + ENDPOINT))
                        .timeout(Duration.ofMillis(Math.round(timeout * 1000)))
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
                HttpResponse<String> response = httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
                }
                if (attempt == maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed to get Reranker after " + maxRetries
                                    + " attempts: HTTP " + response.statusCode()
                    );
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt == maxRetries - 1) {
                    throw ErrorHelper.buildError(
                            StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                            "error_msg",
                            "Failed to get Reranker after " + maxRetries
                                    + " attempts: " + exception.getMessage()
                    );
                }
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED,
                "error_msg",
                "Unreachable code in ChatReranker"
        );
    }

    private static boolean isValidYesNoIds(List<Integer> yesNoIds) {
        return yesNoIds != null && yesNoIds.size() == 2 && yesNoIds.stream().allMatch(id -> id != null);
    }

    private static List<String> extractSingleDocument(Object doc) {
        if (!(doc instanceof List<?> values) || values.size() != 1) {
            throw invalidInput();
        }
        Object value = values.get(0);
        if (value instanceof String text) {
            return List.of(text);
        }
        if (value instanceof Document document) {
            return List.of(document.getText());
        }
        throw invalidInput();
    }

    private static RuntimeException invalidInput() {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                "error_msg",
                "input to chat reranker must be a list[str | Document] of size 1"
        );
    }

    private static String buildQuery(String query, Object instruct) {
        if (instruct instanceof String text && !text.isBlank()) {
            return QUERY_TEMPLATE.formatted(text, query);
        }
        return QUERY_TEMPLATE.formatted(DEFAULT_INSTRUCT, query);
    }

    private static boolean hasLogprobs(Object logprobs) {
        if (logprobs instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (logprobs instanceof List<?> list) {
            return !list.isEmpty();
        }
        return false;
    }

    private static List<?> extractTopLogprobs(Object logprobs) {
        if (logprobs instanceof Map<?, ?> map) {
            List<?> content = getList(map.get("content"));
            if (!content.isEmpty() && content.get(0) instanceof Map<?, ?> first) {
                return getList(first.get("top_logprobs"));
            }
        }
        if (logprobs instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            return getList(first.get("top_logprobs"));
        }
        return List.of();
    }

    private static String documentId(List<Object> doc) {
        Object first = doc.get(0);
        if (first instanceof Document document) {
            return document.getId();
        }
        return String.valueOf(first);
    }

    private static List<?> getList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static double asDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith(ENDPOINT)) {
            return normalized.substring(0, normalized.length() - ENDPOINT.length());
        }
        return normalized;
    }

    record AssembleResult(Map<String, String> headers, Map<String, Object> params) {
    }
}
