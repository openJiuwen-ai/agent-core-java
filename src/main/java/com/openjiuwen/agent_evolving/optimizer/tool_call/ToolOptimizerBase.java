/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import com.openjiuwen.agent_evolving.optimizer.BaseOptimizer;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.CustomizedPipeline;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.DefaultConfigs;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.SchemaExtractor;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.ToolDescriptionReviewer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool dimension optimizer base class.
 *
 * <p>Optimizes tunables exposed by ToolCallOperator (e.g., tool_description).
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.base.ToolOptimizerBase}.
 */
public abstract class ToolOptimizerBase extends BaseOptimizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected int maxTurns;
    protected String llmApiKey;
    protected Map<String, Object> configEg;
    protected Map<String, Object> configDesc;
    protected String pathSaveDir;

    protected ToolOptimizerBase() {
        this(Map.of());
    }

    protected ToolOptimizerBase(Map<String, Object> kwargs) {
        this.domain = "tool";
        Map<String, Object> options = kwargs != null ? kwargs : Map.of();
        this.maxTurns = intOption(options, "max_turns", 5);
        this.llmApiKey = stringOption(options, "llm_api_key", "");
        this.configEg = mapOption(options, "config_eg", DefaultConfigs.defaultConfigEg());
        this.configDesc = mapOption(options, "config_desc", DefaultConfigs.defaultConfigDesc());
        this.pathSaveDir = stringOption(options, "path_save_dir", "./tool_optimizer_results");
        String toolName = stringOption(options, "tool_name", "tool");

        this.configEg.put("save_dir", Paths.get(pathSaveDir, "examples").toString());
        this.configDesc.put("save_dir", Paths.get(pathSaveDir, "descriptions").toString());
        this.configDesc.put("examples_dir", this.configEg.get("save_dir"));
        this.configDesc.put("neg_ex_input_path", Paths.get(pathSaveDir, toolName + ".json").toString());
    }

    /**
     * Default targets for tool optimizers.
     *
     * @return List of default targets
     */
    @Override
    public List<String> defaultTargets() {
        return Collections.singletonList("tool_description");
    }

    /**
     * Optimize a tool schema/description through example and description stages.
     *
     * @param tool         Tool map containing name and description
     * @param toolCallable Callable used by the example-generation stage
     * @return Final reviewed description map
     */
    public Map<String, Object> optimizeTool(Map<String, Object> tool, Object toolCallable) {
        Map<String, Object> workingTool = tool != null ? tool : new LinkedHashMap<>();
        String originalDesc = String.valueOf(workingTool.getOrDefault("description", ""));
        List<Object> resultDesc = new ArrayList<>();

        configEg.put("llm_api_key", llmApiKey);
        configDesc.put("llm_api_key", llmApiKey);

        for (int i = 0; i < maxTurns; i++) {
            if (i > 0) {
                String latestDescription = extractLatestDescription(resultDesc);
                if (latestDescription != null) {
                    workingTool.put("description", latestDescription);
                }
            }
            runCustomizedPipeline("example", workingTool, toolCallable, configEg);
            resultDesc = runCustomizedPipeline("description", workingTool, toolCallable, configDesc);
        }

        String outputDesc = extractLatestDescription(resultDesc);
        if (outputDesc == null) {
            outputDesc = String.valueOf(workingTool.getOrDefault("description", ""));
        }

        ToolDescriptionReviewer processor = createToolDescriptionReviewer(
                String.valueOf(configDesc.getOrDefault("eval_model_id", "")),
                llmApiKey
        );
        Map<String, Object> schema = SchemaExtractor.extractSchema(originalDesc);
        Map<String, Object> processed = processor.process(
                Map.of("description", outputDesc),
                String.valueOf(workingTool.getOrDefault("description", "")),
                List.of("clean", "cross_check", "translate")
        );
        return processor.format(schema, toJson(processed), null);
    }

    protected List<Object> runCustomizedPipeline(
            String stage,
            Map<String, Object> tool,
            Object toolCallable,
            Map<String, Object> config
    ) {
        return CustomizedPipeline.customizedPipeline(stage, tool, config, toolCallable);
    }

    protected ToolDescriptionReviewer createToolDescriptionReviewer(String evalModelId, String apiKey) {
        return new ToolDescriptionReviewer(evalModelId, apiKey);
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public Map<String, Object> getConfigEg() {
        return new LinkedHashMap<>(configEg);
    }

    public Map<String, Object> getConfigDesc() {
        return new LinkedHashMap<>(configDesc);
    }

    public String getPathSaveDir() {
        return pathSaveDir;
    }

    private static int intOption(Map<String, Object> options, String key, int defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            return Integer.parseInt(String.valueOf(value));
        }
        return defaultValue;
    }

    private static String stringOption(Map<String, Object> options, String key, String defaultValue) {
        Object value = options.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static Map<String, Object> mapOption(
            Map<String, Object> options,
            String key,
            Map<String, Object> defaultValue
    ) {
        Object raw = options.get(key);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>(defaultValue);
    }

    private static String extractLatestDescription(Object value) {
        Object latest = latestLeaf(value);
        if (latest instanceof Map<?, ?> map) {
            Object description = map.get("description");
            return description != null ? String.valueOf(description) : null;
        }
        return latest != null ? String.valueOf(latest) : null;
    }

    private static Object latestLeaf(Object value) {
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            return latestLeaf(list.get(list.size() - 1));
        }
        return value;
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize processed tool description", e);
        }
    }
}
