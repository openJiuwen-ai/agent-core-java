/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

class MemorySchemaModelsTest {

    @Test
    void taskMemoryRoundTripPreservesMetadataAndScore() {
        TaskMemory memory = new TaskMemory("Use a scratch workspace for destructive trials", "when cleanup can fail");
        memory.setWorkspaceId("ws-1");
        memory.setHelpfulCount(3);
        memory.setHarmfulCount(1);
        memory.setSection("ops");

        VectorNode node = memory.toVectorNode();
        TaskMemory restored = TaskMemory.fromVectorNode(node);

        assertEquals("task_ws-1_" + SchemaUtils.md5Hex(memory.getContent()), node.getId());
        assertEquals("task_memory", node.getMetadata().get("type"));
        assertEquals(memory.getContent(), restored.getContent());
        assertEquals("ws-1", restored.getWorkspaceId());
        assertEquals("when cleanup can fail", restored.getWhenToUse());
        assertEquals("ops", restored.getSection());
        assertEquals(2.0d, restored.getScore());
        assertTrue(restored.toString().contains("TaskMemory("));
    }

    @Test
    void personalMemoryRoundTripPreservesOptionalReflectionSubject() {
        PersonalMemory memory = new PersonalMemory("User prefers concise summaries", "user preference");
        memory.setWorkspaceId("alice");
        memory.setReflectionSubject("summary style");

        VectorNode node = memory.toVectorNode();
        PersonalMemory restored = PersonalMemory.fromVectorNode(node);

        assertEquals("personal_alice_" + SchemaUtils.md5Hex(memory.getContent()), node.getId());
        assertEquals("user preference", restored.getTarget());
        assertEquals("summary style", restored.getReflectionSubject());
        assertEquals("alice", restored.getWorkspaceId());
    }

    @Test
    void reasoningBankMemoryRoundTripPreservesStrategyFields() {
        ReasoningBankMemory memory = new ReasoningBankMemory(
            "Distill repeated reasoning into transferable rules",
            "Generalize from fixes",
            "Capture why the fix worked"
        );
        memory.setWorkspaceId("planner");
        memory.setSourceType("comparative");
        memory.setHelpfulCount(5);
        memory.setHarmfulCount(1);

        VectorNode node = memory.toVectorNode();
        ReasoningBankMemory restored = ReasoningBankMemory.fromVectorNode(node);

        assertEquals(
            "reasoning_bank_planner_" + SchemaUtils.md5Hex("Generalize from fixes|Distill repeated reasoning into transferable rules"),
            node.getId()
        );
        assertEquals("comparative", restored.getSourceType());
        assertEquals("Capture why the fix worked", restored.getDescription());
        assertEquals(3.0d, restored.getScore());
        assertTrue(restored.toString().contains("ReasoningBankMemory("));
    }

    @Test
    void vectorNodeToMemoryDispatchesByMetadataType() {
        TaskMemory taskMemory = new TaskMemory("content", "when");
        taskMemory.setWorkspaceId("default");
        BaseMemory dispatched = MemorySchemas.vectorNodeToMemory(taskMemory.toVectorNode());
        assertInstanceOf(TaskMemory.class, dispatched);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "mystery");
        VectorNode node = new VectorNode("id", "content", null, metadata);
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> MemorySchemas.vectorNodeToMemory(node)
        );
        assertEquals("Unknown memory type: mystery", error.getMessage());
    }
}
