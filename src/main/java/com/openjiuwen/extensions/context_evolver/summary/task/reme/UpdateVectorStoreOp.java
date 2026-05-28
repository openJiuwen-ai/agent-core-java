/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.persistence.MemoryPersistenceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme.update.UpdateVectorStoreOp}.
 * 
 * Persist memories to vector store.
 */
public class UpdateVectorStoreOp extends BaseOp {
    
    private static final Logger log = LoggerFactory.getLogger(UpdateVectorStoreOp.class);
    
    private final MemoryPersistenceHelper persistenceHelper;
    
    public UpdateVectorStoreOp() {
        super();
        this.persistenceHelper = new MemoryPersistenceHelper();
    }
    
    public UpdateVectorStoreOp(MemoryPersistenceHelper persistenceHelper) {
        super();
        this.persistenceHelper = persistenceHelper;
    }
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> memories = context.has("deduplicated_memories")
            ? (List<Map<String, Object>>) context.get("deduplicated_memories")
            : new ArrayList<>();
        
        if (memories.isEmpty()) {
            log.warn("No memories to persist");
            return CompletableFuture.completedFuture(null);
        }
        
        String userId = context.getUserId();
        String algoName = "reme"; // ReMe algorithm
        
        log.info("Persisting {} memories to vector store", memories.size());
        
        // Convert memories to nodes_dict format
        Map<String, Map<String, Object>> nodesDict = new HashMap<>();
        for (int i = 0; i < memories.size(); i++) {
            Map<String, Object> memory = memories.get(i);
            String nodeId = "reme_" + userId + "_node_" + i;
            
            Map<String, Object> nodeData = new HashMap<>();
            nodeData.put("id", nodeId);
            nodeData.put("content", memory.getOrDefault("content", ""));
            nodeData.put("metadata", memory);
            // Embedding would be generated in a proper implementation
            
            nodesDict.put(nodeId, nodeData);
        }
        
        // Persist to backend
        persistenceHelper.save(userId, algoName, nodesDict);
        
        context.set("persisted_count", memories.size());
        
        log.info("Persisted {} memories for user {}", memories.size(), userId);
        
        return CompletableFuture.completedFuture(null);
    }
}