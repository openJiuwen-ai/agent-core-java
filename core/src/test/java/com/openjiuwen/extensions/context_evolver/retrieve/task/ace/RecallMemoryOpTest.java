/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.ace;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code RecallMemoryOp} behavior in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/ace/run.py}.
 */
class RecallMemoryOpTest {

    private final ServiceContext serviceContext = new ServiceContext();

    @BeforeEach
    void setUp() {
        serviceContext.clear();
    }

    @AfterEach
    void tearDown() {
        serviceContext.clear();
    }

    @Test
    void loadsAceMemoriesForRequestedUser() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("node-1", aceNode("node-1", "user-a", "sec-a", "content-a", 3, 1, 2));
        vectorStore.loadNode("node-2", aceNode("node-2", "user-b", "sec-b", "content-b", 4, 0, 1));
        vectorStore.loadNode("node-3", nonAceNode("node-3", "user-a"));
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-a");

        new RecallMemoryOp().asyncExecute(context).join();

        List<ACERetrievedMemory> retrieved = retrievedMemories(context);
        assertThat(retrieved).hasSize(1);
        ACERetrievedMemory memory = retrieved.get(0);
        assertThat(memory.getId()).isEqualTo("node-1");
        assertThat(memory.getSection()).isEqualTo("sec-a");
        assertThat(memory.getContent()).isEqualTo("content-a");
        assertThat(memory.getHelpful()).isEqualTo(3);
        assertThat(memory.getHarmful()).isEqualTo(1);
        assertThat(memory.getNeutral()).isEqualTo(2);
    }

    @Test
    void defaultsUserIdWhenContextDoesNotContainUserId() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("node-default", aceNode("node-default", "default", "general", "content", 0, 0, 0));
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();

        new RecallMemoryOp().asyncExecute(context).join();

        assertThat(retrievedMemories(context)).hasSize(1);
    }

    @Test
    void missingVectorStoreRaises() {
        RuntimeContext context = new RuntimeContext();

        assertThatThrownBy(() -> new RecallMemoryOp().asyncExecute(context).join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vector store not configured in ServiceContext");
    }

    @Test
    void emptyVectorStoreSetsEmptyRetrievedMemories() {
        serviceContext.registerService("vector_store", new MemoryVectorStore());
        RuntimeContext context = new RuntimeContext();

        new RecallMemoryOp().asyncExecute(context).join();

        assertThat(retrievedMemories(context)).isEmpty();
    }

    @Test
    void limitsRetrievedMemoriesToFifty() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        for (int index = 0; index < 55; index++) {
            vectorStore.loadNode("node-" + index, aceNode("node-" + index, "default", "sec", "content-" + index, 0, 0, 0));
        }
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();

        new RecallMemoryOp().asyncExecute(context).join();

        assertThat(retrievedMemories(context)).hasSize(50);
    }

    @SuppressWarnings("unchecked")
    private static List<ACERetrievedMemory> retrievedMemories(RuntimeContext context) {
        Object value = context.get("retrieved_memories");
        assertThat(value).isInstanceOf(List.class);
        return (List<ACERetrievedMemory>) value;
    }

    private static VectorNode aceNode(String id,
                                      String workspaceId,
                                      String section,
                                      String content,
                                      int helpful,
                                      int harmful,
                                      int neutral) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "ace_memory");
        metadata.put("id", id);
        metadata.put("section", section);
        metadata.put("content", content);
        metadata.put("helpful", helpful);
        metadata.put("harmful", harmful);
        metadata.put("neutral", neutral);
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(id, content, embedding(), metadata);
    }

    private static VectorNode nonAceNode(String id, String workspaceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "other");
        metadata.put("id", id);
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(id, "other", embedding(), metadata);
    }

    private static List<Double> embedding() {
        List<Double> values = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            values.add(1.0d);
        }
        return values;
    }
}
