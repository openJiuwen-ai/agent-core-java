/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme.update.MemoryDeduplicationOp}.
 * 
 * Remove duplicate memories.
 */
public class MemoryDeduplicationOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryDeduplicationOp.class);
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> allMemories = new ArrayList<>();
        
        // Combine success and failure memories
        if (context.has("success_memories")) {
            allMemories.addAll((List<Map<String, Object>>) context.get("success_memories"));
        }
        if (context.has("failure_memories")) {
            allMemories.addAll((List<Map<String, Object>>) context.get("failure_memories"));
        }
        
        if (allMemories.isEmpty()) {
            log.warn("No memories to deduplicate");
            context.set("deduplicated_memories", new ArrayList<>());
            return CompletableFuture.completedFuture(null);
        }
        
        log.info("Deduplicating {} memories", allMemories.size());
        
        // Simple deduplication by content similarity
        Set<String> seenContent = new HashSet<>();
        List<Map<String, Object>> deduplicatedMemories = new ArrayList<>();
        
        for (Map<String, Object> memory : allMemories) {
            String content = (String) memory.getOrDefault("content", "");
            String normalizedContent = content.toLowerCase().trim();
            
            if (!seenContent.contains(normalizedContent)) {
                seenContent.add(normalizedContent);
                deduplicatedMemories.add(memory);
            }
        }
        
        context.set("deduplicated_memories", deduplicatedMemories);
        
        log.info("Deduplicated to {} unique memories", deduplicatedMemories.size());
        
        return CompletableFuture.completedFuture(null);
    }
}