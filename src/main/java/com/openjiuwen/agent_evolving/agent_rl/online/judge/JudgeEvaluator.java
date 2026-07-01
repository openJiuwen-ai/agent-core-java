/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Judge evaluation helper.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/evaluator.py}.
 */
public class JudgeEvaluator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern TOOL_CALL_BLOCK = Pattern.compile("<tool_call>.*?</tool_call>", Pattern.DOTALL);
    private static final Pattern OPEN_TAG = Pattern.compile("<[a-zA-Z_][^>]{0,80}>");
    private static final Pattern CLOSE_TAG = Pattern.compile("</[a-zA-Z_][^>]{0,80}>");
    private static final List<Integer> RETRYABLE_STATUS_CODES = List.of(429, 500, 502, 503, 504);

    private final UpstreamGatewayClient upstreamGatewayClient;
    private final Sleeper sleeper;

    public JudgeEvaluator(UpstreamGatewayClient upstreamGatewayClient) {
        this(upstreamGatewayClient, millis -> {
            if (millis > 0L) {
                Thread.sleep(millis);
            }
        });
    }

    JudgeEvaluator(UpstreamGatewayClient upstreamGatewayClient, Sleeper sleeper) {
        this.upstreamGatewayClient = upstreamGatewayClient;
        this.sleeper = sleeper;
    }

    public List<Map<String, String>> buildJudgeMessages(String responseText, String instructionText,
                                                        String followupUserFeedback) {
        String prompt = JudgeScoring.buildJudgePrompt(
                sanitizeText(instructionText),
                sanitizeText(responseText),
                sanitizeText(followupUserFeedback)
        );
        return List.of(Map.of("role", "user", "content", prompt));
    }

    public ScoreResponse evaluateJudgeScores(JudgeEvaluatorConfig config,
                                             String responseText,
                                             String instructionText,
                                             String followupUserFeedback,
                                             String sessionId,
                                             int turnNum) {
        List<Map<String, String>> messages = buildJudgeMessages(responseText, instructionText, followupUserFeedback);
        int voteCount = Math.max(1, config.getNumVotes());
        List<Double> votes = new ArrayList<>();
        List<Object> details = new ArrayList<>();
        for (int voteId = 0; voteId < voteCount; voteId++) {
            Map<String, Object> vote = queryVote(config, messages, voteId);
            Object overall = vote.getOrDefault("overall", 5.0);
            votes.add(overall instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(overall)));
            details.add(vote);
        }
        double avgOverall = votes.stream().mapToDouble(Double::doubleValue).average().orElse(5.0);
        return new ScoreResponse(
                JudgeScoring.normalizeOverallScore(avgOverall),
                avgOverall,
                votes,
                voteCount == 1 ? details.get(0) : details,
                config.getModelId(),
                sessionId != null ? sessionId : "",
                turnNum
        );
    }

    private Map<String, Object> queryVote(JudgeEvaluatorConfig config, List<Map<String, String>> messages, int voteId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModelId());
        payload.put("messages", messages);
        payload.put("temperature", config.getTemperature());
        payload.put("max_tokens", config.getMaxCompletionTokens());
        payload.put("stream", false);

        Map<String, Object> data = postChatCompletion(config, payload, voteId);
        Map<String, Object> choice = firstMap(data, "choices");
        String content = flattenContent(firstMap(choice, "message").get("content"));
        Map<String, Object> scores = JudgeScoring.parseJudgeScores(content, false);

        if (scores == null && "length".equals(String.valueOf(choice.getOrDefault("finish_reason", "")))) {
            Map<String, Object> retryPayload = new LinkedHashMap<>(payload);
            retryPayload.put("temperature", 0.0);
            retryPayload.put("max_tokens", Math.max(config.getMaxCompletionTokens(), 1024));
            Map<String, Object> retryData = postChatCompletion(config, retryPayload, voteId);
            Map<String, Object> retryChoice = firstMap(retryData, "choices");
            String retryContent = flattenContent(firstMap(retryChoice, "message").get("content"));
            Map<String, Object> retryScores = JudgeScoring.parseJudgeScores(retryContent, false);
            if (retryScores != null) {
                return retryScores;
            }
            content = retryContent;
        }

        if (scores == null) {
            return Map.of("overall", 5.0, "error", "unparseable", "content", content);
        }
        return scores;
    }

    private Map<String, Object> postChatCompletion(JudgeEvaluatorConfig config, Map<String, Object> payload, int voteId) {
        String url = trimTrailingSlash(config.getLlmUrl()) + "/v1/chat/completions";
        Map<String, String> headers = buildHeaders(config.getApiKey());
        int attempt = 0;
        while (true) {
            GatewayHttpResponse response;
            try {
                response = upstreamGatewayClient.request("POST", url, Map.of(), headers, OBJECT_MAPPER.writeValueAsBytes(payload));
            } catch (Exception exception) {
                if (attempt >= config.getMaxRetries()) {
                    throw new RuntimeException(exception);
                }
                attempt += 1;
                sleep(config.getRetryBackoffSec() * attempt);
                continue;
            }

            if (RETRYABLE_STATUS_CODES.contains(response.statusCode()) && attempt < config.getMaxRetries()) {
                attempt += 1;
                sleep(config.getRetryBackoffSec() * attempt);
                continue;
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Judge upstream vote " + voteId + " failed: HTTP " + response.statusCode());
            }
            try {
                return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to parse judge upstream response", exception);
            }
        }
    }

    private void sleep(double seconds) {
        try {
            sleeper.sleep((long) (seconds * 1000L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    public static Map<String, String> buildHeaders(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? Map.of() : Map.of("Authorization", "Bearer " + apiKey);
    }

    public static String sanitizeText(String text) {
        String safe = text != null ? text : "";
        safe = TOOL_CALL_BLOCK.matcher(safe).replaceAll("[tool_call block]");
        safe = OPEN_TAG.matcher(safe).replaceAll("[tag]");
        return CLOSE_TAG.matcher(safe).replaceAll("[/tag]");
    }

    public static String flattenContent(Object content) {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && "text".equals(String.valueOf(map.get("type")))) {
                    if (!builder.isEmpty()) {
                        builder.append(' ');
                    }
                    Object textValue = map.get("text");
                    builder.append(textValue != null ? String.valueOf(textValue) : "");
                }
            }
            return builder.toString().trim();
        }
        return content == null ? "" : String.valueOf(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstMap(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String trimTrailingSlash(String value) {
        String safe = value != null ? value : "";
        while (safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
