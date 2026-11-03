/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's ReMe retrieval operation behavior in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/run.py}.
 */
class ReMeRunOpsTest {

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
    void recallRetrievesReMeMemoriesForRequestedUser() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(1.0d, 0.0d));
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("node-1", remeNode("node-1", "user-a", List.of(1.0d, 0.0d), "when-a", "content-a"));
        vectorStore.loadNode("node-2", remeNode("node-2", "user-b", List.of(1.0d, 0.0d), "when-b", "content-b"));
        vectorStore.loadNode("node-3", otherNode("node-3", "user-a", List.of(1.0d, 0.0d)));
        serviceContext.registerService("embedding_model", embedding);
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "how to proceed");
        context.set("user_id", "user-a");

        new RecallMemoryOp().asyncExecute(context).join();

        assertThat(embedding.getLastText()).isEqualTo("how to proceed");
        List<ReMeRetrievedMemory> memories = retrievedMemories(context);
        assertThat(memories).hasSize(1);
        assertThat(memories.get(0).getWhenToUse()).isEqualTo("when-a");
        assertThat(memories.get(0).getContent()).isEqualTo("content-a");
    }

    @Test
    void recallMissingEmbeddingModelRaises() {
        serviceContext.registerService("vector_store", new MemoryVectorStore());
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");

        assertThatThrownBy(() -> new RecallMemoryOp().asyncExecute(context).join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding model not configured in ServiceContext");
    }

    @Test
    void recallMissingVectorStoreRaises() {
        serviceContext.registerService("embedding_model", new FakeEmbedding(List.of(1.0d, 0.0d)));
        RuntimeContext context = new RuntimeContext();
        context.set("query", "query");

        assertThatThrownBy(() -> new RecallMemoryOp().asyncExecute(context).join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vector store not configured in ServiceContext");
    }

    @Test
    void rerankDisabledLeavesMemoriesUnchangedWithoutLlm() {
        RuntimeContext context = new RuntimeContext();
        List<ReMeRetrievedMemory> memories = memories();
        context.set("retrieved_memories", memories);

        new RerankMemoryOp(false, 1).asyncExecute(context).join();

        assertThat(retrievedMemories(context)).containsExactlyElementsOf(memories);
    }

    @Test
    void rerankUsesLlmIndicesAddsRemainingAndTrims() {
        RecordingLlm llm = new RecordingLlm("""
                ```json
                {"ranked_indices": [2, 0]}
                ```
                """);
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "current task");
        context.set("retrieved_memories", memories());

        new RerankMemoryOp(true, 2).asyncExecute(context).join();

        assertThat(llm.getLastPrompt()).contains("current task");
        assertThat(llm.getLastPrompt()).contains("Candidate 0:");
        List<ReMeRetrievedMemory> reranked = retrievedMemories(context);
        assertThat(reranked).extracting(ReMeRetrievedMemory::getWhenToUse)
                .containsExactly("when-2", "when-0");
    }

    @Test
    void rerankInvalidResponseKeepsOriginalOrderBeforeTrim() {
        serviceContext.registerService("llm", new RecordingLlm("no ranking here"));
        RuntimeContext context = new RuntimeContext();
        context.set("query", "current task");
        context.set("retrieved_memories", memories());

        new RerankMemoryOp(true, 2).asyncExecute(context).join();

        assertThat(retrievedMemories(context)).extracting(ReMeRetrievedMemory::getWhenToUse)
                .containsExactly("when-0", "when-1");
    }

    @Test
    void rewriteNoMemoriesSetsEmptyString() {
        serviceContext.registerService("llm", new RecordingLlm("{}"));
        RuntimeContext context = new RuntimeContext();
        context.set("retrieved_memories", List.of());

        new RewriteMemoryOp().asyncExecute(context).join();

        assertThat(context.get("memory_string")).isEqualTo("");
    }

    @Test
    void rewriteDisabledUsesFormattedOriginalMemories() {
        serviceContext.registerService("llm", new RecordingLlm("{}"));
        RuntimeContext context = new RuntimeContext();
        context.set("retrieved_memories", List.of(new ReMeRetrievedMemory("when", "content")));

        new RewriteMemoryOp(false).asyncExecute(context).join();

        assertThat(context.get("memory_string")).isEqualTo("Memory 1:\n  When to use: when\n  Content: content\n");
    }

    @Test
    void rewriteUsesParsedLlmContext() {
        RecordingLlm llm = new RecordingLlm("""
                ```json
                {"rewritten_context": "Use the cached plan."}
                ```
                """);
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("query", "current task");
        context.set("retrieved_memories", List.of(new ReMeRetrievedMemory("when", "content")));

        new RewriteMemoryOp(true).asyncExecute(context).join();

        assertThat(llm.getLastPrompt()).contains("current task");
        assertThat(context.get("memory_string")).isEqualTo("Use the cached plan.");
    }

    @Test
    void rewriteInvalidResponseUsesFormattedOriginalMemories() {
        serviceContext.registerService("llm", new RecordingLlm("not json"));
        RuntimeContext context = new RuntimeContext();
        context.set("query", "current task");
        context.set("retrieved_memories", List.of(new ReMeRetrievedMemory("when", "content")));

        new RewriteMemoryOp(true).asyncExecute(context).join();

        assertThat(context.get("memory_string")).isEqualTo("Memory 1:\n  When to use: when\n  Content: content\n");
    }

    @SuppressWarnings("unchecked")
    private static List<ReMeRetrievedMemory> retrievedMemories(RuntimeContext context) {
        Object value = context.get("retrieved_memories");
        assertThat(value).isInstanceOf(List.class);
        return (List<ReMeRetrievedMemory>) value;
    }

    private static List<ReMeRetrievedMemory> memories() {
        return List.of(
                new ReMeRetrievedMemory("when-0", "content-0"),
                new ReMeRetrievedMemory("when-1", "content-1"),
                new ReMeRetrievedMemory("when-2", "content-2")
        );
    }

    private static VectorNode remeNode(String id,
                                       String workspaceId,
                                       List<Double> embedding,
                                       String whenToUse,
                                       String content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reme_memory");
        metadata.put("when_to_use", whenToUse);
        metadata.put("content", content);
        metadata.put("score", 0.25d);
        metadata.put("created_at", Instant.parse("2026-01-01T00:00:00Z").toString());
        metadata.put("updated_at", Instant.parse("2026-01-02T00:00:00Z").toString());
        metadata.put("workspace_id", workspaceId);
        metadata.put("metadata", Map.of("tags", List.of("tag"), "freq", 1));
        return new VectorNode(id, whenToUse, embedding, metadata);
    }

    private static VectorNode otherNode(String id, String workspaceId, List<Double> embedding) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "other");
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(id, "other", embedding, metadata);
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

    private static final class RecordingLlm implements ReMeAsyncLlm {
        private final String response;
        private String lastPrompt;

        private RecordingLlm(String response) {
            this.response = response;
        }

        @Override
        public CompletableFuture<String> asyncGenerate(String prompt) {
            this.lastPrompt = prompt;
            return CompletableFuture.completedFuture(response);
        }

        private String getLastPrompt() {
            return lastPrompt;
        }
    }
}
