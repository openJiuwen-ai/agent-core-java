/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.TripleBeam;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.retriever.AbstractStoreBackedRetriever;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.HybridRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.retriever.TripleBeamSearch;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Graph retriever test cases.
 *
 * <p>Mirrors Python's {@code test_graph_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_graph_retriever.py}.</p>
 */
@DisplayName("GraphRetriever Tests")
class TestGraphRetriever {

    @Test
    @DisplayName("test_init_with_retrievers")
    void testInitWithRetrievers() throws Exception {
        RecordingRetriever chunkRetriever = new RecordingRetriever("hybrid");
        RecordingRetriever tripleRetriever = new RecordingRetriever("hybrid");

        GraphRetriever retriever = new GraphRetriever(chunkRetriever, tripleRetriever);

        assertThat(field(retriever, "chunkRetriever")).isSameAs(chunkRetriever);
        assertThat(field(retriever, "tripleRetriever")).isSameAs(tripleRetriever);
    }

    @Test
    @DisplayName("test_init_with_vector_store")
    void testInitWithVectorStore() throws Exception {
        VectorStore vectorStore = mockVectorStore();
        Embedding embedModel = new FakeEmbedding();

        GraphRetriever retriever = new GraphRetriever(vectorStore, embedModel, "chunks", "triples");

        assertThat(field(retriever, "vectorStore")).isSameAs(vectorStore);
        assertThat(field(retriever, "embedModel")).isSameAs(embedModel);
        assertThat(field(retriever, "chunkCollection")).isEqualTo("chunks");
        assertThat(field(retriever, "tripleCollection")).isEqualTo("triples");
    }

