/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code SimpleKnowledgeBase} in
 * {@code openjiuwen/core/retrieval/simple_knowledge_base.py}.
 */
class SimpleKnowledgeBaseTest {

    @Test
    void addDocumentsBuildsChunkIndexWithCaptionFlagAndVectorStoreDatabaseName() {
        RecordingIndexer indexer = new RecordingIndexer();
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                KnowledgeBaseConfig.builder()
                        .kbId("kb1")
                        .indexType("vector")
                        .useCaptionForImages(true)
                        .build(),
                new FakeVectorStore("database-a"),
                null,
                null,
                new SingleChunker(),
                null,
                indexer,
                null,
                null
        );
        Document document = new Document("", "hello world", Map.of("source", "unit"));

        List<String> docIds = kb.addDocuments(List.of(document), Map.of("extra", "value")).join();

        assertThat(docIds).hasSize(1);
        assertThat(docIds.get(0)).isNotBlank();
        assertThat(indexer.buildConfigs).hasSize(1);
        assertThat(indexer.buildConfigs.get(0).getIndexName()).isEqualTo("kb_kb1_chunks");
        assertThat(indexer.buildConfigs.get(0).getIndexType()).isEqualTo("vector");
        assertThat(indexer.buildConfigs.get(0).isUseCaptionForImages()).isTrue();
        assertThat(indexer.buildOptions.get(0))
                .containsOnly(Map.entry("database_name", "database-a"));
    }

    @Test
    void retrieveUsesProvidedRetrieverWithResolvedModeAndOptions() {
        RecordingRetriever retriever = new RecordingRetriever("bm25");
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                KnowledgeBaseConfig.builder().kbId("kb1").indexType("bm25").build(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                retriever
        );
        RetrievalConfig config = RetrievalConfig.builder()
                .topK(3)
                .filters(Map.of("source", "docs"))
                .graphExpansion(true)
                .build();

        kb.retrieve("query", config, Map.of("custom", "value")).join();

        assertThat(retriever.calls).hasSize(1);
        RetrieveCall call = retriever.calls.get(0);
        assertThat(call.query()).isEqualTo("query");
        assertThat(call.topK()).isEqualTo(3);
        assertThat(call.mode()).isEqualTo("sparse");
        assertThat(call.options())
                .containsOnly(Map.entry("filters", Map.of("source", "docs")));
    }

    @Test
    void retrieveMultiKbUsesPythonLimitAndWithSourceMergeSemantics() {
        KnowledgeBase first = new FixedKnowledgeBase("kb-b", List.of(
                new RetrievalResult("same", 0.4d, Map.of("raw_score", 9.0d, "raw_score_scaled", 1.0d, "marker", "first"), "doc1", "chunk1"),
                new RetrievalResult("other", 0.3d, Map.of(), "doc2", "chunk2")
        ));
        KnowledgeBase second = new FixedKnowledgeBase("kb-a", List.of(
                new RetrievalResult("same", 0.8d, Map.of("raw_score", 5.0d, "raw_score_scaled", 7.0d, "marker", "second"), "doc3", "chunk3")
        ));
        RetrievalConfig config = RetrievalConfig.builder().topK(2).build();

        List<String> mergedTexts = SimpleKnowledgeBase.retrieveMultiKb(List.of(first, second), "query", config, 0).join();
        List<MultiKBRetrievalResult> mergedWithSource = SimpleKnowledgeBase
                .retrieveMultiKbWithSource(List.of(first, second), "query", config, 0)
                .join();

        assertThat(mergedTexts).containsExactly("same", "other");
        assertThat(mergedWithSource).hasSize(2);
        MultiKBRetrievalResult same = mergedWithSource.get(0);
        assertThat(same.getText()).isEqualTo("same");
        assertThat(same.getScore()).isEqualTo(0.8d);
        assertThat(same.getRawScore()).isEqualTo(9.0d);
        assertThat(same.getRawScoreScaled()).isEqualTo(7.0d);
        assertThat(same.getKbIds()).containsExactly("kb-a", "kb-b");
        assertThat(same.getMetadata()).containsEntry("marker", "first");
    }

    private record RetrieveCall(String query, int topK, String mode, Map<String, Object> options) {
    }

    private static final class SingleChunker extends Chunker {
        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }
    }

    private static final class RecordingRetriever implements Retriever {
        private final String indexType;
        private final List<RetrieveCall> calls = new ArrayList<>();

        private RecordingRetriever(String indexType) {
            this.indexType = indexType;
        }

        @Override
        public List<RetrievalResult> retrieve(
                String query,
                int topK,
                Double scoreThreshold,
                String mode,
                Map<String, Object> options
        ) {
            calls.add(new RetrieveCall(query, topK, mode, new LinkedHashMap<>(options)));
            return List.of(new RetrievalResult("text", 1.0d, Map.of(), "doc", "chunk"));
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(
                List<String> queries,
                int topK,
                String mode,
                Map<String, Object> options
        ) {
            return List.of();
        }

        @Override
        public String getIndexType() {
            return indexType;
        }
    }

    private static final class FixedKnowledgeBase extends KnowledgeBase {
        private final List<RetrievalResult> results;

        private FixedKnowledgeBase(String kbId, List<RetrievalResult> results) {
            super(KnowledgeBaseConfig.builder().kbId(kbId).build());
            this.results = results;
        }

        @Override
        public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> retrieve(
                String query,
                RetrievalConfig config,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class RecordingIndexer extends Indexer {
        private final List<IndexConfig> buildConfigs = new ArrayList<>();
        private final List<Map<String, Object>> buildOptions = new ArrayList<>();

        @Override
        public CompletableFuture<Boolean> buildIndex(
                List<TextChunk> chunks,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            buildConfigs.add(config);
            buildOptions.add(new LinkedHashMap<>(kwargs));
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> updateIndex(
                List<TextChunk> chunks,
                String docId,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> indexExists(String indexName) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class FakeVectorStore implements VectorStore {
        private final FakeVectorStoreConfig config;

        private FakeVectorStore(String databaseName) {
            this.config = new FakeVectorStoreConfig(databaseName);
        }

        public FakeVectorStoreConfig getConfig() {
            return config;
        }

        @Override
        public void checkVectorField() {
        }

        @Override
        public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> search(
                List<Double> queryVector,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> hybridSearch(
                String queryText,
                List<Double> queryVector,
                int topK,
                double alpha,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private record FakeVectorStoreConfig(String databaseName) {
        public String getDatabaseName() {
            return databaseName;
        }
    }
}
