/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI parsing helpers for online RL launcher runtime config.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/cli.py}.
 */
public final class LauncherCli {

    private static final Map<String, String> OPTION_TO_FIELD = Map.ofEntries(
            Map.entry("--config", "config"),
            Map.entry("--model-path", "modelPath"),
            Map.entry("--model-name", "modelName"),
            Map.entry("--vllm-gpu", "vllmGpu"),
            Map.entry("--vllm-tp", "vllmTp"),
            Map.entry("--vllm-port", "vllmPort"),
            Map.entry("--inference-url", "inferenceUrl"),
            Map.entry("--judge-model-path", "judgeModelPath"),
            Map.entry("--judge-model-name", "judgeModelName"),
            Map.entry("--judge-gpu", "judgeGpu"),
            Map.entry("--judge-tp", "judgeTp"),
            Map.entry("--judge-port", "judgePort"),
            Map.entry("--judge-url", "judgeUrl"),
            Map.entry("--gateway-port", "gatewayPort"),
            Map.entry("--redis-url", "redisUrl"),
            Map.entry("--threshold", "threshold"),
            Map.entry("--scan-interval", "scanInterval"),
            Map.entry("--train-gpu", "trainGpu"),
            Map.entry("--ppo-config", "ppoConfig"),
            Map.entry("--trajectory-batch-size", "trajectoryBatchSize"),
            Map.entry("--lora-repo", "loraRepo"),
            Map.entry("--jiuwen-agent-server-port", "jiuwenAgentServerPort"),
            Map.entry("--jiuwen-ws-port", "jiuwenWsPort"),
            Map.entry("--jiuwen-web-host", "jiuwenWebHost"),
            Map.entry("--jiuwen-web-port", "jiuwenWebPort")
    );
    private static final Map<String, Boolean> INTEGER_FIELDS = Map.ofEntries(
            Map.entry("vllmTp", true),
            Map.entry("vllmPort", true),
            Map.entry("judgeTp", true),
            Map.entry("judgePort", true),
            Map.entry("gatewayPort", true),
            Map.entry("threshold", true),
            Map.entry("scanInterval", true),
            Map.entry("trajectoryBatchSize", true),
            Map.entry("jiuwenAgentServerPort", true),
            Map.entry("jiuwenWsPort", true),
            Map.entry("jiuwenWebPort", true)
    );

    private LauncherCli() {
    }

