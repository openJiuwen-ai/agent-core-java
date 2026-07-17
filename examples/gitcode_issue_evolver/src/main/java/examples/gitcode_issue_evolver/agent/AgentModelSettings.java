/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.agent;

/**
 * Model-only settings exposed to the Issue worker Agent.
 *
 * @param provider model client provider
 * @param apiKey model API key
 * @param apiBase model API base URL
 * @param modelName model name
 * @param verifySsl whether TLS certificates must be verified
 * @since 0.1.12
 */
public record AgentModelSettings(String provider, String apiKey, String apiBase,
                                 String modelName, boolean verifySsl) {
    /**
     * Validate required model settings.
     */
    public AgentModelSettings {
        provider = requireText(provider, "model provider");
        apiKey = requireText(apiKey, "model API key");
        apiBase = requireText(apiBase, "model API base");
        modelName = requireText(modelName, "model name");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
