/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.HttpUpstreamGatewayClient;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.JavaNetGatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.RetryPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Judge scoring service with voting and retry logic.
 * <p>
 * Mirrors Python's judge server hosting in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/judge_server.py}.
 */
public final class JudgeServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger("online_rl.judge_server");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JudgeConfig config;
    private final HttpServer server;
    private final Function<ScoreRequest, ScoreResponse> scoreHandler;

    public JudgeServer(String host,
                       int port,
                       JudgeConfig config,
                       Function<ScoreRequest, ScoreResponse> scoreHandler) throws IOException {
        this.config = Objects.requireNonNull(config, "config");
        this.scoreHandler = Objects.requireNonNull(scoreHandler, "scoreHandler");
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/healthz", this::handleHealthz);
        this.server.createContext("/score", this::handleScore);
        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public static JudgeServer start(String host, int port, JudgeConfig config) throws IOException {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000L, (long) (config.getTimeout() * 1000))))
                .build();
        RetryPolicy retryPolicy = new RetryPolicy(
                Math.max(0, config.getMaxRetries()),
                Math.max(0.0d, config.getRetryBackoffSec()),
                Math.max(0.0d, config.getRetryBackoffSec())
        );
        HttpUpstreamGatewayClient upstreamClient = new HttpUpstreamGatewayClient(
                new JavaNetGatewayHttpTransport(httpClient),
                config.getLlmUrl(),
                retryPolicy,
                Duration.ofMillis(Math.max(1000L, (long) (config.getTimeout() * 1000)))
        );
        JudgeEvaluator evaluator = new JudgeEvaluator(upstreamClient);
        JudgeServer server = new JudgeServer(host, port, config, request -> evaluator.evaluateJudgeScores(
                config,
                request.responseText(),
                request.instructionText(),
                request.followupUserFeedback(),
                request.sessionId(),
                request.turnNum()
        ));
        server.start();
        return server;
    }

    public static JudgeServer startFromArgs(String[] args) throws IOException {
        CliArgs cliArgs = CliArgs.parse(args);
        JudgeConfig config = new JudgeConfig(cliArgs.llmUrl, cliArgs.modelId);
        config.setApiKey(cliArgs.apiKey);
        config.setNumVotes(Math.max(1, cliArgs.numVotes));
        config.setTemperature(cliArgs.temperature);
        config.setMaxCompletionTokens(Math.max(1, cliArgs.maxCompletionTokens));
        config.setTimeout(Math.max(1.0d, cliArgs.timeout));
        config.setExpectedApiKey(cliArgs.judgeApiKey);
        config.setMaxRetries(2);
        config.setRetryBackoffSec(0.2d);
        return start(cliArgs.host, cliArgs.port, config);
    }

    public void start() {
        server.start();
        LOG.info("Judge server started on {} with model {}", server.getAddress(), config.getModelId());
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        JudgeServer server = startFromArgs(args);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Thread.currentThread().join();
    }

    private void handleHealthz(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("detail", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, Map.of(
                "ok", true,
                "model", config.getModelId(),
                "num_votes", config.getNumVotes()
        ));
    }

    private void handleScore(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("detail", "method not allowed"));
            return;
        }
        String authError = validateAuthorization(exchange);
        if (authError != null) {
            int status = "missing bearer token".equals(authError) ? 401 : 403;
            sendJson(exchange, status, Map.of("detail", authError));
            return;
        }
        ScoreRequest request;
        try {
            request = readScoreRequest(exchange);
        } catch (Exception exception) {
            sendJson(exchange, 400, Map.of("detail", "invalid score request: " + exception.getMessage()));
            return;
        }
        try {
            sendJson(exchange, 200, scoreHandler.apply(request).toMap());
        } catch (Exception exception) {
            LOG.warn("Judge score request failed", exception);
            sendJson(exchange, 500, Map.of("detail", String.valueOf(exception.getMessage())));
        }
    }

    private String validateAuthorization(HttpExchange exchange) {
        String expectedApiKey = config.getExpectedApiKey();
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            return null;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            return "missing bearer token";
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!expectedApiKey.equals(token)) {
            return "invalid bearer token";
        }
        return null;
    }

    private static ScoreRequest readScoreRequest(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(inputStream, MAP_TYPE);
            return new ScoreRequest(
                    String.valueOf(payload.getOrDefault("response_text", "")),
                    String.valueOf(payload.getOrDefault("instruction_text", "")),
                    String.valueOf(payload.getOrDefault("followup_user_feedback", "")),
                    String.valueOf(payload.getOrDefault("session_id", "")),
                    payload.get("turn_num") instanceof Number number ? number.intValue() : 0
            );
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(new LinkedHashMap<>(payload));
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private record CliArgs(
            String host,
            int port,
            String llmUrl,
            String modelId,
            String apiKey,
            String judgeApiKey,
            int numVotes,
            double temperature,
            int maxCompletionTokens,
            double timeout,
            String logLevel
    ) {
        static CliArgs parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("unexpected argument: " + arg);
                }
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("missing value for " + arg);
                }
                values.put(arg, args[++index]);
            }
            String host = values.getOrDefault("--host", "127.0.0.1");
            String llmUrl = required(values, "--llm-url");
            String modelId = required(values, "--model-id");
            int port = Integer.parseInt(required(values, "--port"));
            return new CliArgs(
                    host,
                    port,
                    llmUrl,
                    modelId,
                    values.getOrDefault("--api-key", ""),
                    values.getOrDefault("--judge-api-key", ""),
                    Integer.parseInt(values.getOrDefault("--num-votes", "1")),
                    Double.parseDouble(values.getOrDefault("--temperature", "0.1")),
                    Integer.parseInt(values.getOrDefault("--max-completion-tokens", "4096")),
                    Double.parseDouble(values.getOrDefault("--timeout", "120.0")),
                    values.getOrDefault("--log-level", "info")
            );
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(key + " is required");
            }
            return value;
        }
    }
}
