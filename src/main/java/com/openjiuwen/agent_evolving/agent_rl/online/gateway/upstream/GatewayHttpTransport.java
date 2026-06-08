/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Transport seam for gateway upstream HTTP operations.
 * <p>
 * Mirrors Python's injected HTTP transport role in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/upstream/upstream_client.py}.
 */
public interface GatewayHttpTransport {

    GatewayHttpResponse send(HttpRequest request) throws IOException, InterruptedException;
}
