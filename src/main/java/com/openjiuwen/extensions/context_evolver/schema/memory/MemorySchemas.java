/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema.memory;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

/**
 * Mirrors Python's {@code vector_node_to_memory} in
 * {@code openjiuwen/extensions/context_evolver/schema/memory.py}.
 */
public final class MemorySchemas {

    private MemorySchemas() {
    }

    public static BaseMemory vectorNodeToMemory(VectorNode node) {
        String memoryType = node.getMetadata().getOrDefault("type", "task_memory").toString();
        if ("task_memory".equals(memoryType)) {
            return TaskMemory.fromVectorNode(node);
        }
        if ("personal_memory".equals(memoryType)) {
            return PersonalMemory.fromVectorNode(node);
        }
        if ("reasoning_bank_memory".equals(memoryType)) {
            return ReasoningBankMemory.fromVectorNode(node);
        }
        throw new IllegalArgumentException("Unknown memory type: " + memoryType);
    }
}
