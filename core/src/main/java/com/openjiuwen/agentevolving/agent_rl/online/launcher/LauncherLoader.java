/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.launcher;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.openjiuwen.agentevolving.agent_rl.config.OnlineRLConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime launcher config merge: built-in defaults + optional YAML + CLI overrides.
 * <p>
 * Mirrors Python's {@code load_runtime_config} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/launcher/loader.py}.
 */
public final class LauncherLoader {

    private static final ObjectMapper CONFIG_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String DEFAULT_CONFIG_FILENAME = "online_config.py (built-in)";
    private static final String BUILTIN_ONLINE_CONFIG_PATH =
            "openjiuwen/agent_evolving/agent_rl/config/online_config.py";

    private LauncherLoader() {
    }

    public record RuntimeConfigResult(Map<String, Object> config, Path resolvedPath, OnlineRLConfig validatedConfig) {
        public RuntimeConfigResult {
            config = deepCopyMap(Objects.requireNonNull(config, "config"));
            resolvedPath = Objects.requireNonNull(resolvedPath, "resolvedPath");
            validatedConfig = Objects.requireNonNull(validatedConfig, "validatedConfig");
        }
    }

    public static Map<String, Object> loadConfig(String configFile, Map<String, Object> cliOverrides) {
        try {
            return loadRuntimeConfig(configFile, cliOverrides).config();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static RuntimeConfigResult loadRuntimeConfig(String configFile, Map<String, Object> cliOverrides)
            throws IOException {
        Map<String, Object> config = getDefaultConfig();
        Path resolvedPath;
        if (configFile != null && !configFile.isBlank()) {
            resolvedPath = expandUser(configFile).toAbsolutePath().normalize();
            if (!Files.exists(resolvedPath)) {
                throw new FileNotFoundException("Config file not found: " + resolvedPath);
            }
            deepMerge(config, loadYamlConfig(resolvedPath));
        } else {
            resolvedPath = resolveBuiltinOnlineConfigPath();
        }
        deepMerge(config, cliOverrides != null ? cliOverrides : Map.of());
        OnlineRLConfig validatedConfig = toOnlineRLConfig(config);
        validatedConfig.validate();
        return new RuntimeConfigResult(config, resolvedPath, validatedConfig);
    }

    private static Map<String, Object> loadYamlConfig(Path configPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Object loaded = yaml.load(inputStream);
            if (loaded == null) {
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Config file root must be a mapping: " + configPath);
            }
            return normalizeMap(map);
        }
    }

    public static Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();

        Map<String, Object> inference = new LinkedHashMap<>();
        inference.put("model_path", "/path/to/your/model");
        inference.put("model_name", "Qwen3-4B-Thinking-2507");
        inference.put("host", "127.0.0.1");
        inference.put("port", null);
        inference.put("gpu_ids", "0,1");
        inference.put("tp", 2);
        inference.put("existing_url", null);
        inference.put("health_timeout", 300.0);
        inference.put("env", new LinkedHashMap<>(Map.of("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1")));
        inference.put("extra_args", List.of(
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
        config.put("inference", inference);

        Map<String, Object> judge = new LinkedHashMap<>();
        judge.put("model_path", "/path/to/your/model");
        judge.put("model_name", "Qwen3-4B-Thinking-2507");
        judge.put("host", "127.0.0.1");
        judge.put("port", null);
        judge.put("gpu_ids", "2,3");
        judge.put("tp", 2);
        judge.put("existing_url", null);
        judge.put("health_timeout", 600.0);
        judge.put("reuse_inference_if_same_model", true);
        judge.put("env", new LinkedHashMap<>());
        judge.put("extra_args", List.of(
                "--max-model-len",
                "8192",
                "--gpu-memory-utilization",
                "0.85",
                "--max-num-seqs",
                "16"
        ));
        config.put("judge", judge);

        Map<String, Object> gateway = new LinkedHashMap<>();
        gateway.put("host", "127.0.0.1");
        gateway.put("port", null);
        gateway.put("redis_url", null);
        gateway.put("record_dir", "records");
        gateway.put("log_level", "info");
        gateway.put("health_timeout", 30.0);
        gateway.put("disable_trajectory_collection", true);
        gateway.put("env", new LinkedHashMap<>());
        config.put("gateway", gateway);

        Map<String, Object> training = new LinkedHashMap<>();
        training.put("gpu_ids", "4,5");
        training.put("threshold", 4);
        training.put("scan_interval", 30);
        training.put("ppo_config", null);
        training.put("lora_repo", null);
        config.put("training", training);

        Map<String, Object> trajectory = new LinkedHashMap<>();
        trajectory.put("batch_size", 4);
        trajectory.put("mode", "feedback_level");
        config.put("trajectory", trajectory);

        Map<String, Object> jiuwen = new LinkedHashMap<>();
        jiuwen.put("enabled", true);
        jiuwen.put("agent_server_port", null);
        jiuwen.put("app_host", "127.0.0.1");
        jiuwen.put("ws_port", null);
        jiuwen.put("web_host", "127.0.0.1");
        jiuwen.put("web_port", null);
        config.put("jiuwen", jiuwen);
        config.put("jiwen", jiuwen);

        config.put("demo", false);
        return deepCopyMap(config);
    }

    private static void deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> overlayMap && base.get(key) instanceof Map<?, ?> baseMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedBase = (Map<String, Object>) baseMap;
                deepMerge(typedBase, normalizeMap(overlayMap));
            } else {
                base.put(key, normalizeValue(value));
            }
        }
    }

    public static String getDefaultConfigFilename() {
        return DEFAULT_CONFIG_FILENAME;
    }

    public static Path resolveBuiltinOnlineConfigPath() {
        return Path.of(BUILTIN_ONLINE_CONFIG_PATH).toAbsolutePath().normalize();
    }

    public static OnlineRLConfig toOnlineRLConfig(Map<String, Object> config) {
        OnlineRLConfig onlineRLConfig = CONFIG_MAPPER.convertValue(config, OnlineRLConfig.class);
        onlineRLConfig.validate();
        return onlineRLConfig;
    }

    private static Path expandUser(String configFile) {
        if (configFile.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (configFile.startsWith("~/") || configFile.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home"), configFile.substring(2));
        }
        return Path.of(configFile);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(LauncherLoader::normalizeValue).toList();
        }
        return value;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        return normalizeMap(source);
    }
}
