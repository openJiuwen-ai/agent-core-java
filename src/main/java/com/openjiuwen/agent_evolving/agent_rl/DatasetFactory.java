/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating agent datasets for RL training.
 * <p>
 * Mirrors Python's dataset functions in
 * {@code openjiuwen.agent_evolving.agent_rl.dataset}.
 */
public final class DatasetFactory {

    private DatasetFactory() {
        // Static utility class
    }

    /**
     * Build agent datasets for training and validation.
     * 
     * @param dataCfg Data configuration
     * @param tokenizer Tokenizer (placeholder)
     * @param processor Processor (placeholder)
     * @param trainFiles Training data files
     * @param valFiles Validation data files
     * @return Array of [trainDataset, valDataset]
     */
    public static AgentDataset[] buildAgentDatasets(
            Object dataCfg,
            Object tokenizer,
            Object processor,
            Object trainFiles,
            Object valFiles) {
        
        AgentDataset trainDs = new AgentDataset(trainFiles, tokenizer, processor, dataCfg);
        AgentDataset valDs = new AgentDataset(valFiles, tokenizer, processor, dataCfg);
        
        return new AgentDataset[] { trainDs, valDs };
    }

    /**
     * Set train and val files in data configuration.
     * 
     * @param dataCfg Data configuration object
     * @param trainFiles Training files path
     * @param valFiles Validation files path
     */
    public static void setTrainValFiles(Object dataCfg, String trainFiles, String valFiles) {
        // TODO: Implement actual config update when data config type is available
        // Side-effect behavior: updates dataCfg.trainFiles and dataCfg.valFiles
    }

    /**
     * Create offline datasets for RL training.
     * 
     * @param config Configuration object
     * @param tokenizer Tokenizer (placeholder)
     * @param processor Processor (placeholder)
     * @return DatasetBundle with train and val datasets
     */
    public static DatasetBundle createOfflineDatasets(
            Object config,
            Object tokenizer,
            Object processor) {
        
        // TODO: Implement actual offline dataset creation
        // This requires access to config.data and Verl's create_rl_sampler
        
        AgentDataset[] datasets = buildAgentDatasets(
            null, // dataCfg
            tokenizer,
            processor,
            null, // trainFiles
            null  // valFiles
        );
        
        DatasetBundle bundle = new DatasetBundle();
        bundle.setTrainDataset(datasets[0]);
        bundle.setValDataset(datasets[1]);
        bundle.setCollateFn((args) -> defaultCollateFn(args));
        
        return bundle;
    }

    /**
     * Create online datasets for RL training.
     * 
     * @param config Configuration object
     * @param tokenizer Tokenizer (placeholder)
     * @param processor Processor (placeholder)
     * @return DatasetBundle with train and val datasets and cleanup function
     */
    public static DatasetBundle createOnlineDatasets(
            Object config,
            Object tokenizer,
            Object processor) {
        
        String tmpPath = createDummyParquet();
        
        // TODO: Implement actual online dataset creation with config
        
        AgentDataset[] datasets = buildAgentDatasets(
            null, // dataCfg
            tokenizer,
            processor,
            tmpPath,
            tmpPath
        );
        
        DatasetBundle bundle = new DatasetBundle();
        bundle.setTrainDataset(datasets[0]);
        bundle.setValDataset(datasets[1]);
        bundle.setCollateFn((args) -> defaultCollateFn(args));
        bundle.setCleanupFn(() -> cleanupTempFile(tmpPath));
        
        return bundle;
    }

    /**
     * Create a dummy parquet file for testing.
     * 
     * @return Path to the created temporary file
     */
    public static String createDummyParquet() {
        try {
            // Create dummy messages data
            List<Map<String, String>> dummyMsg = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", "hi");
            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", "hello");
            dummyMsg.add(userMsg);
            dummyMsg.add(assistantMsg);
            
            // TODO: Actual parquet creation requires Apache Parquet library
            // For now, create a temporary file placeholder
            Path tmpPath = Files.createTempFile("online_ppo_dummy_", ".parquet");
            
            // Write placeholder content
            String placeholderContent = "{\"messages\": []}";
            Files.writeString(tmpPath, placeholderContent);
            
            return tmpPath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dummy parquet file", e);
        }
    }

    /**
     * Cleanup temporary file.
     * 
     * @param tmpPath Path to temporary file
     */
    private static void cleanupTempFile(String tmpPath) {
        if (tmpPath == null || tmpPath.isEmpty()) {
            return;
        }
        if (!tmpPath.contains("online_ppo_dummy_")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(tmpPath));
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Default collate function for batching.
     * 
     * @param args Array of items to collate
     * @return Collated batch
     */
    private static Object defaultCollateFn(Object[] args) {
        // TODO: Implement actual collation when integrating with ML framework
        return List.of(args);
    }
}