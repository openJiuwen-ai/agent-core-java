/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.schema.memory.MemorySchemas;
import com.openjiuwen.extensions.context_evolver.schema.memory.PersonalMemory;
import com.openjiuwen.extensions.context_evolver.schema.memory.TaskMemory;

/**
 * Package bridge for the context-evolver schema exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/extensions/context_evolver/schema/__init__.py}.
 */
public final class ContextEvolverSchemaPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/schema/__init__.py";
    public static final String DESCRIPTION = "Memory schema definitions.";

    public static final Class<com.openjiuwen.extensions.context_evolver.schema.memory.BaseMemory> BASE_MEMORY =
            com.openjiuwen.extensions.context_evolver.schema.memory.BaseMemory.class;
    public static final Class<TaskMemory> TASK_MEMORY = TaskMemory.class;
    public static final Class<PersonalMemory> PERSONAL_MEMORY = PersonalMemory.class;
    public static final Class<MemorySchemas> VECTOR_NODE_TO_MEMORY_OWNER = MemorySchemas.class;
    public static final String VECTOR_NODE_TO_MEMORY_METHOD = "vectorNodeToMemory";

    public static final Class<ACEMemory> ACE_MEMORY = ACEMemory.class;
    public static final Class<ACERetrievedMemory> ACE_RETRIEVED_MEMORY = ACERetrievedMemory.class;
    public static final Class<ReasoningBankMemory> REASONING_BANK_MEMORY = ReasoningBankMemory.class;
    public static final Class<ReasoningBankMemoryItem> REASONING_BANK_MEMORY_ITEM = ReasoningBankMemoryItem.class;
    public static final Class<ReasoningBankRetrievedMemory> REASONING_BANK_RETRIEVED_MEMORY = ReasoningBankRetrievedMemory.class;
    public static final Class<ReMeMemory> RE_ME_MEMORY = ReMeMemory.class;
    public static final Class<ReMeMemoryMetadata> RE_ME_MEMORY_METADATA = ReMeMemoryMetadata.class;
    public static final Class<ReMeRetrievedMemory> RE_ME_RETRIEVED_MEMORY = ReMeRetrievedMemory.class;
    public static final Class<SummarizeResponse> SUMMARIZE_RESPONSE = SummarizeResponse.class;
    public static final Class<RetrieveResponse> RETRIEVE_RESPONSE = RetrieveResponse.class;

    private ContextEvolverSchemaPackage() {
    }
}
