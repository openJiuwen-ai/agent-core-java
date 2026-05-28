// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Task runner for PPO training with Ray integration.
 * <p>
 * Mirrors Python's {@code task_runner.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.task_runner}.
 */
public final class TaskRunner {
    
    private static final Logger logger = Logger.getLogger(TaskRunner.class.getName());
    
    // Agent core directory (project root)
    private static final Path AGENT_CORE_DIR = Paths.get("")
        .toAbsolutePath()
        .getParent()
        .getParent()
        .getParent()
        .getParent();
    
    private TaskRunner() {
        // Utility class
    }
    
    /**
     * Get PPO Ray runtime environment configuration.
     */
    public static Map<String, Object> getPpoRayRuntimeEnv() {
        Map<String, String> envVars = new HashMap<>();
        
        envVars.put("TOKENIZERS_PARALLELISM", "true");
        envVars.put("NCCL_DEBUG", "WARN");
        envVars.put("VLLM_LOGGING_LEVEL", "WARN");
        envVars.put("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "true");
        envVars.put("CUDA_DEVICE_MAX_CONNECTIONS", "1");
        envVars.put("NCCL_CUMEM_ENABLE", "0");
        envVars.put("VLLM_ASCEND_ENABLE_NZ", "0");
        
        // Build PYTHONPATH equivalent
        String agentCorePath = AGENT_CORE_DIR.toString();
        String existingPath = System.getenv("PYTHONPATH");
        
        List<String> pathParts = new ArrayList<>();
        pathParts.add(agentCorePath);
        
        if (existingPath != null && !existingPath.isEmpty()) {
            for (String entry : existingPath.split(":")) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                
                Path normalized = Paths.get(entry).toAbsolutePath();
                
                // Skip package subdirs that would break imports
                if (normalized.toString().endsWith("openjiuwen/agent_evolving")) {
                    continue;
                }
                
                if (!pathParts.contains(normalized.toString())) {
                    pathParts.add(normalized.toString());
                }
            }
        }
        
        envVars.put("PYTHONPATH", String.join(":", pathParts));
        
        Map<String, Object> runtimeEnv = new HashMap<>();
        runtimeEnv.put("env_vars", envVars);
        
        return runtimeEnv;
    }
    
    /**
     * Get default Ray runtime environment.
     */
    public static Map<String, Object> getDefaultRayRuntimeEnv() {
        return getPpoRayRuntimeEnv();
    }
    
    /**
     * Load HF tokenizer.
     * PLACEHOLDER: Requires HuggingFace tokenizer integration.
     */
    public static Object loadTokenizer(String modelPath) {
        throw new UnsupportedOperationException(
            "loadTokenizer requires HuggingFace tokenizer Java binding. " +
            "Placeholder until HF tokenizer integration is available."
        );
    }
    
    /**
     * Load HF processor.
     * PLACEHOLDER: Requires HuggingFace processor integration.
     */
    public static Object loadProcessor(String modelPath) {
        throw new UnsupportedOperationException(
            "loadProcessor requires HuggingFace processor Java binding. " +
            "Placeholder until HF processor integration is available."
        );
    }
    
    /**
     * Copy to local path.
     * <p>
     * Mirrors Python's {@code copy_to_local} from {@code verl.utils.fs}.
     * <p>
     * For remote sources (hf://, s3://), requires external framework integration.
     * For local sources, performs direct file copy.
     *
     * @param source Source path (local, hf://, or s3://)
     * @param target Target path
     * @return Target path if successful, null if failed
     */
    public static Path copyToLocal(String source, Path target) {
        if (source == null || target == null) {
            return null;
        }
        
        try {
            // Handle remote sources - PLACEHOLDER until framework integration
            if (source.startsWith("hf://") || source.startsWith("s3://") || 
                source.startsWith("http://") || source.startsWith("https://")) {
                logger.warning("Remote file copy not implemented for: " + source + 
                    ". Requires HuggingFace/S3 SDK integration. Returning target as-is.");
                // For remote sources, we cannot copy without external dependencies
                // Return null to indicate the copy was not performed
                return null;
            }
            
            // Handle local file copy
            Path sourcePath = Paths.get(source);
            if (Files.exists(sourcePath)) {
                // Create parent directories if needed
                if (target.getParent() != null && !Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                
                // Copy file
                Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied file from " + source + " to " + target);
                return target;
            }
            
            // Source doesn't exist locally
            logger.warning("Source file does not exist: " + source);
            return null;
        } catch (Exception e) {
            logger.warning("Failed to copy to local: " + e.getMessage());
            return null;
        }
    }
}