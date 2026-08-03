/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code RecallMemoryOp} behavior in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/run.py}.
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
    void retrievesReasoningBankItemsForRequestedUser() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(1.0d, 0.0d));
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("node-1", reasoningNode("node-1", "user-a", List.of(1.0d, 0.0d), List.of(
                item("Title 1", "Description 1", "Content 1"),
                item("Title 2", "Description 2", "Content 2")
        )));
        vectorStore.loadNode("node-2", reasoningNode("node-2", "user-b", List.of(1.0d, 0.0d), List.of(
                item("Other", "Other description", "Other content")
        )));
        vectorStore.loadNode("node-3", otherNode("node-3", "user-a", List.of(1.0d, 0.0d)));
        serviceContext.registerService("embedding_model", embedding);
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "how to reason");
        context.set("user_id", "user-a");

        new RecallMemoryOp().asyncExecute(context).join();

        assertThat(embedding.getLastText()).isEqualTo("how to reason");
        List<ReasoningBankRetrievedMemory> memories = retrievedMemories(context);
        assertThat(memories).hasSize(2);
        assertThat(memories.get(0).getTitle()).isEqualTo("Title 1");
        assertThat(memories.get(0).getDescription()).isEqualTo("Description 1");
        assertThat(memories.get(0).getContent()).isEqualTo("Content 1");
        assertThat(memories.get(1).getTitle()).isEqualTo("Title 2");
    }

    @Test
    void honorsTopKBeforeExpandingMemoryItems() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("best", reasoningNode("best", "default", List.of(1.0d, 0.0d), List.of(
                item("Best", "Best description", "Best content")
        )));
        vectorStore.loadNode("second", reasoningNode("second", "default", List.of(0.0d, 1.0d), List.of(
                item("Second", "Second description", "Second content")
        )));
        serviceContext.registerService("embedding_model", new FakeEmbedding(List.of(1.0d, 0.0d)));
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "default query");

        new RecallMemoryOp(1).asyncExecute(context).join();

        List<ReasoningBankRetrievedMemory> memories = retrievedMemories(context);
        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).getTitle()).isEqualTo("Best");
    }

    @Test
    void missingEmbeddingModelRaises() {
        serviceContext.registerService("vector_store", new MemoryVectorStore());
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");

        assertThatThrownBy(() -> new RecallMemoryOp().asyncExecute(context).join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding model not configured in ServiceContext");
    }

    @Test
    void missingVectorStoreRaises() {
        serviceContext.registerService("embedding_model", new FakeEmbedding(List.of(1.0d, 0.0d)));
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");

        assertThatThrownBy(() -> new RecallMemoryOp().asyncExecute(context).join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vector store not configured in ServiceContext");
    }

    @Test
    void emptyVectorStoreSetsEmptyRetrievedMemories() {
        serviceContext.registerService("embedding_model", new FakeEmbedding(List.of(1.0d, 0.0d)));
        serviceContext.registerService("vector_store", new MemoryVectorStore());
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");

        new RecallMemoryOp().asyncExecute(context).join();

        assertThat(retrievedMemories(context)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<ReasoningBankRetrievedMemory> retrievedMemories(RuntimeContext context) {
        Object value = context.get("retrieved_memories");
        assertThat(value).isInstanceOf(List.class);
        return (List<ReasoningBankRetrievedMemory>) value;
    }

    private static VectorNode reasoningNode(String id,
                                            String workspaceId,
                                            List<Double> embedding,
                                            List<Map<String, Object>> memory) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reasoning_bank_memory");
        metadata.put("query", "stored query");
        metadata.put("memory", memory);
        metadata.put("label", Boolean.TRUE);
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(id, "stored query", embedding, metadata);
    }

    private static VectorNode otherNode(String id, String workspaceId, List<Double> embedding) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "other");
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(id, "other", embedding, metadata);
    }

    private static Map<String, Object> item(String title, String description, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title);
        data.put("description", description);
        data.put("content", content);
        return data;
    }

    private static final class FakeEmbedding extends Embedding {
        private final List<Double> embedding;
        private String lastText;

        private FakeEmbedding(List<Double> embedding) {
            this.embedding = new ArrayList<>(embedding);
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            this.lastText = text;
            return CompletableFuture.completedFuture(new ArrayList<>(embedding));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                     Integer batchSize,
                                                                     Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(texts.stream()
                    .map(ignored -> new ArrayList<>(embedding))
                    .map(values -> (List<Double>) values)
                    .toList());
        }

        @Override
        public int getDimension() {
            return embedding.size();
        }

        private String getLastText() {
            return lastText;
        }
    }
}
