/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemoryMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's ReMe summary update operations in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 */
class ReMeUpdateOpsTest {

    private final ServiceContext serviceContext = new ServiceContext();

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        serviceContext.clear();
    }

    @AfterEach
    void tearDown() {
        serviceContext.clear();
    }

    @Test
    void preprocessGroupsTrajectoriesByThreshold() {
        RuntimeContext context = new RuntimeContext();
        context.set("trajectories", List.of("low", "high", "border"));
        context.set("score", List.of(0.2d, 2.0d, 1.0d));
        context.set("threshold", 1.0d);

        new TrajectoryPreprocessOp().asyncExecute(context).join();

        assertThat(values(context, "success_trajectories")).containsExactly("high", "border");
        assertThat(values(context, "failure_trajectories")).containsExactly("low");
        assertThat(values(context, "all_trajectories")).containsExactly("low", "high", "border");
    }

    @Test
    void successAndFailureExtractionCreateReMeMemories() {
        RecordingLlm llm = new RecordingLlm(experienceResponse("when success", "success content"),
                experienceResponse("when fail", "failure content"));
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("query", "query");
        context.set("success_trajectories", List.of("success trajectory"));
        context.set("failure_trajectories", List.of("failure trajectory"));

        new SuccessExtractionOp().asyncExecute(context).join();
        new FailureExtractionOp().asyncExecute(context).join();

        assertThat(llm.getPrompts()).hasSize(2);
        assertThat(memories(context, "success_memories")).extracting(ReMeMemory::getWhenToUse)
                .containsExactly("when success");
        assertThat(memories(context, "failure_memories")).extracting(ReMeMemory::getContent)
                .containsExactly("failure content");
    }

    @Test
    void comparativeExtractionUsesBestAndWorstScores() {
        RecordingLlm llm = new RecordingLlm(experienceResponse("compare", "better ordering"));
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("all_trajectories", List.of("low steps", "high steps"));
        context.set("score", List.of(0.1d, 0.9d));

        new ComparativeExtractionOp().asyncExecute(context).join();

        assertThat(llm.getLastPrompt()).contains("high steps", "low steps", "0.9", "0.1");
        assertThat(memories(context, "comparative_memories")).extracting(ReMeMemory::getWhenToUse)
                .containsExactly("compare");
    }

    @Test
    void comparativeAllExtractionFormatsEveryTrajectory() {
        RecordingLlm llm = new RecordingLlm(experienceResponse("all", "pattern"));
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("all_trajectories", List.of("first", "second"));

        new ComparativeAllExtractionOp().asyncExecute(context).join();

        assertThat(llm.getLastPrompt()).contains("# Trajectory 1\nfirst", "# Trajectory 2\nsecond");
        assertThat(memories(context, "comparative_memories")).hasSize(1);
    }

    @Test
    void validationFiltersLowQualityMemoryAndUpdatesScore() {
        RecordingLlm llm = new RecordingLlm("""
                ```json
                {"is_valid": true, "score": 0.8}
                ```
                """, """
                {"is_valid": true, "score": 0.2}
                """);
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("success_memories", List.of(memory("when-valid", "content-valid")));
        context.set("failure_memories", List.of(memory("when-low", "content-low")));

        new MemoryValidationOp().asyncExecute(context).join();

        assertThat(memories(context, "validated_memories")).hasSize(1);
        assertThat(memories(context, "validated_memories").get(0).getScore()).isEqualTo(0.8d);
    }

    @Test
    void deduplicationRemovesExistingAndBatchDuplicates() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("reme_user-1_existing", remeNode("reme_user-1_existing", "user-1", "existing", List.of(1.0d, 0.0d)));
        serviceContext.registerService("vector_store", vectorStore);
        serviceContext.registerService("embedding_model", new QueuedEmbedding(
                List.of(1.0d, 0.0d),
                List.of(0.0d, 1.0d),
                List.of(0.0d, 1.0d)
        ));
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("validated_memories", List.of(
                memory("duplicate-existing", "one"),
                memory("unique", "two"),
                memory("duplicate-batch", "three")
        ));

        new MemoryDeduplicationOp(true, 0.9d).asyncExecute(context).join();

        assertThat(memories(context, "deduplicated_memories")).extracting(ReMeMemory::getWhenToUse)
                .containsExactly("unique");
        assertThat(context.get("duplicate_count")).isEqualTo(2);
    }

    @Test
    void updateVectorStoreEmbedsAndStoresDeduplicatedMemories() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        serviceContext.registerService("vector_store", vectorStore);
        serviceContext.registerService("embedding_model", new QueuedEmbedding(List.of(0.3d, 0.7d)));
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("deduplicated_memories", List.of(memory("when", "content")));

        new UpdateVectorStoreOp().asyncExecute(context).join();

        assertThat(context.get("stored_count")).isEqualTo(1);
        assertThat(values(context, "memory_ids")).hasSize(1);
        assertThat(memories(context, "memories")).hasSize(1);
        assertThat(vectorStore.getAll(Map.of("workspace_id", "user-1", "type", "reme_memory")))
                .hasSize(1)
                .allSatisfy(node -> assertThat(node.getEmbedding()).containsExactly(0.3d, 0.7d));
    }

    @Test
    void persistMemoryWritesReMeNodesForUser() throws Exception {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        VectorNode first = remeNode("reme_user-1_first", "user-1", "first", List.of(0.1d));
        VectorNode second = remeNode("reme_user-1_second", "user-1", "second", List.of(0.2d));
        VectorNode other = remeNode("reme_other_third", "other", "third", List.of(0.3d));
        vectorStore.loadNode(first.getId(), first);
        vectorStore.loadNode(second.getId(), second);
        vectorStore.loadNode(other.getId(), other);
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        Path pathTemplate = tempDir.resolve("{algo_name}-{user_id}.json");

        new PersistMemoryOp("json", pathTemplate.toString(), "localhost", 19530, "vector_nodes")
                .asyncExecute(context)
                .join();

        assertThat(context.get("persist_count")).isEqualTo(2);
        String persisted = Files.readString(tempDir.resolve("reme-user-1.json"));
        assertThat(persisted).contains("reme_user-1_first", "reme_user-1_second");
        assertThat(persisted).doesNotContain("reme_other_third");
    }

    @SuppressWarnings("unchecked")
    private static List<ReMeMemory> memories(RuntimeContext context, String key) {
        return (List<ReMeMemory>) context.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> values(RuntimeContext context, String key) {
        return (List<Object>) context.get(key);
    }

    private static String experienceResponse(String whenToUse, String content) {
        return """
                ```json
                [
                  {
                    "when_to_use": "%s",
                    "experience": "%s",
                    "tags": ["tag"],
                    "confidence": 0.9,
                    "step_type": "reasoning",
                    "tools_used": ["tool"]
                  }
                ]
                ```
                """.formatted(whenToUse, content);
    }

    private static ReMeMemory memory(String whenToUse, String content) {
        ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();
        metadata.setTags(List.of("tag"));
        metadata.setStepType("reasoning");
        metadata.setToolsUsed(List.of("tool"));
        metadata.setConfidence(0.9d);
        metadata.setFreq(0);
        metadata.setUtility(0.0d);
        ReMeMemory memory = new ReMeMemory();
        memory.setWorkspaceId("user-1");
        memory.setWhenToUse(whenToUse);
        memory.setContent(content);
        memory.setScore(1.0d);
        memory.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        memory.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        memory.setMetadata(metadata);
        return memory;
    }

    private static VectorNode remeNode(String nodeId, String workspaceId, String whenToUse, List<Double> embedding) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reme_memory");
        metadata.put("when_to_use", whenToUse);
        metadata.put("content", "content");
        metadata.put("score", 1.0d);
        metadata.put("created_at", "2026-01-01T00:00:00Z");
        metadata.put("updated_at", "2026-01-02T00:00:00Z");
        metadata.put("workspace_id", workspaceId);
        metadata.put("metadata", Map.of("tags", List.of("tag"), "freq", 0, "utility", 0.0d));
        return new VectorNode(nodeId, whenToUse, embedding, metadata);
    }

    private static final class RecordingLlm implements ReMeSummaryAsyncLlm {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        private RecordingLlm(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public CompletableFuture<String> asyncGenerate(String prompt) {
            prompts.add(prompt);
            int index = Math.min(prompts.size() - 1, responses.size() - 1);
            return CompletableFuture.completedFuture(responses.get(index));
        }

        private List<String> getPrompts() {
            return prompts;
        }

        private String getLastPrompt() {
            return prompts.get(prompts.size() - 1);
        }
    }

    private static final class QueuedEmbedding extends Embedding {
        private final List<List<Double>> embeddings;
        private int callIndex;

        @SafeVarargs
        private QueuedEmbedding(List<Double>... embeddings) {
            this.embeddings = List.of(embeddings);
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            List<Double> embedding = embeddings.get(Math.min(callIndex, embeddings.size() - 1));
            callIndex += 1;
            return CompletableFuture.completedFuture(new ArrayList<>(embedding));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                     Integer batchSize,
                                                                     Map<String, Object> kwargs) {
            List<List<Double>> result = new ArrayList<>();
            for (int index = 0; index < texts.size(); index++) {
                List<Double> embedding = embeddings.get(Math.min(callIndex, embeddings.size() - 1));
                callIndex += 1;
                result.add(new ArrayList<>(embedding));
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public int getDimension() {
            return embeddings.get(0).size();
        }
    }
}
