/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank.run.RecallMemoryOp}.
 * 
 * ReasoningBank algorithm memory recall operation.
 */
public class RecallMemoryOp extends BaseOp {
    
    private final MemoryVectorStore vectorStore;
    private final int topK;
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public RecallMemoryOp() {
        this(1);
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public RecallMemoryOp(int topK) {
        this.topK = topK;
        this.vectorStore = (MemoryVectorStore) ServiceContext.getInstance().getVectorStore();
    }
    
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String userId = context.getString("user_id", "default");
        String query = context.getString("query", "");
        if (vectorStore == null) {
            context.set("retrieved_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("type", "reasoning_bank_memory");
        if (userId != null) {
            filter.put("workspace_id", userId);
        }

        return vectorStore.asyncSearch(defaultEmbeddingFor(query), topK, filter).thenAccept(vectorNodes -> {
            List<ReasoningBankRetrievedMemory> retrievedMemories = new ArrayList<>();
            for (VectorNode node : vectorNodes) {
                try {
                    retrievedMemories.addAll(ReasoningBankMemory.fromVectorNode(node).toRetrievedMemories());
                } catch (RuntimeException error) {
                    log.warn("Failed to decode ReasoningBank memory {}: {}", node.getId(), error.getMessage());
                }
            }
            context.set("retrieved_memories", retrievedMemories);
        });
    }

    private static List<Double> defaultEmbeddingFor(String value) {
        int dimensions = 32;
        double[] dense = new double[dimensions];
        String normalized = value != null ? value.toLowerCase(Locale.ROOT) : "";
        String[] tokens = normalized.split("[^a-z0-9]+");
        int previousSlot = -1;

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int slot = Math.floorMod(token.hashCode(), dimensions);
            dense[slot] += 1.0d;
            if (previousSlot >= 0) {
                dense[(previousSlot + slot) % dimensions] += 0.25d;
            }
            previousSlot = slot;
        }

        if (Arrays.stream(dense).allMatch(component -> component == 0.0d)) {
            dense[0] = 1.0d;
        }

        List<Double> result = new ArrayList<>(dimensions);
        for (double component : dense) {
            result.add(component);
        }
        return result;
    }
}
