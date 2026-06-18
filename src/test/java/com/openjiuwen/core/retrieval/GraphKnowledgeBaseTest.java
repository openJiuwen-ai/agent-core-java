/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code GraphKnowledgeBase} in
 * {@code openjiuwen/core/retrieval/graph_knowledge_base.py}.
 */
class GraphKnowledgeBaseTest {

    @Test
    void addDocumentsBuildsChunkAndTripleIndexes() {
        RecordingIndexer indexer = new RecordingIndexer();
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                KnowledgeBaseConfig.builder()
                        .kbId("graph")
                        .indexType("hybrid")
                        .useGraph(true)
                        .useCaptionForImages(true)
                        .build(),
                new FakeVectorStore("graph-db"),
                null,
                null,
                new SingleChunker(),
                new SingleTripleExtractor(),
                indexer,
                null,
                null,
                null
        );

        List<String> docIds = kb.addDocuments(List.of(new Document("doc-1", "Alice knows Bob", Map.of())), Map.of()).join();

        assertThat(docIds).containsExactly("doc-1");
        assertThat(indexer.buildConfigs).extracting(IndexConfig::getIndexName)
                .containsExactly("kb_graph_chunks", "kb_graph_triples");
        assertThat(indexer.buildConfigs.getFirst().isUseCaptionForImages()).isTrue();
        assertThat(indexer.buildConfigs.get(1).isUseCaptionForImages()).isTrue();
        assertThat(indexer.buildChunks.get(1)).hasSize(1);
        assertThat(indexer.buildChunks.get(1).getFirst().getText()).isEqualTo("Alice knows Bob");
        assertThat(indexer.buildChunks.get(1).getFirst().getMetadata())
                .containsEntry("triple", "[\"Alice\",\"knows\",\"Bob\"]")
                .containsEntry("chunk_id", "chunk-1");
        assertThat(indexer.buildOptions.getFirst()).containsEntry("database_name", "graph-db");
    }

    @Test
    void tripleDeleteFailureDoesNotFailOverallDeleteResult() {
        RecordingIndexer indexer = new RecordingIndexer();
        indexer.deleteResults = new ArrayList<>(List.of(Boolean.TRUE, Boolean.FALSE));
        GraphKnowledgeBase kb = new GraphKnowledgeBase(
                KnowledgeBaseConfig.builder().kbId("graph").useGraph(true).build(),
                null,
                null,
                null,
                null,
                null,
                indexer,
                null,
                null,
                null
        );

        Boolean deleted = kb.deleteDocuments(List.of("doc-1"), Map.of()).join();

        assertThat(deleted).isTrue();
        assertThat(indexer.deletedIndexNames).containsExactly("kb_graph_chunks", "kb_graph_triples");
    }

    private static final class SingleChunker extends Chunker {
        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }
    }

    private static final class SingleTripleExtractor extends Extractor {
        @Override
        public CompletableFuture<List<Triple>> extract(List<TextChunk> chunks) {
            return CompletableFuture.completedFuture(List.of(new Triple(
                    "Alice",
                    "knows",
                    "Bob",
                    Map.of("doc_id", "doc-1", "chunk_id", "chunk-1")
            )));
        }
    }

    private static final class RecordingIndexer extends Indexer {
        private final List<IndexConfig> buildConfigs = new ArrayList<>();
        private final List<List<TextChunk>> buildChunks = new ArrayList<>();
        private final List<Map<String, Object>> buildOptions = new ArrayList<>();
        private final List<String> deletedIndexNames = new ArrayList<>();
        private List<Boolean> deleteResults = new ArrayList<>(List.of(Boolean.TRUE));

        @Override
        public CompletableFuture<Boolean> buildIndex(
                List<TextChunk> chunks,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            buildChunks.add(List.copyOf(chunks));
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
            deletedIndexNames.add(indexName);
            Boolean result = deleteResults.isEmpty() ? Boolean.TRUE : deleteResults.removeFirst();
            return CompletableFuture.completedFuture(result);
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
