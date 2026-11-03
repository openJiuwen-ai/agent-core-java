/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's ReasoningBank summary update operations in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 */
class ReasoningBankUpdateOpsTest {

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
    void messagesToTextFormatsKnownRolesAndRejectsUnknown() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", "rules"),
                Map.of("role", "user", "content", "question"),
                Map.of("role", "assistant", "content", "answer")
        );

        assertThat(ReasoningBankUpdateUtils.messagesToText(messages))
                .isEqualTo("SYSTEM:\nrules\nUSER:\nquestion\nASSISTANT:\nanswer");
        assertThatThrownBy(() -> ReasoningBankUpdateUtils.messagesToText(List.of(Map.of("role", "tool", "content", "x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown message role tool");
    }

    @Test
    void labelDeterminatorUsesStatusLine() {
        RecordingLlm llm = new RecordingLlm("Thoughts: not enough evidence\nStatus: failure");

        boolean label = LabelDeterminator.determineLabel(llm, "query", "trajectory").join();

        assertThat(label).isFalse();
        assertThat(llm.getLastPrompt()).contains("Query: query", "Trajectory: trajectory");
        assertThat(llm.getLastSystemPrompt()).contains("evaluating the performance");
    }

    @Test
    void memoryItemParserExtractsItemsFromMarkdownFence() {
        List<ReasoningBankMemory> memories = MemoryItemParser.parse("""
                ```markdown
                # Memory Item 1
                ## Title Check pagination
                ## Description Always collect all pages
                ## Content Continue until the API returns no more items.

                # Memory Item 2
                ## Title Use authoritative source
                ## Description Resolve identity from source of truth
                ## Content Prefer contact APIs over transaction descriptions.
                ```
                """, "query", true);

        assertThat(memories).hasSize(1);
        ReasoningBankMemory memory = memories.get(0);
        assertThat(memory.getQuery()).isEqualTo("query");
        assertThat(memory.getLabel()).isTrue();
        assertThat(memory.getMemory()).extracting(ReasoningBankMemoryItem::getTitle)
                .containsExactly("Check pagination", "Use authoritative source");
    }

    @Test
    void summarizeUsesProvidedLabelAndExtractsMemory() {
        RecordingLlm llm = new RecordingLlm(memoryResponse());
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("matts", "none");
        context.set("query", "solve task");
        context.set("trajectories", List.of("successful trajectory"));
        context.set("label", List.of(true));

        new SummarizeMemoryOp().asyncExecute(context).join();

        assertThat(llm.getCalls()).isEqualTo(1);
        assertThat(llm.getLastPrompt()).contains("Query: solve task", "Trajectory: successful trajectory");
        assertThat(llm.getLastSystemPrompt()).contains("successfully accomplished");
        ReasoningBankMemory memory = memories(context).get(0);
        assertThat(memory.getLabel()).isTrue();
        assertThat(memory.getMemory()).hasSize(1);
    }

    @Test
    void summarizeDeterminesLabelWhenMissing() {
        RecordingLlm llm = new RecordingLlm(
                "Thoughts: worked\nStatus: success",
                memoryResponse()
        );
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("matts", "sequential");
        context.set("query", "solve task");
        context.set("trajectories", List.of("trajectory"));

        new SummarizeMemoryOp().asyncExecute(context).join();

        assertThat(llm.getCalls()).isEqualTo(2);
        assertThat(context.get("label")).isEqualTo(List.of(true));
        assertThat(memories(context).get(0).getLabel()).isTrue();
    }

    @Test
    void parallelSummarizeRequiresParallelMattsAndTwoTrajectories() {
        RecordingLlm llm = new RecordingLlm(memoryResponse());
        serviceContext.registerService("llm", llm);
        RuntimeContext skipped = new RuntimeContext();
        skipped.set("matts", "none");
        skipped.set("query", "query");
        skipped.set("trajectories", List.of("a", "b"));

        new SummarizeMemoryParallelOp().asyncExecute(skipped).join();

        assertThat(llm.getCalls()).isZero();

        RuntimeContext context = new RuntimeContext();
        context.set("matts", "parallel");
        context.set("query", "query");
        context.set("trajectories", List.of("a", "b"));

        new SummarizeMemoryParallelOp().asyncExecute(context).join();

        assertThat(llm.getCalls()).isEqualTo(1);
        assertThat(llm.getLastPrompt()).contains("<Trajectory 1>", "<Trajectory 2>");
        assertThat(memories(context).get(0).getLabel()).isNull();
    }

    @Test
    void updateVectorStoreEmbedsAndStoresReasoningBankMemories() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        FakeEmbedding embedding = new FakeEmbedding(List.of(0.5d, 0.5d));
        serviceContext.registerService("vector_store", vectorStore);
        serviceContext.registerService("embedding_model", embedding);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("memories", List.of(reasoningMemory("query", true, "Title", "Description", "Content")));

        new UpdateVectorStoreOp().asyncExecute(context).join();

        assertThat(context.get("stored_count")).isEqualTo(1);
        assertThat((List<?>) context.get("memory_ids")).hasSize(1);
        assertThat(embedding.getLastTexts()).containsExactly("query");
        assertThat(vectorStore.getAll(Map.of("workspace_id", "user-1", "type", "reasoning_bank_memory")))
                .hasSize(1)
                .allSatisfy(node -> assertThat(node.getEmbedding()).containsExactly(0.5d, 0.5d));
    }

    @Test
    void persistMemoryWritesReasoningBankNodesForUser() throws Exception {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        VectorNode first = reasoningNode("reasoning_bank_user-1_first", "user-1", "first");
        VectorNode second = reasoningNode("reasoning_bank_user-1_second", "user-1", "second");
        VectorNode other = reasoningNode("reasoning_bank_other_third", "other", "third");
        vectorStore.loadNode(first.getId(), first);
        vectorStore.loadNode(second.getId(), second);
        vectorStore.loadNode(other.getId(), other);
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");

        new PersistMemoryOp("json", "{algo_name}-{user_id}.json", "localhost", 19530, "vector_nodes", tempDir)
                .asyncExecute(context)
                .join();

        assertThat(context.get("persist_count")).isEqualTo(2);
        String persisted = Files.readString(tempDir.resolve("rb-user-1.json"));
        assertThat(persisted).contains("reasoning_bank_user-1_first", "reasoning_bank_user-1_second");
        assertThat(persisted).doesNotContain("reasoning_bank_other_third");
    }

    @SuppressWarnings("unchecked")
    private static List<ReasoningBankMemory> memories(RuntimeContext context) {
        return (List<ReasoningBankMemory>) context.get("memories");
    }

    private static String memoryResponse() {
        return """
                # Memory Item 1
                ## Title Prefer precise APIs
                ## Description Use the direct data source
                ## Content Choose the source API that owns the required fact before applying filters.
                """;
    }

    private static ReasoningBankMemory reasoningMemory(String query,
                                                       Boolean label,
                                                       String title,
                                                       String description,
                                                       String content) {
        ReasoningBankMemory memory = new ReasoningBankMemory();
        memory.setQuery(query);
        memory.setLabel(label);
        memory.setMemory(List.of(new ReasoningBankMemoryItem(title, description, content)));
        return memory;
    }

    private static VectorNode reasoningNode(String nodeId, String workspaceId, String query) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "reasoning_bank_memory");
        metadata.put("query", query);
        metadata.put("memory", List.of(Map.of("title", "title", "description", "desc", "content", "content")));
        metadata.put("label", true);
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(nodeId, query, List.of(1.0d), metadata);
    }

    private static final class RecordingLlm implements ReasoningBankAsyncLlm {
        private final List<String> responses;
        private int index;
        private String lastPrompt;
        private String lastSystemPrompt;

        private RecordingLlm(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public CompletableFuture<String> asyncGenerate(String prompt, String systemPrompt) {
            this.lastPrompt = prompt;
            this.lastSystemPrompt = systemPrompt;
            String response = responses.get(Math.min(index, responses.size() - 1));
            index += 1;
            return CompletableFuture.completedFuture(response);
        }

        private String getLastPrompt() {
            return lastPrompt;
        }

        private String getLastSystemPrompt() {
            return lastSystemPrompt;
        }

        private int getCalls() {
            return index;
        }
    }

    private static final class FakeEmbedding extends Embedding {
        private final List<Double> embedding;
        private List<String> lastTexts = List.of();

        private FakeEmbedding(List<Double> embedding) {
            this.embedding = new ArrayList<>(embedding);
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new ArrayList<>(embedding));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                     Integer batchSize,
                                                                     Map<String, Object> kwargs) {
            this.lastTexts = new ArrayList<>(texts);
            return CompletableFuture.completedFuture(texts.stream()
                    .map(ignored -> new ArrayList<>(embedding))
                    .map(values -> (List<Double>) values)
                    .toList());
        }

        @Override
        public int getDimension() {
            return embedding.size();
        }

        private List<String> getLastTexts() {
            return lastTexts;
        }
    }
}
