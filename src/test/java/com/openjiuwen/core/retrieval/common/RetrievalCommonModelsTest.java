package com.openjiuwen.core.retrieval.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalCommonModelsTest {

    @Test
    void callbacksTrackCallsAndCloseWhenSequenceIsExhausted() {
        BaseCallback baseCallback = new BaseCallback(List.of("a", "b"));
        baseCallback.call(0, 1, List.of("a"));

        TqdmCallback tqdmCallback = new TqdmCallback(List.of("a", "b"), false, "Indexing");
        tqdmCallback.call(0, 1, List.of("a"));
        tqdmCallback.call(1, 2, List.of("b"));

        assertThat(baseCallback.getCallCounter()).isEqualTo(1);
        assertThat(tqdmCallback.getCallCounter()).isEqualTo(2);
        assertThat(tqdmCallback.length()).isEqualTo(2);
        assertThat(tqdmCallback.isClosed()).isTrue();
    }

    @Test
    void retrievalResultModelsKeepPythonDefaults() {
        SearchResult searchResult = new SearchResult("id", "text", 0.5, null);
        RetrievalResult retrievalResult = new RetrievalResult("text", 0.8, Map.of("lang", "zh"), null, "chunk-1");
        MultiKBRetrievalResult multi = new MultiKBRetrievalResult("text", 0.7, 7.0, 0.7, List.of("kb-a"), null);

        assertThat(searchResult.getMetadata()).isEmpty();
        assertThat(retrievalResult.getMetadata()).containsEntry("lang", "zh");
        assertThat(retrievalResult.getDocId()).isNull();
        assertThat(retrievalResult.getChunkId()).isEqualTo("chunk-1");
        assertThat(multi.getKbIds()).containsExactly("kb-a");
        assertThat(multi.getMetadata()).isEmpty();
    }

    @Test
    void tripleAndTripleMemoryPreserveDedupAndFormatting() {
        Triple triple = new Triple("A", "likes", "B", Map.of("score", 1.0));
        TripleMemory memory = new TripleMemory();
        memory.extendMemory(List.of("A", "likes", "B"));
        memory.extendMemory(List.of("a", "LIKES", "b"));
        memory.batchExtendMemory(List.of(List.of("C", "knows", "D")));

        assertThat(triple.getMetadata()).containsEntry("score", 1.0);
        assertThat(memory.size()).isEqualTo(2);
        assertThat(memory.getTriplesStr()).isEqualTo("(A likes B)\n(C knows D)");
        assertThat(TripleMemory.tupleToString(List.of("A", "LIKES", "B"))).isEqualTo("a likes b");
    }
}
