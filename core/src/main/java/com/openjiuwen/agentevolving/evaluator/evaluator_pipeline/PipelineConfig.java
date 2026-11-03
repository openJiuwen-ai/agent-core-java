/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pipeline configuration loaded from YAML or overrides.
 * <p>
 * Mirrors Python's {@code PipelineConfig} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/config.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfig {

    private String agent = "jiuwenswarm";
    private String benchmark = "skillsbench";
    private boolean evolutionMode = false;
    private int maxIterations = 1;
    private boolean convergenceCheck = true;
    private int convergenceThreshold = 2;
    private int stagnationPatience = 3;
    private Path resultsDir = Path.of("./evolution_results");
    private boolean saveTrajectory = true;
    private boolean saveSkillHistory = true;
    private Map<String, Object> agentConfig = new LinkedHashMap<>();
    private Map<String, Object> benchConfig = new LinkedHashMap<>();
    private List<String> taskIds = new ArrayList<>();
    private String tasksFilter = "";

    @SuppressWarnings("unchecked")
    public static PipelineConfig fromYaml(Path configPath) {
        Map<String, Object> data;
        try {
            String yamlText = Files.readString(configPath, StandardCharsets.UTF_8);
            Object loaded = new Yaml().load(yamlText);
            data = loaded instanceof Map<?, ?> rawMap ? toStringKeyMap(rawMap) : new LinkedHashMap<>();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read pipeline config " + configPath, exception);
        }

        Map<String, Object> pipeline = data.get("pipeline") instanceof Map<?, ?> map
                ? toStringKeyMap(map)
                : new LinkedHashMap<>();
        Map<String, Object> agentCfg = data.get("agent_config") instanceof Map<?, ?> map
                ? toStringKeyMap(map)
                : new LinkedHashMap<>();
        Map<String, Object> benchCfg = data.get("bench_config") instanceof Map<?, ?> map
                ? toStringKeyMap(map)
                : new LinkedHashMap<>();

        resolveEnvVars(agentCfg);
        resolveEnvVars(benchCfg);

        PipelineConfig config = new PipelineConfig();
        config.setAgent(stringValue(pipeline.get("agent"), "jiuwenswarm"));
        config.setBenchmark(stringValue(pipeline.get("benchmark"), "skillsbench"));
        config.setEvolutionMode(booleanValue(pipeline.get("evolution_mode"), false));
        config.setMaxIterations(intValue(pipeline.get("max_iterations"), 1));
        config.setConvergenceCheck(booleanValue(pipeline.get("convergence_check"), true));
        config.setConvergenceThreshold(intValue(pipeline.get("convergence_threshold"), 2));
        config.setStagnationPatience(intValue(pipeline.get("stagnation_patience"), 3));
        config.setResultsDir(Path.of(stringValue(pipeline.get("results_dir"), "./evolution_results")));
        config.setSaveTrajectory(booleanValue(pipeline.get("save_trajectory"), true));
        config.setSaveSkillHistory(booleanValue(pipeline.get("save_skill_history"), true));
        config.setAgentConfig(agentCfg);
        config.setBenchConfig(benchCfg);
        return config;
    }

    public static PipelineConfig fromArgs(Map<String, Object> overrides) {
        return fromDict(overrides);
    }

    public static PipelineConfig fromDict(Map<String, Object> data) {
        PipelineConfig config = new PipelineConfig();
        if (data == null) {
            return config;
        }
        if (data.containsKey("agent")) {
            config.setAgent(stringValue(data.get("agent"), config.getAgent()));
        }
        if (data.containsKey("benchmark")) {
            config.setBenchmark(stringValue(data.get("benchmark"), config.getBenchmark()));
        }
        if (containsAny(data, "evolutionMode", "evolution_mode")) {
            config.setEvolutionMode(booleanValue(firstValue(data, "evolutionMode", "evolution_mode"),
                    config.isEvolutionMode()));
        }
        if (containsAny(data, "maxIterations", "max_iterations")) {
            config.setMaxIterations(intValue(firstValue(data, "maxIterations", "max_iterations"),
                    config.getMaxIterations()));
        }
        if (containsAny(data, "convergenceCheck", "convergence_check")) {
            config.setConvergenceCheck(booleanValue(firstValue(data, "convergenceCheck", "convergence_check"),
                    config.isConvergenceCheck()));
        }
        if (containsAny(data, "convergenceThreshold", "convergence_threshold")) {
            config.setConvergenceThreshold(intValue(firstValue(data, "convergenceThreshold", "convergence_threshold"),
                    config.getConvergenceThreshold()));
        }
        if (containsAny(data, "stagnationPatience", "stagnation_patience")) {
            config.setStagnationPatience(intValue(firstValue(data, "stagnationPatience", "stagnation_patience"),
                    config.getStagnationPatience()));
        }
        if (containsAny(data, "resultsDir", "results_dir")) {
            config.setResultsDir(Path.of(stringValue(firstValue(data, "resultsDir", "results_dir"),
                    config.getResultsDir().toString())));
        }
        if (containsAny(data, "saveTrajectory", "save_trajectory")) {
            config.setSaveTrajectory(booleanValue(firstValue(data, "saveTrajectory", "save_trajectory"),
                    config.isSaveTrajectory()));
        }
        if (containsAny(data, "saveSkillHistory", "save_skill_history")) {
            config.setSaveSkillHistory(booleanValue(firstValue(data, "saveSkillHistory", "save_skill_history"),
                    config.isSaveSkillHistory()));
        }
        if (firstValue(data, "agentConfig", "agent_config") instanceof Map<?, ?> map) {
            config.setAgentConfig(toStringKeyMap(map));
        }
        if (firstValue(data, "benchConfig", "bench_config") instanceof Map<?, ?> map) {
            config.setBenchConfig(toStringKeyMap(map));
        }
        if (firstValue(data, "taskIds", "task_ids") instanceof List<?> list) {
            config.setTaskIds(list.stream().map(String::valueOf).toList());
        }
        if (containsAny(data, "tasksFilter", "tasks_filter")) {
            config.setTasksFilter(stringValue(firstValue(data, "tasksFilter", "tasks_filter"),
                    config.getTasksFilter()));
        }
        return config;
    }

    private static boolean containsAny(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static Object firstValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private static void resolveEnvVars(Map<String, Object> cfg) {
        for (Map.Entry<String, Object> entry : new ArrayList<>(cfg.entrySet())) {
            Object value = entry.getValue();
            if (!(value instanceof String text)) {
                continue;
            }
            if (text.startsWith("${") && text.endsWith("}")) {
                String envName = text.substring(2, text.length() - 1);
                String envValue = System.getenv(envName);
                if (envValue != null && !envValue.isEmpty()) {
                    cfg.put(entry.getKey(), envValue);
                }
            }
        }
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
