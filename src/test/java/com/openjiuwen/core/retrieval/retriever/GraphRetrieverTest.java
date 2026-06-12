/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TripleBeam;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's graph retriever behavior in
 * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
 */
class GraphRetrieverTest {

    @Test
    void initializationPreservesProvidedRetrievers() {
        FakeRetriever chunkRetriever = new FakeRetriever();
        FakeRetriever tripleRetriever = new FakeRetriever();

        GraphRetriever retriever = new GraphRetriever(chunkRetriever, tripleRetriever);

        assertThat(retriever.getChunkRetriever()).isSameAs(chunkRetriever);
        assertThat(retriever.getTripleRetriever()).isSameAs(tripleRetriever);
    }

    @Test
    void initializationPreservesVectorStoreSettings() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeEmbedding embedding = new FakeEmbedding();

        GraphRetriever retriever = new GraphRetriever(vectorStore, embedding, "chunks", "triples");

        assertThat(retriever.getVectorStore()).isSameAs(vectorStore);
        assertThat(retriever.getEmbedModel()).isSameAs(embedding);
        assertThat(retriever.getChunkCollection()).isEqualTo("chunks");
        assertThat(retriever.getTripleCollection()).isEqualTo("triples");
    }

    @Test
    void retrieveRejectsScoreThresholdOutsideVectorMode() {
        GraphRetriever retriever = new GraphRetriever(new FakeRetriever(), null);

        assertThatThrownBy(() -> retriever.retrieve("query", 5, 0.8d, "sparse", Map.of()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID);
    }

    @Test
    void graphExpansionWithEmptyHybridChunksReturnsEmptyList() {
        GraphRetriever retriever = new GraphRetriever(new FakeRetriever(), null);

        List<RetrievalResult> results = retriever.graphExpansion("query", List.of(), 5, "hybrid");

        assertThat(results).isEmpty();
    }

    @Test
    void graphExpansionWithEmptySparseChunksFallsBackToSparseRetriever() {
        FakeRetriever chunkRetriever = new FakeRetriever();
        chunkRetriever.results = List.of(result("fallback", "fallback chunk", 0.7d, Map.of("chunk_id", "fallback")));
        GraphRetriever retriever = new GraphRetriever(chunkRetriever, null);

        List<RetrievalResult> results = retriever.graphExpansion("query", List.of(), 5, "sparse");

        assertThat(results).extracting(RetrievalResult::getText).containsExactly("fallback chunk");
        assertThat(chunkRetriever.calls).hasSize(1);
        assertThat(chunkRetriever.calls.getFirst().mode()).isEqualTo("sparse");
    }

    @Test
    void getRetrieverForModeRejectsFixedRetrieverThatCannotSupportMode() {
        FakeRetriever chunkRetriever = new FakeRetriever();
        chunkRetriever.supportedModes = Set.of("vector");
        GraphRetriever retriever = new GraphRetriever(chunkRetriever, null);

        assertThatThrownBy(() -> retriever.getRetrieverForMode("sparse", true))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_CAPABILITY_NOT_SUPPORT);
    }

    @Test
    void dynamicRetrieverPreservesInjectedGraphIndexType() {
        GraphRetriever retriever = new GraphRetriever(
                new FakeVectorStore(),
                new FakeEmbedding(),
                "chunk_collection",
                "triple_collection"
        );
        retriever.setIndexType("hybrid");

        Retriever dynamicRetriever = retriever.getRetrieverForMode("hybrid", false);

        assertThat(dynamicRetriever.getIndexType()).isEqualTo("hybrid");
    }

    @Test
    void dynamicRetrieverSkipsIndexTypeWhenGraphIndexTypeIsUnset() {
        GraphRetriever retriever = new GraphRetriever(
                new FakeVectorStore(),
                new FakeEmbedding(),
                "chunk_collection",
                "triple_collection"
        );

        Retriever dynamicRetriever = retriever.getRetrieverForMode("hybrid", false);

        assertThat(dynamicRetriever.getIndexType()).isNull();
    }

    @Test
    void graphExpansionWithTriplesRunsBeamSearchFetchesChunksAndFusesResults() {
        FakeRetriever chunkRetriever = new FakeRetriever();
        chunkRetriever.results = List.of(result("chunk-a", "expanded chunk", 0.5d, Map.of("chunk_id", "chunk-a")));
        FakeRetriever tripleRetriever = new FakeRetriever(new FakeEmbedding());
        GraphRetriever retriever = new GraphRetriever(chunkRetriever, tripleRetriever);
        List<RetrievalResult> chunks = List.of(result("seed", "seed chunk", 0.8d, Map.of("chunk_id", "seed")));
        List<RetrievalResult> triples = List.of(
                result("chunk-a", "entity1 relation entity2", 0.9d, Map.of(
                        "chunk_id", "chunk-a",
                        "triple", "[\"entity1\", \"relation\", \"entity2\"]"
                ))
        );

        List<RetrievalResult> results = retriever.graphExpansion("entity1", chunks, triples, 2, "hybrid", Map.of("max_length", 1));

        assertThat(results).extracting(RetrievalResult::getText).contains("expanded chunk", "seed chunk");
        assertThat(chunkRetriever.calls).hasSize(1);
        assertThat(chunkRetriever.calls.getFirst().options()).containsKey("chunk_ids");
    }

    @Test
    void tripleBeamSearchRejectsInvalidMaxLength() {
        assertThatThrownBy(() -> new TripleBeamSearch(new FakeRetriever(new FakeEmbedding()), 10, 100, 0, 256, null))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_MODE_INVALID);
    }

    @Test
    void tripleBeamSearchRequiresEmbedModel() {
        TripleBeamSearch search = new TripleBeamSearch(new FakeRetriever());

        assertThatThrownBy(() -> search.beamSearch("query", List.of(result("a", "triple1", 0.9d, Map.of()))))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND);
    }

    @Test
    void tripleBeamSearchRanksInitialTriplesAndKeepsUnexpandedBeams() {
        FakeRetriever retriever = new FakeRetriever(new FakeEmbedding());
        retriever.results = List.of();
        TripleBeamSearch search = new TripleBeamSearch(retriever, 2, 100, 2, 256, null);
        List<RetrievalResult> triples = List.of(
                result("chunk-1", "entity1 relation entity2", 0.9d, Map.of("triple", "[\"entity1\", \"relation\", \"entity2\"]")),
                result("chunk-2", "entity3 relation entity4", 0.8d, Map.of("triple", "[\"entity3\", \"relation\", \"entity4\"]"))
        );

        List<TripleBeam> beams = search.beamSearch("entity1", triples);

        assertThat(beams).hasSize(2);
        assertThat(beams).allMatch(beam -> beam.size() == 1);
        assertThat(beams.getFirst().get(0).getText()).isEqualTo("entity1 relation entity2");
    }

    @Test
    void searchCandidatesMapsBm25IndexTypeToSparseMode() {
        FakeRetriever retriever = new FakeRetriever(new FakeEmbedding());
        retriever.indexType = "bm25";
        TripleBeamSearch search = new TripleBeamSearch(retriever, 10, 50, 2, 256, null);
        TripleBeam beam = new TripleBeam(List.of(result(
                "chunk-1",
                "entity1 relation entity2",
                0.9d,
                Map.of("triple", "[\"entity1\", \"relation\", \"entity2\"]")
        )), 0.9d);

        search.searchCandidates(beam);

        assertThat(retriever.calls).hasSize(1);
        assertThat(retriever.calls.getFirst().mode()).isEqualTo("sparse");
        assertThat(retriever.calls.getFirst().topK()).isEqualTo(50);
        assertThat(retriever.calls.getFirst().query()).contains("entity1").contains("entity2");
    }

    @Test
    void searchCandidatesUsesRetrieveModeWhenRetrieverIndexTypeUnset() {
        FakeRetriever retriever = new FakeRetriever(new FakeEmbedding());
        TripleBeamSearch search = new TripleBeamSearch(retriever, 10, 7, 2, 256, "vector");
        TripleBeam beam = new TripleBeam(List.of(result(
                "chunk-1",
                "x relation y",
                1.0d,
                Map.of("triple", "[\"x\", \"relation\", \"y\"]")
        )), 1.0d);

        search.searchCandidates(beam);

        assertThat(retriever.calls.getFirst().mode()).isEqualTo("vector");
        assertThat(retriever.calls.getFirst().topK()).isEqualTo(7);
    }

    private static RetrievalResult result(String chunkId, String text, double score, Map<String, Object> metadata) {
        return new RetrievalResult(text, score, metadata, null, chunkId);
    }

    private record RetrieveCall(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options) {
    }

    private static final class FakeRetriever implements Retriever, RetrieverEmbeddingProvider {
        private List<RetrievalResult> results = List.of();
        private Set<String> supportedModes = new LinkedHashSet<>(List.of("vector", "sparse", "hybrid"));
        private String indexType;
        private final Embedding embedModel;
        private final List<RetrieveCall> calls = new ArrayList<>();

        private FakeRetriever() {
            this(null);
        }

        private FakeRetriever(Embedding embedModel) {
            this.embedModel = embedModel;
        }

        @Override
        public List<RetrievalResult> retrieve(
                String query,
                int topK,
                Double scoreThreshold,
                String mode,
                Map<String, Object> options
        ) {
            calls.add(new RetrieveCall(query, topK, scoreThreshold, mode, new LinkedHashMap<>(options == null ? Map.of() : options)));
            return results;
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(
                List<String> queries,
                int topK,
                String mode,
                Map<String, Object> options
        ) {
            List<List<RetrievalResult>> values = new ArrayList<>();
            for (String ignored : queries) {
                values.add(results);
            }
            return values;
        }

        @Override
        public boolean supportsMode(String mode) {
            return supportedModes.contains(mode);
        }

        @Override
        public String getIndexType() {
            return indexType;
        }

        @Override
        public Embedding getEmbedModel() {
            return embedModel;
        }
    }

    private static final class FakeEmbedding extends Embedding {

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(vectorFor(text));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            List<List<Double>> values = new ArrayList<>();
            for (String text : texts) {
                values.add(vectorFor(text));
            }
            return CompletableFuture.completedFuture(values);
        }

        @Override
        public int getDimension() {
            return 2;
        }

        private static List<Double> vectorFor(String text) {
            if (text != null && (text.contains("entity1") || text.contains("chunk-a"))) {
                return List.of(1.0d, 0.0d);
            }
            return List.of(0.0d, 1.0d);
        }
    }

    private static final class FakeVectorStore implements VectorStore {

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
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
