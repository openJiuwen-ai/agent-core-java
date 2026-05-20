/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.VerlVllmEngineHydraKwargs.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerlVllmEngineHydraKwargs {
    private boolean isEnableAutoToolChoice = true;
    private String toolCallParser = "hermes";
    private String servedModelName = "agentrl";

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnable_auto_tool_choice() { return isEnableAutoToolChoice(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTool_call_parser() { return getToolCallParser(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getServed_model_name() { return getServedModelName(); }
}
