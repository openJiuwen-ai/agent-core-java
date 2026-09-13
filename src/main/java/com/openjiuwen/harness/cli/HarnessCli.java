/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public class HarnessCli used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class HarnessCli {
    /**
     * runOnce.
     * 
     * @param opts opts
     * @param prompt prompt
     * @param outputFormat outputFormat
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> runOnce(CLIOptions opts, String prompt, String outputFormat) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(opts != null && opts.getWorkspace() != null ? opts.getWorkspace() : ".").build();
        var agent = HarnessFactory
                .createDeepAgent(AgentCard.builder().name("cli_agent").description("CLI agent").build(), config, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", prompt);
        result.put("output_format", outputFormat);
        TenantContext tenantCtx = buildTenantContext(opts);
        if (tenantCtx != null) {
            result.put("response", agent.invoke(Map.of("query", prompt), tenantCtx));
        } else {
            result.put("response", agent.invoke(Map.of("query", prompt)));
        }
        return result;
    }

    static TenantContext buildTenantContext(CLIOptions opts) {
        return Optional.ofNullable(opts)
                .map(CLIOptions::getTenantId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> TenantContext.builder().tenantId(id).build())
                .orElse(null);
    }

    /**
     * runChat.
     * 
     * @param opts opts
     * @return the result
     * @since 0.1.7
     */
    public SessionStore runChat(CLIOptions opts) {
        SessionStore store = new SessionStore();
        store.newSession("cli", opts != null ? opts.getModel() : null);
        return store;
    }
}