    @Test
    @DisplayName("test_retrieve_score_threshold_invalid_mode")
    void testRetrieveScoreThresholdInvalidMode() {
        GraphRetriever retriever = new GraphRetriever(new RecordingRetriever("hybrid"), null);

        assertThatThrownBy(() -> retriever.retrieve("test query", 5, 0.8, "sparse", Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("score_threshold is only supported");
    }

    @Test
    @DisplayName("test_graph_expansion_empty_chunks")
    void testGraphExpansionEmptyChunks() {
        GraphRetriever retriever = new GraphRetriever(new RecordingRetriever("hybrid"), null);

        List<RetrievalResult> results = retriever.graphExpansion("test query", List.of(), null, 5, "hybrid", Map.of());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("test_close")
    void testClose() {
        RecordingRetriever chunkRetriever = new RecordingRetriever("hybrid");
        RecordingRetriever tripleRetriever = new RecordingRetriever("hybrid");
        GraphRetriever retriever = new GraphRetriever(chunkRetriever, tripleRetriever);

        retriever.close();

        assertThat(chunkRetriever.closeCalls).isEqualTo(1);
        assertThat(tripleRetriever.closeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_close_sync_close")
    void testCloseSyncClose() {
        RecordingRetriever chunkRetriever = new RecordingRetriever("hybrid");
        GraphRetriever retriever = new GraphRetriever(chunkRetriever, null);

        retriever.close();

        assertThat(chunkRetriever.closeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_close_no_close_method")
    void testCloseNoCloseMethod() {
        Retriever defaultCloseRetriever = new Retriever() {
            @Override
            public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold,
                                                  String mode, Map<String, Object> options) {
                return List.of();
            }

            @Override
            public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK,
                                                             String mode, Map<String, Object> options) {
                return List.of();
            }
        };
        GraphRetriever retriever = new GraphRetriever(defaultCloseRetriever, null);

        retriever.close();

        assertThat(retriever.getIndexType()).isEqualTo("hybrid");
    }

    @Test
    @DisplayName("test_init_invalid_max_length")
    void testInitInvalidMaxLength() {
        assertThatThrownBy(() -> new TripleBeamSearch(new SearchableRetriever("hybrid"), 10, 100, 0))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("expect max_length >= 1");
    }

    @Test
    @DisplayName("test_beam_search_no_embed_model")
    void testBeamSearchNoEmbedModel() {
        TripleBeamSearch search = new TripleBeamSearch(new RecordingRetriever("hybrid"));

        assertThatThrownBy(() -> search.beamSearch("test query", List.of(triple("triple1", "a", "rel", "b"))))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("embed_model is required");
    }

    @Test
    @DisplayName("test_beam_search_basic")
    void testBeamSearchBasic() {
        SearchableRetriever retriever = new SearchableRetriever("hybrid");
        retriever.results = List.of();
        TripleBeamSearch search = new TripleBeamSearch(retriever, 2, 100, 2);
        List<RetrievalResult> triples = List.of(
                triple("entity1 relation entity2", "entity1", "relation", "entity2"),
                triple("entity3 relation entity4", "entity3", "relation", "entity4"));

        List<TripleBeam> beams = search.beamSearch("test query", triples);

        assertThat(beams).hasSize(2);
        assertThat(beams).allSatisfy(beam -> assertThat(beam).isInstanceOf(TripleBeam.class));
        assertThat(beams).allSatisfy(beam -> assertThat(beam.size()).isEqualTo(1));
    }

    @Test
    @DisplayName("test_search_candidates_bm25_index_type_uses_sparse_mode")
    void testSearchCandidatesBm25IndexTypeUsesSparseMode() {
        SearchableRetriever retriever = new SearchableRetriever("bm25");
        TripleBeamSearch search = new TripleBeamSearch(retriever, 1, 50, 2);

        search.beamSearch("test query", List.of(triple("entity1 relation entity2", "entity1", "relation", "entity2")));

        assertThat(retriever.modes).contains("sparse");
        assertThat(retriever.topKs).contains(50);
        assertThat(retriever.queries.get(0)).contains("entity1").contains("entity2");
    }

    @Test
    @DisplayName("test_search_candidates_non_bm25_index_type_passes_through")
    void testSearchCandidatesNonBm25IndexTypePassesThrough() {
        SearchableRetriever retriever = new SearchableRetriever("hybrid");
        TripleBeamSearch search = new TripleBeamSearch(retriever, 1, 100, 2);

        search.beamSearch("test query", List.of(triple("a rel b", "a", "rel", "b")));

        assertThat(retriever.modes).contains("hybrid");
    }

    @Test
    @DisplayName("test_search_candidates_uses_retrieve_mode_when_no_index_type")
    void testSearchCandidatesUsesRetrieveModeWhenNoIndexType() {
        SearchableRetriever retriever = new SearchableRetriever(null);
        TripleBeamSearch search = new TripleBeamSearch(retriever, 1, 7, 2, "hybrid");

        search.beamSearch("test query", List.of(triple("entity1 relation entity2", "entity1", "relation", "entity2")));

        assertThat(retriever.modes).contains("hybrid");
        assertThat(retriever.topKs).contains(7);
    }

    @Test
    @DisplayName("test_search_candidates_retrieve_mode_vector_when_no_index_type")
    void testSearchCandidatesRetrieveModeVectorWhenNoIndexType() {
        SearchableRetriever retriever = new SearchableRetriever(null);
        TripleBeamSearch search = new TripleBeamSearch(retriever, 1, 3, 2, "vector");

        search.beamSearch("test query", List.of(triple("x rel y", "x", "rel", "y")));

        assertThat(retriever.modes).contains("vector");
    }

    @Test
    @DisplayName("test_search_candidates_defaults_hybrid_without_index_type_or_retrieve_mode")
    void testSearchCandidatesDefaultsHybridWithoutIndexTypeOrRetrieveMode() {
        SearchableRetriever retriever = new SearchableRetriever(null);
        TripleBeamSearch search = new TripleBeamSearch(retriever, 1, 5, 2);

        search.beamSearch("test query", List.of(triple("a rel b", "a", "rel", "b")));

        assertThat(retriever.modes).contains("hybrid");
    }

    @Test
    @DisplayName("test_search_candidates_retriever_index_type_overrides_retrieve_mode")
    void testSearchCandidatesRetrieverIndexTypeOverridesRetrieveMode() {
        SearchableRetriever retriever = new SearchableRetriever("vector");
        TripleBeamSearch search = new TripleBeamSearch(retriever, 1, 2, 2, "hybrid");

        search.beamSearch("test query", List.of(triple("p rel q", "p", "rel", "q")));

        assertThat(retriever.modes).contains("vector");
    }

    @Test
    @DisplayName("test_search_candidates_real_hybrid_retriever_no_index_type_uses_retrieve_mode")
    void testSearchCandidatesRealHybridRetrieverNoIndexTypeUsesRetrieveMode() {
        VectorStore vectorStore = mockVectorStore();
        when(vectorStore.getIndexType()).thenReturn(null);
        when(vectorStore.hybridSearch(anyString(), anyList(), anyInt(), anyDouble(),
                nullable(Map.class), nullable(Map.class))).thenReturn(List.of());
        HybridRetriever hybridRetriever = new HybridRetriever(vectorStore, new FakeEmbedding());
        TripleBeamSearch search = new TripleBeamSearch(hybridRetriever, 1, 11, 2, "hybrid");

        search.beamSearch("test query", List.of(triple("u rel v", "u", "rel", "v")));

        verify(vectorStore).hybridSearch(anyString(), anyList(), eq(11), anyDouble(),
                nullable(Map.class), nullable(Map.class));
    }

    @Test
    @DisplayName("test_injects_index_type_hybrid_onto_hybrid_triple_retriever")
    void testInjectsIndexTypeHybridOntoHybridTripleRetriever() {
        VectorStore vectorStore = mockVectorStore();
        GraphRetriever graphRetriever = new GraphRetriever(vectorStore, new FakeEmbedding(), "kb_x_chunks", "kb_x_triples");
        graphRetriever.setIndexType("hybrid");

        Retriever tripleRetriever = graphRetriever.getRetrieverForMode("hybrid", false);

        assertThat(tripleRetriever.getIndexType()).isEqualTo("hybrid");
    }

    @Test
    @DisplayName("test_injects_index_type_onto_vector_retriever")
    void testInjectsIndexTypeOntoVectorRetriever() {
        VectorStore vectorStore = mockVectorStore();
        GraphRetriever graphRetriever = new GraphRetriever(vectorStore, new FakeEmbedding(), "kb_x_chunks", "kb_x_triples");
        graphRetriever.setIndexType("vector");

        Retriever retriever = graphRetriever.getRetrieverForMode("vector", true);

        assertThat(retriever.getIndexType()).isEqualTo("vector");
    }

    @Test
    @DisplayName("test_skips_injection_when_graph_index_type_unset")
    void testSkipsInjectionWhenGraphIndexTypeUnset() {
        VectorStore vectorStore = mockVectorStore();
        GraphRetriever graphRetriever = new GraphRetriever(vectorStore, new FakeEmbedding(), "kb_x_chunks", "kb_x_triples");

        Retriever tripleRetriever = graphRetriever.getRetrieverForMode("hybrid", false);

        assertThat(tripleRetriever.getIndexType()).isNull();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static RetrievalResult triple(String text, String subject, String predicate, String object) {
        return new RetrievalResult(text, 0.9,
                Map.of("triple", "[\"" + subject + "\", \"" + predicate + "\", \"" + object + "\"]"),
                "doc_1",
                "chunk_1");
    }

    private static VectorStore mockVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.withCollection(anyString())).thenReturn(vectorStore);
        when(vectorStore.search(anyList(), anyInt(), nullable(Map.class), nullable(Map.class))).thenReturn(List.of());
        when(vectorStore.sparseSearch(anyString(), anyInt(), nullable(Map.class), nullable(Map.class))).thenReturn(List.of());
        when(vectorStore.hybridSearch(anyString(), anyList(), anyInt(), anyDouble(),
                nullable(Map.class), nullable(Map.class))).thenReturn(List.of());
        return vectorStore;
    }

    private static final class FakeEmbedding implements Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return vectorFor(text);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(FakeEmbedding::vectorFor).toList();
        }

        @Override
        public int getDimension() {
            return 3;
        }

        private static List<Float> vectorFor(String text) {
            int hash = Math.abs(text == null ? 0 : text.hashCode());
            return List.of(1.0f + hash % 3, 0.5f + hash % 5, 0.25f + hash % 7);
        }
    }

    private static class RecordingRetriever implements Retriever {
        private final String indexType;
        private int closeCalls;

        private RecordingRetriever(String indexType) {
            this.indexType = indexType;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold,
                                              String mode, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK,
                                                         String mode, Map<String, Object> options) {
            return queries.stream().map(query -> List.<RetrievalResult>of()).toList();
        }

        @Override
        public String getIndexType() {
            return indexType;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static final class SearchableRetriever extends AbstractStoreBackedRetriever {
        private List<RetrievalResult> results = List.of();
        private final List<String> modes = new ArrayList<>();
        private final List<Integer> topKs = new ArrayList<>();
        private final List<String> queries = new ArrayList<>();

        private SearchableRetriever(String indexType) {
            super(null, new FakeEmbedding());
            setIndexType(indexType);
        }

        @Override
        public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold,
                                              String mode, Map<String, Object> options) {
            queries.add(query);
            topKs.add(topK);
            modes.add(mode);
            return results;
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK,
                                                         String mode, Map<String, Object> options) {
            return queries.stream().map(query -> results).toList();
        }
    }
}
