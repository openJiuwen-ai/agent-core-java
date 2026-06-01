/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.utils.ApiRequestUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat-completion-based reranker aligned with Python's ChatReranker behavior.
 *
 * <p>Mirrors Python's {@code ChatReranker} in
 * {@code openjiuwen.core.retrieval.reranker.chat_reranker}.</p>
 */
public class ChatReranker extends StandardReranker {

    private static final float EPSILON = 1e-6f;
    private static final String CHAT_ENDPOINT = "/chat/completions";
    private static final String DOC_TEMPLATE = "<Document>: %s";
    private static final String SYSTEM_INSTRUCT =
            "Judge whether the Document meets the requirements based on the Query and the Instruct provided. "
                    + "Note that the answer can only be \"yes\" or \"no\".";

    private final List<Integer> yesNoIds;

    public ChatReranker(RerankerConfig config) {
        this(config, 3, null, null);
    }

    public ChatReranker(RerankerConfig config,
                        int maxRetries,
                        Map<String, String> extraHeaders,
                        HttpClient httpClient) {
        super(config, maxRetries, extraHeaders, httpClient);
        List<Integer> ids = config.getYesNoIds();
        if (ids == null || ids.size() != 2) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "chat reranker require yes_no_ids to be specified in RerankerConfig");
        }
        this.yesNoIds = ids;
    }

    public boolean testCompatibility() {
        try {
            rerankScores("test", List.of("test"), Boolean.FALSE, Map.of());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    protected List<Double> rerankOrderedScores(String query,
                                               List<String> documents,
                                               Object instruct,
                                               Map<String, Object> options) {
        List<Double> scores = new ArrayList<>();
        for (String document : documents) {
            AssembleResult assembled = assembleParams(query, List.of(document), instruct, options);
            JsonNode response = ApiRequestUtils.postJsonWithRetry(
                    httpClient,
                    apiUrl + CHAT_ENDPOINT,
                    assembled.params(),
                    assembled.headers(),
                    Duration.ofMillis(Math.round(config.getTimeout() * 1000)),
                    maxRetries,
                    StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                    "ChatReranker");
            scores.add(parseChatScore(response));
        }
        return scores;
    }

    AssembleResult assembleParams(String query, Object documents, Object instruct, Map<String, Object> options) {
        if (!(documents instanceof List<?> list) || list.size() != 1) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "input to chat reranker must be a list[str | Document] of size 1");
        }
        Object item = list.get(0);
        String document;
        if (item instanceof String text) {
            document = text;
        } else if (item instanceof Document doc) {
            document = doc.getText();
        } else {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_INPUT_INVALID,
                    "input to chat reranker must be a list[str | Document] of size 1");
        }
        return new AssembleResult(new LinkedHashMap<>(headers), buildChatPayload(query, document, instruct, options));
    }

    private Map<String, Object> buildChatPayload(String query,
                                                 String document,
                                                 Object instruct,
                                                 Map<String, Object> options) {
        String content = buildQuery(query, instruct) + DOC_TEMPLATE.formatted(document);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_INSTRUCT),
                Map.of("role", "user", "content", content));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("messages", messages);
        payload.put("temperature", 0);
        payload.put("max_tokens", 1);
        payload.put("logprobs", true);
        payload.put("top_logprobs", 5);
        Map<String, Integer> logitBias = new LinkedHashMap<>();
        for (Integer id : yesNoIds) {
            logitBias.put(String.valueOf(id), 5);
        }
        payload.put("logit_bias", logitBias);
        payload.putAll(config.getExtraBody());
        if (options != null) {
            payload.putAll(options);
        }
        return payload;
    }

    private static double parseChatScore(JsonNode response) {
        JsonNode choice = response.path("choices").isArray() && response.path("choices").size() > 0
                ? response.path("choices").get(0)
                : null;
        if (choice == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                    "chat reranker response missing choices");
        }
        JsonNode logprobs = choice.path("logprobs");
        JsonNode topLogProbs = null;
        if (logprobs.path("content").isArray() && logprobs.path("content").size() > 0) {
            topLogProbs = logprobs.path("content").get(0).path("top_logprobs");
        } else if (logprobs.isArray() && logprobs.size() > 0) {
            topLogProbs = logprobs.get(0).path("top_logprobs");
        }
        if (topLogProbs == null || !topLogProbs.isArray()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED,
                    "the service does not support logprobs for chat reranker to function");
        }

        double yesScore = 0.0;
        double noScore = 0.0;
        for (JsonNode token : topLogProbs) {
            String text = token.path("token").asText("").trim().toLowerCase();
            double probability = Math.exp(token.path("logprob").asDouble(Double.NEGATIVE_INFINITY));
            if (text.startsWith("yes")) {
                yesScore = Math.max(yesScore, probability);
            } else if (text.startsWith("no")) {
                noScore = Math.max(noScore, probability);
            }
        }
        double total = yesScore + noScore;
        return (Math.abs(total - 0.0)) < EPSILON ? 0.0 : yesScore / total;
    }

    record AssembleResult(Map<String, String> headers, Map<String, Object> params) {
    }
}
