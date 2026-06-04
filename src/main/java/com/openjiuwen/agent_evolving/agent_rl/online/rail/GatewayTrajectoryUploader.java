/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP uploader for online-RL trajectory batches.
 * <p>
 * Mirrors Python's {@code TrajectoryUploader} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.uploader}.
 */
public class GatewayTrajectoryUploader implements TrajectoryUploader {

    private static final Logger LOGGER = Logger.getLogger(GatewayTrajectoryUploader.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UPLOAD_PATH = "/v1/gateway/upload/batch";

    private final String gatewayEndpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration timeout;

    public GatewayTrajectoryUploader(String gatewayEndpoint, String apiKey) {
        this(gatewayEndpoint, apiKey, HttpClient.newHttpClient(), Duration.ofSeconds(30));
    }

    GatewayTrajectoryUploader(String gatewayEndpoint, String apiKey, HttpClient httpClient, Duration timeout) {
        this.gatewayEndpoint = stripTrailingSlashes(gatewayEndpoint != null ? gatewayEndpoint : "");
        this.apiKey = apiKey != null ? apiKey : "";
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
    }

    @Override
    public void enqueue(OnlineRlBatch batch) {
        if (batch == null) {
            return;
        }
        postPayload(batch.toDict());
    }

    public void enqueue(RailV1Batch batch) {
        if (batch == null) {
            return;
        }
        postPayload(batch.toDict());
    }

    public String getGatewayEndpoint() {
        return gatewayEndpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    private void postPayload(Map<String, Object> payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayEndpoint + UPLOAD_PATH))
                    .timeout(timeout)
                    .header("Content-Type", "application/json");
            if (!apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                LOGGER.warning("[TrajectoryUploader] drop 4xx batch status="
                        + response.statusCode() + " body=" + abbreviate(response.body()));
            } else if (response.statusCode() >= 500) {
                LOGGER.warning("[TrajectoryUploader] upload failed status="
                        + response.statusCode() + " body=" + abbreviate(response.body()));
            }
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "[TrajectoryUploader] upload failed", exception);
        }
    }

    private static String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return JSON.writeValueAsString(payload);
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 200) {
            return value;
        }
        return value.substring(0, 200);
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
