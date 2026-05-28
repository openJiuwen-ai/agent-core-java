/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.util.HashMap;
import java.util.Map;

/**
 * CLI parsing helpers for online RL launcher runtime config.
 * <p>
 * Mirrors Python's {@code build_cli_overrides} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.cli}.
 */
public class LauncherCli {

    private static final String DEFAULT_CONFIG_FILENAME = "online_rl_config.yaml";

    /**
     * Set nested value in data map.
     * 
     * @param data Data map
     * @param path Dot-separated path
     * @param value Value to set
     */
    public static void setNestedValue(Map<String, Object> data, String path, Object value) {
        Map<String, Object> current = data;
        String[] parts = path.split("\\.");
        
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new HashMap<String, Object>();
                current.put(parts[i], next);
            }
            current = (Map<String, Object>) next;
        }
        
        current.put(parts[parts.length - 1], value);
    }

    /**
     * Build CLI overrides from parsed arguments.
     * 
     * @param args Parsed CLI arguments
     * @return Overrides map
     */
    public static Map<String, Object> buildCliOverrides(LauncherArgs args) {
        Map<String, Object> overrides = new HashMap<>();
        
        Map<String, String> cliMappings = new HashMap<>();
        cliMappings.put("demo", "demo");
        cliMappings.put("modelPath", "inference.model_path");
        cliMappings.put("modelName", "inference.model_name");
        cliMappings.put("vllmGpu", "inference.gpu_ids");
        cliMappings.put("vllmTp", "inference.tp");
        cliMappings.put("vllmPort", "inference.port");
        cliMappings.put("inferenceUrl", "inference.existing_url");
        cliMappings.put("judgeModelPath", "judge.model_path");
        cliMappings.put("judgeModelName", "judge.model_name");
        cliMappings.put("judgeGpu", "judge.gpu_ids");
        cliMappings.put("judgeTp", "judge.tp");
        cliMappings.put("judgePort", "judge.port");
        cliMappings.put("judgeUrl", "judge.existing_url");
        cliMappings.put("gatewayPort", "gateway.port");
        cliMappings.put("redisUrl", "gateway.redis_url");
        cliMappings.put("threshold", "training.threshold");
        cliMappings.put("scanInterval", "training.scan_interval");
        cliMappings.put("trainGpu", "training.gpu_ids");
        cliMappings.put("ppoConfig", "training.ppo_config");
        cliMappings.put("trajectoryBatchSize", "trajectory.batch_size");
        cliMappings.put("loraRepo", "training.lora_repo");
        cliMappings.put("jiuwenAgentServerPort", "jiuwen.agent_server_port");
        cliMappings.put("jiuwenWsPort", "jiuwen.ws_port");
        cliMappings.put("jiuwenWebHost", "jiuwen.web_host");
        cliMappings.put("jiuwenWebPort", "jiuwen.web_port");
        
        for (Map.Entry<String, String> entry : cliMappings.entrySet()) {
            Object value = args.get(entry.getKey());
            if (value != null) {
                setNestedValue(overrides, entry.getValue(), value);
            }
        }
        
        if (args.isSkipJiuwen()) {
            setNestedValue(overrides, "jiuwen.enabled", false);
        }
        
        return overrides;
    }

    /**
     * Launcher arguments container.
     */
    public static class LauncherArgs {
        private String config;
        private Boolean demo;
        private String modelPath;
        private String modelName;
        private String vllmGpu;
        private Integer vllmTp;
        private Integer vllmPort;
        private String inferenceUrl;
        private String judgeModelPath;
        private String judgeModelName;
        private String judgeGpu;
        private Integer judgeTp;
        private Integer judgePort;
        private String judgeUrl;
        private Integer gatewayPort;
        private String redisUrl;
        private Integer threshold;
        private Integer scanInterval;
        private String trainGpu;
        private String ppoConfig;
        private Integer trajectoryBatchSize;
        private String loraRepo;
        private Integer jiuwenAgentServerPort;
        private Integer jiuwenWsPort;
        private String jiuwenWebHost;
        private Integer jiuwenWebPort;
        private boolean skipJiuwen;

        public Object get(String key) {
            switch (key) {
                case "config": return config;
                case "demo": return demo;
                case "modelPath": return modelPath;
                case "modelName": return modelName;
                case "vllmGpu": return vllmGpu;
                case "vllmTp": return vllmTp;
                case "vllmPort": return vllmPort;
                case "inferenceUrl": return inferenceUrl;
                case "judgeModelPath": return judgeModelPath;
                case "judgeModelName": return judgeModelName;
                case "judgeGpu": return judgeGpu;
                case "judgeTp": return judgeTp;
                case "judgePort": return judgePort;
                case "judgeUrl": return judgeUrl;
                case "gatewayPort": return gatewayPort;
                case "redisUrl": return redisUrl;
                case "threshold": return threshold;
                case "scanInterval": return scanInterval;
                case "trainGpu": return trainGpu;
                case "ppoConfig": return ppoConfig;
                case "trajectoryBatchSize": return trajectoryBatchSize;
                case "loraRepo": return loraRepo;
                case "jiuwenAgentServerPort": return jiuwenAgentServerPort;
                case "jiuwenWsPort": return jiuwenWsPort;
                case "jiuwenWebHost": return jiuwenWebHost;
                case "jiuwenWebPort": return jiuwenWebPort;
                default: return null;
            }
        }

        public boolean isSkipJiuwen() { return skipJiuwen; }
        public void setSkipJiuwen(boolean skipJiuwen) { this.skipJiuwen = skipJiuwen; }
        
        // Standard getters/setters
        public String getConfig() { return config; }
        public void setConfig(String config) { this.config = config; }
        public Boolean getDemo() { return demo; }
        public void setDemo(Boolean demo) { this.demo = demo; }
        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getVllmGpu() { return vllmGpu; }
        public void setVllmGpu(String vllmGpu) { this.vllmGpu = vllmGpu; }
        public Integer getVllmTp() { return vllmTp; }
        public void setVllmTp(Integer vllmTp) { this.vllmTp = vllmTp; }
        public Integer getVllmPort() { return vllmPort; }
        public void setVllmPort(Integer vllmPort) { this.vllmPort = vllmPort; }
        public String getInferenceUrl() { return inferenceUrl; }
        public void setInferenceUrl(String inferenceUrl) { this.inferenceUrl = inferenceUrl; }
        public String getJudgeModelPath() { return judgeModelPath; }
        public void setJudgeModelPath(String judgeModelPath) { this.judgeModelPath = judgeModelPath; }
        public String getJudgeModelName() { return judgeModelName; }
        public void setJudgeModelName(String judgeModelName) { this.judgeModelName = judgeModelName; }
        public String getJudgeGpu() { return judgeGpu; }
        public void setJudgeGpu(String judgeGpu) { this.judgeGpu = judgeGpu; }
        public Integer getJudgeTp() { return judgeTp; }
        public void setJudgeTp(Integer judgeTp) { this.judgeTp = judgeTp; }
        public Integer getJudgePort() { return judgePort; }
        public void setJudgePort(Integer judgePort) { this.judgePort = judgePort; }
        public String getJudgeUrl() { return judgeUrl; }
        public void setJudgeUrl(String judgeUrl) { this.judgeUrl = judgeUrl; }
        public Integer getGatewayPort() { return gatewayPort; }
        public void setGatewayPort(Integer gatewayPort) { this.gatewayPort = gatewayPort; }
        public String getRedisUrl() { return redisUrl; }
        public void setRedisUrl(String redisUrl) { this.redisUrl = redisUrl; }
        public Integer getThreshold() { return threshold; }
        public void setThreshold(Integer threshold) { this.threshold = threshold; }
        public Integer getScanInterval() { return scanInterval; }
        public void setScanInterval(Integer scanInterval) { this.scanInterval = scanInterval; }
        public String getTrainGpu() { return trainGpu; }
        public void setTrainGpu(String trainGpu) { this.trainGpu = trainGpu; }
        public String getPpoConfig() { return ppoConfig; }
        public void setPpoConfig(String ppoConfig) { this.ppoConfig = ppoConfig; }
        public Integer getTrajectoryBatchSize() { return trajectoryBatchSize; }
        public void setTrajectoryBatchSize(Integer trajectoryBatchSize) { this.trajectoryBatchSize = trajectoryBatchSize; }
        public String getLoraRepo() { return loraRepo; }
        public void setLoraRepo(String loraRepo) { this.loraRepo = loraRepo; }
        public Integer getJiuwenAgentServerPort() { return jiuwenAgentServerPort; }
        public void setJiuwenAgentServerPort(Integer jiuwenAgentServerPort) { this.jiuwenAgentServerPort = jiuwenAgentServerPort; }
        public Integer getJiuwenWsPort() { return jiuwenWsPort; }
        public void setJiuwenWsPort(Integer jiuwenWsPort) { this.jiuwenWsPort = jiuwenWsPort; }
        public String getJiuwenWebHost() { return jiuwenWebHost; }
        public void setJiuwenWebHost(String jiuwenWebHost) { this.jiuwenWebHost = jiuwenWebHost; }
        public Integer getJiuwenWebPort() { return jiuwenWebPort; }
        public void setJiuwenWebPort(Integer jiuwenWebPort) { this.jiuwenWebPort = jiuwenWebPort; }
    }
}