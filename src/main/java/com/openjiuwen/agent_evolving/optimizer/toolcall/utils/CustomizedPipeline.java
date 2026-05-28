/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.toolcall.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.BeamSearch;
import com.openjiuwen.agent_evolving.optimizer.tool_call.utils.SimpleEval;

/**
 * Customized pipeline for tool call optimization.
 * <p>
 * Runs pipeline stages for tool optimization using beam search.
 * <p>
 * Mirrors Python's {@code customized_pipeline} function in
 * {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_pipline}.
 * <p>
 * Note: Python filename is "customized_pipline.py" (misspelled), 
 * Java uses correct spelling "CustomizedPipeline.java".
 */
public class CustomizedPipeline {

    private static final Logger logger = Logger.getLogger(CustomizedPipeline.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Run the customized pipeline for tool optimization.
     *
     * @param stage Which stage to run. Expected options: "example", "description"
     * @param tool Tool details with ground truth - main input data
     * @param config Configuration parameters for running
     * @param toolCallable Optional callable for tool invocation
     * @return Pipeline result
     */
    public static Object runPipeline(String stage, Map<String, Object> tool, 
            Map<String, Object> config, Object toolCallable) {
        
        // Validate inputs
        if (config.containsKey("fn_call_path")) {
            throw new UnsupportedOperationException(
                "config based api wrapper is not implemented yet.");
        }
        
        Object callApiFn;
        if (toolCallable != null) {
            String toolName = (String) tool.getOrDefault("name", "unknown");
            callApiFn = new SimpleAPIWrapperFromCallable(toolCallable, toolName, config);
        } else {
            throw new IllegalArgumentException(
                "Either config or toolCallable must be provided.");
        }
        
        Object evalFn = new SimpleEval(callApiFn, config);
        Object apiKeys = null; // API keys are templates offered to LLM for params generation
        Object[] nonOptParams = new Object[0];

        Object method;
        if ("example".equals(stage)) {
            method = new APICallToExampleMethod(config, callApiFn, evalFn, 
                apiKeys, nonOptParams);
        } else if ("description".equals(stage)) {
            method = new ToolDescriptionMethod(config, evalFn);
        } else {
            throw new IllegalArgumentException("wrong stage: " + stage);
        }

        logger.info("=== Starting SingleRoundSearch ===");
        
        BeamSearch singleSearch = new BeamSearch(
            method,
            ((Number) config.getOrDefault("beam_width", 3)).intValue(),
            ((Number) config.getOrDefault("expand_num", 5)).intValue(),
            ((Number) config.getOrDefault("max_depth", 5)).intValue(),
            ((Number) config.getOrDefault("num_workers", 1)).intValue(),
            ((Boolean) config.getOrDefault("verbose", false)),
            true,  // early_stop
            true,  // check_valid
            3.0,   // max_score
            ((Number) config.getOrDefault("top_k", 3)).intValue()
        );
        
        Object result = singleSearch.search(tool);

        // Save results
        String toolName = (String) tool.getOrDefault("name", "unknown");
        String saveFilename = toolName + ".json";
        String saveDir = (String) config.getOrDefault("save_dir", "./output");
        Path savePath = Path.of(saveDir, saveFilename);
        
        try {
            Files.createDirectories(Path.of(saveDir));
        } catch (Exception e) {
            logger.warning("Failed to create save directory: " + e.getMessage());
        }

        // Merge to old results if any
        try {
            File saveFile = savePath.toFile();
            if (saveFile.exists()) {
                Object oldResult = objectMapper.readValue(saveFile, Object.class);
                // Merge results - requires result type handling
                if (oldResult instanceof Map && result instanceof Map) {
                    Map<String, Object> merged = new HashMap<>();
                    merged.putAll((Map<String, Object>) oldResult);
                    merged.putAll((Map<String, Object>) result);
                    result = merged;
                }
            }
            
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(saveFile, result);
        } catch (Exception e) {
            logger.warning("Failed to save result: " + e.getMessage());
        }

        return result;
    }

    /**
     * Run the example stage pipeline.
     */
    public static Object runExamplePipeline(Map<String, Object> tool, 
            Map<String, Object> config, Object toolCallable) {
        return runPipeline("example", tool, config, toolCallable);
    }

    /**
     * Run the description stage pipeline.
     */
    public static Object runDescriptionPipeline(Map<String, Object> tool, 
            Map<String, Object> config, Object toolCallable) {
        return runPipeline("description", tool, config, toolCallable);
    }
}