  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.extensions.context_evolver.retrieve.task.ace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.ace.run.RecallMemoryOp}.
 * 
 * ACE algorithm memory recall operation.
 */
public class RecallMemoryOp extends BaseOp {
    
    private final MemoryVectorStore vectorStore;
    
    public RecallMemoryOp() {
        this.vectorStore = (MemoryVectorStore) ServiceContext.getInstance().getVectorStore();
    }
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String userId = context.getString("user_id", "default");

        if (vectorStore == null) {
            context.set("retrieved_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("type", "ace_memory");
        if (userId != null) {
            filter.put("workspace_id", userId);
        }

        List<VectorNode> allVectors = vectorStore.getAll(filter);

        List<ACERetrievedMemory> retrievedMemories = allVectors.stream()
            .map(ACERetrievedMemory::fromVectorNode)
            .sorted(Comparator.comparing(ACERetrievedMemory::getSection).thenComparing(ACERetrievedMemory::getId))
            .limit(50)
            .collect(Collectors.toCollection(ArrayList::new));

        context.set("retrieved_memories", retrievedMemories);

        return CompletableFuture.completedFuture(null);
    }
}
