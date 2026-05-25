/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.llm;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM client interface for making chat completion requests.
 * <p>
 * Mirrors Python's LlmClient.
 */
public interface LlmClient {

    /**
     * Create a chat completion.
     *
     * @param request The request as a Map
     * @return CompletableFuture with the response as a Map
     */
    CompletableFuture<Map<String, Object>> createChatCompletion(Map<String, Object> request);

    /**
     * Get the client configuration.
     *
     * @return Configuration object
     */
    Object getConfig();

    /**
     * Close the client and release resources.
     */
    void close();
}