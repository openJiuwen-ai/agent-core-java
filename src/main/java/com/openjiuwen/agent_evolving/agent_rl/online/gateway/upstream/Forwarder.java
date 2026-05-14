/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayCommon;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM request forwarder for upstream chat completions.
 * <p>
 * Mirrors Python's {@code Forwarder} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.forwarder}.
 */
public class Forwarder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final UpstreamGatewayClient upstreamClient;
    private final String modelId;

    public Forwarder(UpstreamGatewayClient upstreamClient, String modelId) {
        this.upstreamClient = upstreamClient;
        this.modelId = modelId;
    }

    public Map<String, Object> cleanBody(Map<String, Object> body) {
        Map<String, Object> sendBody = new LinkedHashMap<>();
        if (body != null) {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (!GatewayCommon.NON_STANDARD_BODY_KEYS.contains(entry.getKey())) {
                    sendBody.put(entry.getKey(), entry.getValue());
                }
            }
        }
        sendBody.put("stream", false);
        sendBody.remove("stream_options");
        sendBody.putIfAbsent("model", modelId);
        sendBody.put("logprobs", true);
        sendBody.put("top_logprobs", 1);
        return sendBody;
    }

    public Map<String, Object> forward(Map<String, Object> body, Map<String, String> headers) {
        GatewayHttpResponse response = upstreamClient.postChatCompletions(cleanBody(body), headers != null ? headers : Map.of());
        if (response.statusCode() >= 400) {
            String detail = response.body() != null ? response.body() : "";
            detail = detail.length() > 500 ? detail.substring(0, 500) : detail;
            throw new GatewayForwardingException(502, "upstream error: " + detail);
        }
        try {
            return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse upstream response body", exception);
        }
    }
}
