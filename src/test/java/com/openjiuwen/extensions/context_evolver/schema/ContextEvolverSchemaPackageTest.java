/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.memory.MemorySchemas;
import com.openjiuwen.extensions.context_evolver.schema.memory.PersonalMemory;
import com.openjiuwen.extensions.context_evolver.schema.memory.TaskMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEvolverSchemaPackageTest {

    @Test
    void exportsExpectedSchemaSurface() throws NoSuchMethodException {
        assertThat(ContextEvolverSchemaPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/extensions/context_evolver/schema/__init__.py");
        assertThat(ContextEvolverSchemaPackage.DESCRIPTION).isEqualTo("Memory schema definitions.");
        assertThat(ContextEvolverSchemaPackage.BASE_MEMORY.getSimpleName()).isEqualTo("BaseMemory");
        assertThat(ContextEvolverSchemaPackage.TASK_MEMORY).isEqualTo(TaskMemory.class);
        assertThat(ContextEvolverSchemaPackage.PERSONAL_MEMORY).isEqualTo(PersonalMemory.class);
        assertThat(ContextEvolverSchemaPackage.ACE_MEMORY).isEqualTo(ACEMemory.class);
        assertThat(ContextEvolverSchemaPackage.RETRIEVE_RESPONSE).isEqualTo(RetrieveResponse.class);
        assertThat(ContextEvolverSchemaPackage.VECTOR_NODE_TO_MEMORY_OWNER).isEqualTo(MemorySchemas.class);
        assertThat(ContextEvolverSchemaPackage.VECTOR_NODE_TO_MEMORY_METHOD).isEqualTo("vectorNodeToMemory");
        assertThat(MemorySchemas.class.getMethod("vectorNodeToMemory", VectorNode.class)).isNotNull();
    }
}
