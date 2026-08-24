/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
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

/**
 * Mirrors Python's ACE summary update operations in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 */
class AceUpdateOpsTest {

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
    void loadPlaybookReadsAceBulletsAndAdvancesNextId() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("ace_user-1_strategies-00002",
                aceNode("ace_user-1_strategies-00002", "user-1", "strategies-00002", "strategies", "use api", List.of(1.0d)));
        vectorStore.loadNode("ace_user-1_rules-00005",
                aceNode("ace_user-1_rules-00005", "user-1", "rules-00005", "rules", "do not guess", List.of(1.0d)));
        vectorStore.loadNode("ace_other_rules-00099",
                aceNode("ace_other_rules-00099", "other", "rules-00099", "rules", "other", List.of(1.0d)));
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");

        new LoadPlaybookOp().asyncExecute(context).join();

        Playbook playbook = (Playbook) context.get("playbook");
        assertThat(playbook.bulletIds()).containsExactly("strategies-00002", "rules-00005");
        assertThat(playbook.addBullet("new section", "fresh").getId()).isEqualTo("new-00006");
    }

    @Test
    void reflectSkipsNonSequentialMattsWithoutRequiringLlm() {
        RuntimeContext context = new RuntimeContext();
        context.set("matts", "parallel");
        context.set("trajectories", List.of("trajectory"));

        new ReflectOp().asyncExecute(context).join();

        assertThat(context.get("reflection")).isNull();
    }

    @Test
    void reflectParsesJsonFromMarkdownResponse() {
        RecordingLlm llm = new RecordingLlm("""
                ```json
                {"reasoning": "parsed", "key_insight": "remember pagination"}
                ```
                """);
        serviceContext.registerService("llm", llm);
        RuntimeContext context = new RuntimeContext();
        context.set("matts", "none");
        context.set("trajectories", List.of("first trajectory"));
        context.set("playbook", new Playbook());

        new ReflectOp().asyncExecute(context).join();

        assertThat(llm.getLastPrompt()).contains("first trajectory");
        assertThat(CurateOp.reflection(context.get("reflection"))).containsEntry("reasoning", "parsed");
    }

    @Test
    void curateInvalidJsonFallsBackToEmptyDelta() {
        serviceContext.registerService("llm", new RecordingLlm("not-json"));
        RuntimeContext context = new RuntimeContext();
        context.set("matts", "none");
        context.set("query", "question");
        context.set("trajectories", List.of("trajectory"));
        context.set("reflection", Map.of("reasoning", "needs update"));
        context.set("playbook", new Playbook());

        new CurateOp().asyncExecute(context).join();

        Playbook.DeltaBatch delta = (Playbook.DeltaBatch) context.get("delta");
        assertThat(delta.getOperations()).isEmpty();
    }

    @Test
    void parallelCurateRequiresTwoTrajectories() {
        serviceContext.registerService("llm", new RecordingLlm("{}"));
        RuntimeContext context = new RuntimeContext();
        context.set("matts", "parallel");
        context.set("query", "question");
        context.set("trajectories", List.of("only one"));
        context.set("reflection", Map.of("reasoning", "needs update"));

        new ParallelCurateOp().asyncExecute(context).join();

        Playbook.DeltaBatch delta = (Playbook.DeltaBatch) context.get("delta");
        assertThat(delta.getOperations()).isEmpty();
    }

    @Test
    void applyDeltaHandlesSizeLimitUpdateTagRemoveAndUpsert() {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("ace_user-1_bad-00001",
                aceNode("ace_user-1_bad-00001", "user-1", "bad-00001", "bad", "bad advice", List.of(0.1d)));
        serviceContext.registerService("vector_store", vectorStore);
        serviceContext.registerService("embedding_model", new FakeEmbedding(List.of(0.25d, 0.75d)));
        Playbook playbook = new Playbook();
        Playbook.Bullet bad = playbook.addBullet("bad", "bad advice", "bad-00001", Map.of("helpful", 0, "harmful", 5));
        bad.setUpdatedAt("2026-01-01T00:00:00Z");
        Playbook.Bullet good = playbook.addBullet("good", "old advice", "good-00002", Map.of("helpful", 5, "harmful", 0));
        good.setUpdatedAt("2026-01-02T00:00:00Z");
        Playbook.DeltaBatch delta = new Playbook.DeltaBatch("curated", List.of(
                new Playbook.DeltaOperation("ADD", "fresh", "fresh advice", null, Map.of("neutral", 1)),
                new Playbook.DeltaOperation("UPDATE", "", "converted advice", "strategies-00099", Map.of("helpful", 2)),
                new Playbook.DeltaOperation("UPDATE", "good", "updated advice", "good-00002", Map.of("helpful", 7)),
                new Playbook.DeltaOperation("TAG", "", null, "good-00002", Map.of("helpful", 2)),
                new Playbook.DeltaOperation("REMOVE", "", null, "bad-00001", Map.of())
        ));
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");
        context.set("playbook", playbook);
        context.set("delta", delta);

        new ApplyDeltaOp(2).asyncExecute(context).join();

        assertThat(playbook.getBullet("bad-00001")).isNull();
        assertThat(playbook.getBullet("good-00002").getContent()).isEqualTo("updated advice");
        assertThat(playbook.getBullet("good-00002").getHelpful()).isEqualTo(9);
        assertThat(playbook.bullets()).extracting(Playbook.Bullet::getContent)
                .contains("fresh advice", "converted advice", "updated advice");
        assertThat(vectorStore.getAll(Map.of("workspace_id", "user-1", "type", "ace_memory")))
                .extracting(VectorNode::getId)
                .contains("ace_user-1_good-00002")
                .doesNotContain("ace_user-1_bad-00001");
        assertThat(memories(context)).extracting(ACEMemory::getContent)
                .containsExactly("fresh advice", "converted advice", "updated advice");
    }

    @Test
    void persistMemoryWritesAllAceNodesForUser() throws Exception {
        MemoryVectorStore vectorStore = new MemoryVectorStore();
        vectorStore.loadNode("ace_user-1_first",
                aceNode("ace_user-1_first", "user-1", "first", "rules", "first content", List.of(0.1d)));
        vectorStore.loadNode("ace_user-1_second",
                aceNode("ace_user-1_second", "user-1", "second", "rules", "second content", List.of(0.2d)));
        vectorStore.loadNode("ace_other_third",
                aceNode("ace_other_third", "other", "third", "rules", "third content", List.of(0.3d)));
        serviceContext.registerService("vector_store", vectorStore);
        RuntimeContext context = new RuntimeContext();
        context.set("user_id", "user-1");

        new PersistMemoryOp("json", "{algo_name}-{user_id}.json", "localhost", 19530, "vector_nodes", tempDir)
                .asyncExecute(context)
                .join();

        assertThat(context.get("persist_count")).isEqualTo(2);
        String persisted = Files.readString(tempDir.resolve("ace-user-1.json"));
        assertThat(persisted).contains("ace_user-1_first", "ace_user-1_second");
        assertThat(persisted).doesNotContain("ace_other_third");
    }

    @SuppressWarnings("unchecked")
    private static List<ACEMemory> memories(RuntimeContext context) {
        return (List<ACEMemory>) context.get("memories");
    }

    private static VectorNode aceNode(String nodeId,
                                      String workspaceId,
                                      String bulletId,
                                      String section,
                                      String content,
                                      List<Double> embedding) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "ace_memory");
        metadata.put("id", bulletId);
        metadata.put("section", section);
        metadata.put("content", content);
        metadata.put("helpful", 1);
        metadata.put("harmful", 0);
        metadata.put("neutral", 0);
        metadata.put("created_at", "2026-01-01T00:00:00Z");
        metadata.put("updated_at", "2026-01-02T00:00:00Z");
        metadata.put("workspace_id", workspaceId);
        return new VectorNode(nodeId, content, embedding, metadata);
    }

    private static final class RecordingLlm implements AceAsyncLlm {
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

    private static final class FakeEmbedding extends Embedding {
        private final List<Double> embedding;

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
            return CompletableFuture.completedFuture(texts.stream()
                    .map(ignored -> new ArrayList<>(embedding))
                    .map(values -> (List<Double>) values)
                    .toList());
        }

        @Override
        public int getDimension() {
            return embedding.size();
        }
    }
}
