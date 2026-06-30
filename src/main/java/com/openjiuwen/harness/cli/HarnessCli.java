/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class HarnessCli used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HarnessCli {
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> runOnce(CLIOptions opts, String prompt, String outputFormat) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(opts != null && opts.getWorkspace() != null ? opts.getWorkspace() : ".")
                .build();
        var agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("cli_agent").description("CLI agent").build(),
                config,
                null
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", prompt);
        result.put("output_format", outputFormat);
        result.put("response", agent.invoke(Map.of("query", prompt)));
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionStore runChat(CLIOptions opts) {
        SessionStore store = new SessionStore();
        store.newSession("cli", opts != null ? opts.getModel() : null);
        return store;
    }
}
