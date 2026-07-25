/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.VLLMServiceConfig.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VLLMServiceConfig {
    private String modelPath = "/path/to/your/model";
    private String modelName = "Qwen3-4B-Thinking-2507";
    private String host = "127.0.0.1";
    private Integer port;
    private String gpuIds = "0,1";
    private int tp = 2;
    private String existingUrl;
    private double healthTimeout = 300.0;
    private Map<String, String> env = new LinkedHashMap<>(Map.of(
            "VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1"
    ));
    private List<String> extraArgs = new ArrayList<>(List.of(
            "--enable-lora",
            "--max-loras",
            "4",
            "--max-lora-rank",
            "32",
            "--enable-auto-tool-choice",
            "--tool-call-parser",
            "hermes",
            "--max-model-len",
            "32768",
            "--gpu-memory-utilization",
            "0.85"
    ));

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate(String fieldPrefix) {
        validateOptionalPort(port, fieldPrefix + ".port");
        validateAtLeast(tp, 1, fieldPrefix + ".tp");
        validateGreaterThanZero(healthTimeout, fieldPrefix + ".health_timeout");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static void validateOptionalPort(Integer value, String fieldName) {
        if (value != null && (value < 1 || value > 65535)) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and 65535");
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static void validateAtLeast(int value, int minimum, String fieldName) {
        if (value < minimum) {
            throw new IllegalArgumentException(fieldName + " must be >= " + minimum);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected static void validateGreaterThanZero(double value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModel_path() { return getModelPath(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModel_path(String value) { setModelPath(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModel_name() { return getModelName(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModel_name(String value) { setModelName(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getGpu_ids() { return getGpuIds(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGpu_ids(String value) { setGpuIds(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getExisting_url() { return getExistingUrl(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExisting_url(String value) { setExistingUrl(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public double getHealth_timeout() { return getHealthTimeout(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setHealth_timeout(double value) { setHealthTimeout(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getExtra_args() { return getExtraArgs(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtra_args(List<String> value) { setExtraArgs(value); }
}