    public static void setNestedValue(Map<String, Object> data, String path, Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> current = data;
        String[] parts = path.split("\\.");
        for (int index = 0; index < parts.length - 1; index++) {
            Object next = current.get(parts[index]);
            if (!(next instanceof Map<?, ?>)) {
                next = new LinkedHashMap<String, Object>();
                current.put(parts[index], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    public static Map<String, Object> buildCliOverrides(LauncherArgs args) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        Map<String, String> cliMappings = new LinkedHashMap<>();
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

    public static LauncherArgs parseArgs(String[] args) {
        LauncherArgs parsed = new LauncherArgs();
        String[] safeArgs = args == null ? new String[0] : args;
        for (int index = 0; index < safeArgs.length; index++) {
            String token = safeArgs[index];
            if (token == null || token.isBlank()) {
                continue;
            }
            ParsedOption option = splitOption(token);
            if ("--demo".equals(option.name())) {
                ensureNoInlineValue(option);
                parsed.setDemo(true);
                continue;
            }
            if ("--skip-jiuwen".equals(option.name()) || "--skip_jiuwen".equals(option.name())) {
                ensureNoInlineValue(option);
                parsed.setSkipJiuwen(true);
                continue;
            }
            String field = OPTION_TO_FIELD.get(option.name());
            if (field == null) {
                throw new IllegalArgumentException("Unknown argument: " + option.name());
            }
            String value = option.value();
            if (value == null) {
                index += 1;
                if (index >= safeArgs.length || safeArgs[index].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for argument: " + option.name());
                }
                value = safeArgs[index];
            }
            setField(parsed, field, value, option.name());
        }
        return parsed;
    }

    private static ParsedOption splitOption(String token) {
        if (!token.startsWith("--")) {
            throw new IllegalArgumentException("Unexpected positional argument: " + token);
        }
        int equalsIndex = token.indexOf('=');
        if (equalsIndex < 0) {
            return new ParsedOption(token, null);
        }
        return new ParsedOption(token.substring(0, equalsIndex), token.substring(equalsIndex + 1));
    }

    private static void ensureNoInlineValue(ParsedOption option) {
        if (option.value() != null) {
            throw new IllegalArgumentException("Flag does not take a value: " + option.name());
        }
    }

    private static void setField(LauncherArgs parsed, String field, String value, String optionName) {
        Object normalized = INTEGER_FIELDS.containsKey(field) ? parseInt(value, optionName) : value;
        switch (field) {
            case "config" -> parsed.setConfig((String) normalized);
            case "modelPath" -> parsed.setModelPath((String) normalized);
            case "modelName" -> parsed.setModelName((String) normalized);
            case "vllmGpu" -> parsed.setVllmGpu((String) normalized);
            case "vllmTp" -> parsed.setVllmTp((Integer) normalized);
            case "vllmPort" -> parsed.setVllmPort((Integer) normalized);
            case "inferenceUrl" -> parsed.setInferenceUrl((String) normalized);
            case "judgeModelPath" -> parsed.setJudgeModelPath((String) normalized);
            case "judgeModelName" -> parsed.setJudgeModelName((String) normalized);
            case "judgeGpu" -> parsed.setJudgeGpu((String) normalized);
            case "judgeTp" -> parsed.setJudgeTp((Integer) normalized);
            case "judgePort" -> parsed.setJudgePort((Integer) normalized);
            case "judgeUrl" -> parsed.setJudgeUrl((String) normalized);
            case "gatewayPort" -> parsed.setGatewayPort((Integer) normalized);
            case "redisUrl" -> parsed.setRedisUrl((String) normalized);
            case "threshold" -> parsed.setThreshold((Integer) normalized);
            case "scanInterval" -> parsed.setScanInterval((Integer) normalized);
            case "trainGpu" -> parsed.setTrainGpu((String) normalized);
            case "ppoConfig" -> parsed.setPpoConfig((String) normalized);
            case "trajectoryBatchSize" -> parsed.setTrajectoryBatchSize((Integer) normalized);
            case "loraRepo" -> parsed.setLoraRepo((String) normalized);
            case "jiuwenAgentServerPort" -> parsed.setJiuwenAgentServerPort((Integer) normalized);
            case "jiuwenWsPort" -> parsed.setJiuwenWsPort((Integer) normalized);
            case "jiuwenWebHost" -> parsed.setJiuwenWebHost((String) normalized);
            case "jiuwenWebPort" -> parsed.setJiuwenWebPort((Integer) normalized);
            default -> throw new IllegalArgumentException("Unsupported field for argument " + optionName + ": " + field);
        }
    }

    private static Integer parseInt(String value, String optionName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for argument " + optionName + ": " + value, exception);
        }
    }

    private record ParsedOption(String name, String value) {
    }

    public static final class LauncherArgs {
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
            return switch (key) {
                case "config" -> config;
                case "demo" -> demo;
                case "modelPath" -> modelPath;
                case "modelName" -> modelName;
                case "vllmGpu" -> vllmGpu;
                case "vllmTp" -> vllmTp;
                case "vllmPort" -> vllmPort;
                case "inferenceUrl" -> inferenceUrl;
                case "judgeModelPath" -> judgeModelPath;
                case "judgeModelName" -> judgeModelName;
                case "judgeGpu" -> judgeGpu;
                case "judgeTp" -> judgeTp;
                case "judgePort" -> judgePort;
                case "judgeUrl" -> judgeUrl;
                case "gatewayPort" -> gatewayPort;
                case "redisUrl" -> redisUrl;
                case "threshold" -> threshold;
                case "scanInterval" -> scanInterval;
                case "trainGpu" -> trainGpu;
                case "ppoConfig" -> ppoConfig;
                case "trajectoryBatchSize" -> trajectoryBatchSize;
                case "loraRepo" -> loraRepo;
                case "jiuwenAgentServerPort" -> jiuwenAgentServerPort;
                case "jiuwenWsPort" -> jiuwenWsPort;
                case "jiuwenWebHost" -> jiuwenWebHost;
                case "jiuwenWebPort" -> jiuwenWebPort;
                default -> null;
            };
        }

        public String getConfig() {
            return config;
        }

        public void setConfig(String config) {
            this.config = config;
        }

        public Boolean getDemo() {
            return demo;
        }

        public void setDemo(Boolean demo) {
            this.demo = demo;
        }

        public String getModelPath() {
            return modelPath;
        }

        public void setModelPath(String modelPath) {
            this.modelPath = modelPath;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getVllmGpu() {
            return vllmGpu;
        }

        public void setVllmGpu(String vllmGpu) {
            this.vllmGpu = vllmGpu;
        }

        public Integer getVllmTp() {
            return vllmTp;
        }

        public void setVllmTp(Integer vllmTp) {
            this.vllmTp = vllmTp;
        }

        public Integer getVllmPort() {
            return vllmPort;
        }

        public void setVllmPort(Integer vllmPort) {
            this.vllmPort = vllmPort;
        }

        public String getInferenceUrl() {
            return inferenceUrl;
        }

        public void setInferenceUrl(String inferenceUrl) {
            this.inferenceUrl = inferenceUrl;
        }

        public String getJudgeModelPath() {
            return judgeModelPath;
        }

        public void setJudgeModelPath(String judgeModelPath) {
            this.judgeModelPath = judgeModelPath;
        }

        public String getJudgeModelName() {
            return judgeModelName;
        }

        public void setJudgeModelName(String judgeModelName) {
            this.judgeModelName = judgeModelName;
        }

        public String getJudgeGpu() {
            return judgeGpu;
        }

        public void setJudgeGpu(String judgeGpu) {
            this.judgeGpu = judgeGpu;
        }

        public Integer getJudgeTp() {
            return judgeTp;
        }

        public void setJudgeTp(Integer judgeTp) {
            this.judgeTp = judgeTp;
        }

        public Integer getJudgePort() {
            return judgePort;
        }

        public void setJudgePort(Integer judgePort) {
            this.judgePort = judgePort;
        }

        public String getJudgeUrl() {
            return judgeUrl;
        }

        public void setJudgeUrl(String judgeUrl) {
            this.judgeUrl = judgeUrl;
        }

        public Integer getGatewayPort() {
            return gatewayPort;
        }

        public void setGatewayPort(Integer gatewayPort) {
            this.gatewayPort = gatewayPort;
        }

        public String getRedisUrl() {
            return redisUrl;
        }

        public void setRedisUrl(String redisUrl) {
            this.redisUrl = redisUrl;
        }

        public Integer getThreshold() {
            return threshold;
        }

        public void setThreshold(Integer threshold) {
            this.threshold = threshold;
        }

        public Integer getScanInterval() {
            return scanInterval;
        }

        public void setScanInterval(Integer scanInterval) {
            this.scanInterval = scanInterval;
        }

        public String getTrainGpu() {
            return trainGpu;
        }

        public void setTrainGpu(String trainGpu) {
            this.trainGpu = trainGpu;
        }

        public String getPpoConfig() {
            return ppoConfig;
        }

        public void setPpoConfig(String ppoConfig) {
            this.ppoConfig = ppoConfig;
        }

        public Integer getTrajectoryBatchSize() {
            return trajectoryBatchSize;
        }

        public void setTrajectoryBatchSize(Integer trajectoryBatchSize) {
            this.trajectoryBatchSize = trajectoryBatchSize;
        }

        public String getLoraRepo() {
            return loraRepo;
        }

        public void setLoraRepo(String loraRepo) {
            this.loraRepo = loraRepo;
        }

        public Integer getJiuwenAgentServerPort() {
            return jiuwenAgentServerPort;
        }

        public void setJiuwenAgentServerPort(Integer jiuwenAgentServerPort) {
            this.jiuwenAgentServerPort = jiuwenAgentServerPort;
        }

        public Integer getJiuwenWsPort() {
            return jiuwenWsPort;
        }

        public void setJiuwenWsPort(Integer jiuwenWsPort) {
            this.jiuwenWsPort = jiuwenWsPort;
        }

        public String getJiuwenWebHost() {
            return jiuwenWebHost;
        }

        public void setJiuwenWebHost(String jiuwenWebHost) {
            this.jiuwenWebHost = jiuwenWebHost;
        }

        public Integer getJiuwenWebPort() {
            return jiuwenWebPort;
        }

        public void setJiuwenWebPort(Integer jiuwenWebPort) {
            this.jiuwenWebPort = jiuwenWebPort;
        }

        public boolean isSkipJiuwen() {
            return skipJiuwen;
        }

        public void setSkipJiuwen(boolean skipJiuwen) {
            this.skipJiuwen = skipJiuwen;
        }
    }
}
