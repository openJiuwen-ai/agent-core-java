/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.JudgeConfig.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeConfig extends VLLMServiceConfig {
    private boolean reuseInferenceIfSameModel = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public JudgeConfig() {
        setGpuIds("2,3");
        setHealthTimeout(600.0);
        setEnv(new LinkedHashMap<>());
        setExtraArgs(new ArrayList<>(List.of(
                "--max-model-len",
                "8192",
                "--gpu-memory-utilization",
                "0.85",
                "--max-num-seqs",
                "16"
        )));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isReuse_inference_if_same_model() {
        return isReuseInferenceIfSameModel();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReuse_inference_if_same_model(boolean value) {
        setReuseInferenceIfSameModel(value);
    }
}
