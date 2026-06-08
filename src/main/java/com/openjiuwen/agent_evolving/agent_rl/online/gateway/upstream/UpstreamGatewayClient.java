/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import java.util.Map;

/**
 * Interface for all gateway to upstream network calls.
 * <p>
 * Mirrors Python's {@code UpstreamGatewayClient} protocol in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/upstream/upstream_client.py}.
 */
public interface UpstreamGatewayClient {

    GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers);

    GatewayHttpResponse request(
            String method,
            String url,
            Map<String, Object> params,
            Map<String, String> headers,
            byte[] content
    );
}
