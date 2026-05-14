/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Transport seam for gateway upstream HTTP operations.
 * <p>
 * Mirrors Python's injected HTTPX async client role in a synchronous Java form.
 */
public interface GatewayHttpTransport {

    GatewayHttpResponse send(HttpRequest request) throws IOException, InterruptedException;
}
