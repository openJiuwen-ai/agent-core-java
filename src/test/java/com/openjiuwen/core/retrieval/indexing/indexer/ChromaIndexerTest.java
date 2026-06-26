/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ChromaIndexer} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/chroma_indexer.py}.
 */
class ChromaIndexerTest {

    @Test
    void constructorValidatesPathVectorFieldAndCallback() {
        VectorStoreConfig config = VectorStoreConfig.builder()
                .storeProvider(StoreType.CHROMA)
                .databaseName("db")
                .distanceMetric("dot")
                .build();

        ChromaIndexer indexer = new ChromaIndexer(
                config,
                "target/chroma",
                "body",
                "vector",
                "sparse",
                "meta",
                "doc",
                RecordingCallback.class
        );

        assertThat(indexer.getDatabaseName()).isEqualTo("db");
        assertThat(indexer.getDistanceMetric()).isEqualTo("ip");
        assertThat(indexer.getVectorField().getVectorField()).isEqualTo("vector");
        assertThat(indexer.getConstructConfig()).containsEntry("space", "ip");

        assertThatThrownBy(() -> new ChromaIndexer(config, " "))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_PATH_NOT_FOUND);

        assertThatThrownBy(() -> new ChromaIndexer(
                config,
                "target/chroma",
                "body",
                42,
                "sparse",
                "meta",
                "doc",
                RecordingCallback.class
        ))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_VECTOR_FIELD_INVALID);

        assertThatThrownBy(() -> new ChromaIndexer(
                config,
                "target/chroma",
                "body",
                new ChromaVectorField(),
                "sparse",
                "meta",
                "doc",
                String.class
        ))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_EMBEDDING_CALLBACK_INVALID);
    }

    @Test
    void buildIndexRequiresEmbeddingForVectorAndHybridIndexes() {
        ChromaIndexer indexer = new ChromaIndexer(config(), "target/chroma");
        IndexConfig indexConfig = IndexConfig.builder().indexName("collection").indexType("vector").build();

        assertThatThrownBy(() -> indexer.buildIndex(List.of(chunk("c1", "doc1")), indexConfig, null, Map.of()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND);
    }

    @Test
    void buildIndexEmbedsAndStoresChunkDataThenReportsIndexInfo() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = new ChromaIndexer(
                config(),
                "target/chroma",
                "content",
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                RecordingCallback.class,
                client
        );
        TextChunk chunk = chunk("chunk-1", "doc-1");

        boolean success = indexer.buildIndex(
                List.of(chunk),
                IndexConfig.builder().indexName("collection").indexType("hybrid").useCaptionForImages(true).build(),
                new FakeEmbedding(),
                Map.of("database_name", "runtime-db")
        ).join();

        assertThat(success).isTrue();
        assertThat(chunk.getEmbedding()).containsExactly(1.0d, 2.0d);
        assertThat(client.collection.rows).hasSize(1);
        assertThat(client.collection.rows.getFirst())
                .containsEntry("id", "chunk-1")
                .containsEntry("document_id", "doc-1")
                .containsEntry("content", "body");
        assertThat(indexer.indexExists("collection").join()).isTrue();
        assertThat(indexer.getIndexInfo("collection").join())
                .containsEntry("exists", true)
                .containsEntry("collection_name", "collection")
                .containsEntry("count", 1);
    }

    @Test
    void duplicateDocIdsRaiseAddRuntimeErrorAndUpdateDeletesBeforeRebuilding() {
        ChromaIndexer indexer = new ChromaIndexer(config(), "target/chroma");
        IndexConfig indexConfig = IndexConfig.builder().indexName("collection").indexType("bm25").build();
        TextChunk first = chunk("chunk-1", "doc-1");

        assertThat(indexer.buildIndex(List.of(first), indexConfig, null, Map.of()).join()).isTrue();
        assertThatThrownBy(() -> indexer.buildIndex(List.of(chunk("chunk-2", "doc-1")), indexConfig, null, Map.of()).join())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR);

        assertThat(indexer.updateIndex(List.of(chunk("chunk-2", "doc-1")), "doc-1", indexConfig, null, Map.of()).join())
                .isTrue();
        assertThat(indexer.getIndexInfo("collection").join()).containsEntry("count", 1);
        assertThat(indexer.deleteIndex("doc-1", "collection", Map.of()).join()).isTrue();
        assertThat(indexer.deleteIndex("doc-1", "collection", Map.of()).join()).isFalse();
    }

    private static VectorStoreConfig config() {
        return VectorStoreConfig.builder()
                .storeProvider(StoreType.CHROMA)
                .databaseName("db")
                .distanceMetric("euclidean")
                .build();
    }

    private static TextChunk chunk(String chunkId, String docId) {
        return new TextChunk(chunkId, "body", docId, Map.of("source", "unit"));
    }

    private static final class RecordingCallback extends BaseCallback {
        private RecordingCallback(Iterable<?> seq) {
            super(seq);
        }
    }

    private static final class FakeEmbedding extends Embedding {
        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(1.0d, 2.0d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(texts.stream().map(ignored -> List.of(1.0d, 2.0d)).toList());
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class RecordingChromaClient implements ChromaIndexer.ChromaClientGateway {
        private RecordingChromaCollection collection;

        @Override
        public ChromaIndexer.ChromaCollectionGateway getCollection(String name) {
            if (collection == null) {
                throw new IllegalArgumentException("missing");
            }
            return collection;
        }

        @Override
        public ChromaIndexer.ChromaCollectionGateway getOrCreateCollection(String name, Map<String, Object> metadata) {
            if (collection == null) {
                collection = new RecordingChromaCollection(metadata);
            }
            return collection;
        }
    }

    private static final class RecordingChromaCollection implements ChromaIndexer.ChromaCollectionGateway {
        private final Map<String, Object> metadata;
        private final List<Map<String, Object>> rows = new ArrayList<>();

        private RecordingChromaCollection(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        @Override
        public Map<String, Object> metadata() {
            return metadata;
        }

        @Override
        public void add(List<Map<String, Object>> data) {
            rows.addAll(data);
        }

        @Override
        public List<String> idsWhere(Map<String, Object> where) {
            return rows.stream()
                    .filter(row -> where.entrySet().stream()
                            .allMatch(entry -> entry.getValue().equals(row.get(entry.getKey()))))
                    .map(row -> String.valueOf(row.get("id")))
                    .toList();
        }

        @Override
        public void delete(List<String> ids) {
            rows.removeIf(row -> ids.contains(String.valueOf(row.get("id"))));
        }

        @Override
        public int count() {
            return rows.size();
        }
    }
}
