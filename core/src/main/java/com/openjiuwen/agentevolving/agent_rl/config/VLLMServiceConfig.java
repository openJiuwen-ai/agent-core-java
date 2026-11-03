/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VLLM service configuration.
 * <p>
 * Mirrors Python's {@code VLLMServiceConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/online_config.py}.
 */
public class VLLMServiceConfig {

    private String modelPath = "/path/to/your/model";
    private String modelName = "Qwen3-4B-Thinking-2507";
    private String host = "127.0.0.1";
    private Integer port; // Port: 1-65535 or null
    private String gpuIds = "0,1";
    private int tp = 2;
    private String existingUrl;
    private double healthTimeout = 300.0;
    private Map<String, String> env = new HashMap<>(Map.of("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1"));
    private List<String> extraArgs = List.of(
            "--enable-lora", "--max-loras", "4", "--max-lora-rank", "32",
            "--enable-auto-tool-choice", "--tool-call-parser", "hermes",
            "--max-model-len", "32768", "--gpu-memory-utilization", "0.85"
    );

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { 
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.port = port; 
    }
    public String getGpuIds() { return gpuIds; }
    public void setGpuIds(String gpuIds) { this.gpuIds = gpuIds; }
    public int getTp() { return tp; }
    public void setTp(int tp) { 
        if (tp < 1) throw new IllegalArgumentException("tp must be >= 1");
        this.tp = tp; 
    }
    public String getExistingUrl() { return existingUrl; }
    public void setExistingUrl(String existingUrl) { this.existingUrl = existingUrl; }
    public double getHealthTimeout() { return healthTimeout; }
    public void setHealthTimeout(double healthTimeout) { 
        if (healthTimeout <= 0) throw new IllegalArgumentException("healthTimeout must be > 0");
        this.healthTimeout = healthTimeout; 
    }
    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env != null ? new HashMap<>(env) : new HashMap<>(); }
    public List<String> getExtraArgs() { return extraArgs; }
    public void setExtraArgs(List<String> extraArgs) { 
        this.extraArgs = extraArgs != null ? List.copyOf(extraArgs) : List.of(); 
    }
}
