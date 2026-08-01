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
 * 
 * @since 0.1.7
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

    /**
     * LinkedHashMap<>.
     * 
     * @param "1" "1"
     * @since 0.1.7
     */
    private Map<String, String> env = new LinkedHashMap<>(Map.of("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1"));

    /**
     * ArrayList<>.
     * 
     * @param "0.85" "0.85"
     * @since 0.1.7
     */
    private List<String> extraArgs = new ArrayList<>(
            List.of("--enable-lora", "--max-loras", "4", "--max-lora-rank", "32", "--enable-auto-tool-choice",
                    "--tool-call-parser", "hermes", "--max-model-len", "32768", "--gpu-memory-utilization", "0.85"));

    /**
     * validate.
     * 
     * @param fieldPrefix fieldPrefix
     * @since 0.1.7
     */
    public void validate(String fieldPrefix) {
        validateOptionalPort(port, fieldPrefix + ".port");
        validateAtLeast(tp, 1, fieldPrefix + ".tp");
        validateGreaterThanZero(healthTimeout, fieldPrefix + ".health_timeout");
    }

    /**
     * validateOptionalPort.
     * 
     * @param value value
     * @param fieldName fieldName
     * @since 0.1.7
     */
    protected static void validateOptionalPort(Integer value, String fieldName) {
        if (value != null && (value < 1 || value > 65535)) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and 65535");
        }
    }

    /**
     * validateAtLeast.
     * 
     * @param value value
     * @param minimum minimum
     * @param fieldName fieldName
     * @since 0.1.7
     */
    protected static void validateAtLeast(int value, int minimum, String fieldName) {
        if (value < minimum) {
            throw new IllegalArgumentException(fieldName + " must be >= " + minimum);
        }
    }

    /**
     * validateGreaterThanZero.
     * 
     * @param value value
     * @param fieldName fieldName
     * @since 0.1.7
     */
    protected static void validateGreaterThanZero(double value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    /**
     * getModel_path.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModel_path() {
        return getModelPath();
    }

    /**
     * setModel_path.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setModel_path(String value) {
        setModelPath(value);
    }

    /**
     * getModel_name.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModel_name() {
        return getModelName();
    }

    /**
     * setModel_name.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setModel_name(String value) {
        setModelName(value);
    }

    /**
     * getGpu_ids.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getGpu_ids() {
        return getGpuIds();
    }

    /**
     * setGpu_ids.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setGpu_ids(String value) {
        setGpuIds(value);
    }

    /**
     * getExisting_url.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getExisting_url() {
        return getExistingUrl();
    }

    /**
     * setExisting_url.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setExisting_url(String value) {
        setExistingUrl(value);
    }

    /**
     * getHealth_timeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getHealth_timeout() {
        return getHealthTimeout();
    }

    /**
     * setHealth_timeout.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setHealth_timeout(double value) {
        setHealthTimeout(value);
    }

    /**
     * getExtra_args.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getExtra_args() {
        return getExtraArgs();
    }

    /**
     * setExtra_args.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setExtra_args(List<String> value) {
        setExtraArgs(value);
    }
}
