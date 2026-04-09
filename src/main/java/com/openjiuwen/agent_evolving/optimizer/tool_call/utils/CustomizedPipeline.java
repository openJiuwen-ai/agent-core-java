/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Customized pipeline for tool optimization.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_pipline.customized_pipeline}.
 */
public final class CustomizedPipeline {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CustomizedPipeline() {
        // Utility class
    }

    /**
     * Run optimization pipeline.
     *
     * @param stage        Pipeline stage ("example" or "description")
     * @param tool         Tool definition
     * @param config       Configuration
     * @param toolCallable Tool callable function
     * @return Pipeline results
     */
    public static List<Object> customizedPipeline(
            String stage,
            Map<String, Object> tool,
            Map<String, Object> config,
            Object toolCallable
    ) {
        String toolName = (String) tool.get("name");

        SimpleApiWrapper callApiFn;
        if (config != null && config.containsKey("fn_call_path")) {
            throw new UnsupportedOperationException("config based api wrapper is not implemented yet.");
        } else if (toolCallable != null) {
            callApiFn = new SimpleApiWrapper(toolCallable, toolName, config);
        } else {
            throw new IllegalArgumentException("Either config or toolCallable must be provided.");
        }

        SimpleEval evalFn = new SimpleEval(callApiFn, config);
        List<String> apiKeys = null; // API keys are templates offered to LLM for params generation
        List<String> nonOptParams = new ArrayList<>();

        Object method;
        if ("example".equals(stage)) {
            method = new APICallToExampleMethod(config, callApiFn, evalFn, apiKeys, nonOptParams);
        } else if ("description".equals(stage)) {
            method = new ToolDescriptionMethod(config, evalFn);
        } else {
            throw new IllegalArgumentException("wrong stage: " + stage);
        }

        Loggers.AGENT.info("=== Starting SingleRoundSearch ===");

        BeamSearch singleSearch = new BeamSearch(
                method,
                (Integer) config.getOrDefault("beam_width", 2),
                (Integer) config.getOrDefault("expand_num", 3),
                (Integer) config.getOrDefault("max_depth", 2),
                (Integer) config.getOrDefault("num_workers", 2),
                (Integer) config.getOrDefault("verbose", 1) == 1,
                true,  // earlyStop
                true,  // checkValid
                3.0,   // maxScore
                (Integer) config.getOrDefault("top_k", 5)
        );

        List<List<Object>> result = singleSearch.search(tool);

        // Save results
        String saveDir = (String) config.get("save_dir");
        if (saveDir != null) {
            String saveFilename = toolName + ".json";
            Path savePath = Paths.get(saveDir, saveFilename);

            try {
                Files.createDirectories(savePath.getParent());

                List<Object> mergedResult = new ArrayList<>();
                // Merge with existing results if file exists
                if (Files.exists(savePath)) {
                    String content = Files.readString(savePath);
                    @SuppressWarnings("unchecked")
                    List<Object> existing = OBJECT_MAPPER.readValue(content, List.class);
                    mergedResult.addAll(existing);
                }
                mergedResult.addAll(result);

                Files.writeString(savePath, OBJECT_MAPPER.writeValueAsString(mergedResult));
            } catch (Exception e) {
                Loggers.AGENT.error("Failed to save results: {}", e.getMessage());
            }
        }

        return new ArrayList<>(result);
    }
}
