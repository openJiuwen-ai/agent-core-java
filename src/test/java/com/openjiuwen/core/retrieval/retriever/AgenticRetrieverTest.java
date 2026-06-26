/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code TestAgenticRetriever} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_agentic_retriever.py}.</p>
 *
 * <p>Also exercises Python's {@code AgenticRetriever} in
 * {@code openjiuwen/core/retrieval/retriever/agentic_retriever.py}.</p>
 */
class AgenticRetrieverTest {

    @Test
    void initSuccessWithGraphRetriever() throws Exception {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient();

        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 3);

        assertThat(field(retriever, "retriever")).isSameAs(graphRetriever);
        assertThat(field(retriever, "llmClient")).isSameAs(llm);
        assertThat(field(retriever, "maxIter")).isEqualTo(3);
        assertThat(retriever.isGraphRetriever()).isTrue();
    }

    @Test
    void initSuccessWithBaseRetriever() throws Exception {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient();

        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        assertThat(field(retriever, "retriever")).isSameAs(baseRetriever);
        assertThat(field(retriever, "llmClient")).isSameAs(llm);
        assertThat(field(retriever, "maxIter")).isEqualTo(2);
        assertThat(retriever.isGraphRetriever()).isFalse();
    }

    @Test
    void initWithDefaults() throws Exception {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"), new FakeModelClient());

        assertThat(field(retriever, "maxIter")).isEqualTo(2);
    }

    @Test
    void initWithInvalidMaxIterFallsBackToDefault() throws Exception {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingGraphRetriever("hybrid"),
                new FakeModelClient(),
                -1
        );

        assertThat(field(retriever, "maxIter")).isEqualTo(2);
    }

    @Test
    void initWithoutRetrieverFails() {
        assertThatThrownBy(() -> new AgenticRetriever(null, new FakeModelClient()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("retriever is required");
    }

    @Test
    void initWithoutLlmClientFails() {
        assertThatThrownBy(() -> new AgenticRetriever(new RecordingGraphRetriever("hybrid"), null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("llm_client is required");
    }

    @Test
    void defaultModeVector() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("vector", oneResult("Result 1", 0.9d)),
                new FakeModelClient()
        );

        assertThat(retriever.getDefaultMode()).isEqualTo("vector");
    }

    @Test
    void defaultModeBm25() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("bm25", oneResult("Result 1", 0.9d)),
                new FakeModelClient()
        );

        assertThat(retriever.getDefaultMode()).isEqualTo("sparse");
    }

    @Test
    void defaultModeHybrid() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d)),
                new FakeModelClient()
        );

        assertThat(retriever.getDefaultMode()).isEqualTo("hybrid");
    }

    @Test
    void retrieveWithGraphSingleIteration() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "[]",
                "{\"sufficient\": true, \"next_question\": null}"
        );
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).isNotEmpty();
        assertThat(graphRetriever.chunkRetriever.retrieveCalls).isEqualTo(1);
        assertThat(graphRetriever.graphExpansionCalls).isEqualTo(1);
    }

    @Test
    void retrieveWithGraphMultipleIterations() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 1\"}",
                "[]",
                "[]"
        );
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).isNotEmpty();
        assertThat(graphRetriever.chunkRetriever.retrieveCalls).isEqualTo(2);
        assertThat(graphRetriever.chunkRetriever.queries).containsExactly("original query", "rewritten query 1");
    }

    @Test
    void graphExpansionFlagIsNotForwardedDownstream() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient("[]");
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 1);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("graph_expansion", false);
        options.put("trace", true);

        List<RetrievalResult> results = retriever.retrieve("query", 5, null, null, options);

        assertThat(results).isNotEmpty();
        assertThat(graphRetriever.graphExpansionCalls).isZero();
        assertThat(graphRetriever.chunkRetriever.optionSnapshots).hasSize(1);
        assertThat(graphRetriever.chunkRetriever.optionSnapshots.getFirst()).doesNotContainKey("graph_expansion");
        assertThat(graphRetriever.chunkRetriever.optionSnapshots.getFirst()).containsEntry("trace", true);
    }

    @Test
    void graphTripleAndPassageLinkingContinueAfterSingleFailure() {
        RecordingRetriever chunkRetriever = RecordingRetriever.sequence(
                "hybrid",
                List.of(oneResult("chunk result", 0.9d), oneResult("linked passage", 0.8d))
        );
        RecordingRetriever tripleRetriever = RecordingRetriever.searchSequence(
                "hybrid",
                List.of(List.of(searchResult("triple-id", "triple text", 0.7d)))
        );
        tripleRetriever.failFirstSearch = true;
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever(chunkRetriever, tripleRetriever);
        FakeModelClient llm = new FakeModelClient(
                "[[\"subject\",\"predicate\",\"object\"],[\"other\",\"predicate\",\"object\"]]",
                "[[\"linked\",\"to\",\"passage\"]]"
        );
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 1);

        List<RetrievalResult> results = retriever.retrieve("query", 5, null, null, Map.of());

        assertThat(results).isNotEmpty();
        assertThat(tripleRetriever.searchCalls).isEqualTo(2);
        assertThat(graphRetriever.graphExpansionCalls).isEqualTo(1);
        assertThat(graphRetriever.lastLinkedTriples).hasSize(1);
        assertThat(chunkRetriever.queries).containsExactly("query", "linked to passage");
    }

    @Test
    void retrieveGenericSingleIteration() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient("[]", "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.retrieveCalls).isEqualTo(1);
    }

    @Test
    void retrieveGenericMultipleIterations() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 1\"}",
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 2\"}",
                "[]"
        );
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 3);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.retrieveCalls).isEqualTo(3);
        assertThat(baseRetriever.queries).containsExactly("original query", "rewritten query 1", "rewritten query 2");
    }

    @Test
    void retrieveGenericStopsAtDefaultMaxIterations() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 1\"}",
                "[]"
        );
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.retrieveCalls).isEqualTo(2);
    }

    @Test
    void retrieveWithoutValidTopKFails() {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"), new FakeModelClient());

        assertThatThrownBy(() -> retriever.retrieve("test query", 0, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("top_k is invalid");
    }

    @Test
    void retrieveWithNegativeTopKFails() {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"), new FakeModelClient());

        assertThatThrownBy(() -> retriever.retrieve("test query", -1, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("top_k is invalid");
    }

    @Test
    void retrieveWithCustomMode() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient("[]", "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "vector", null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.modes).containsExactly("vector");
    }

    @Test
    void retrieveWithScoreThreshold() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient("[]", "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<RetrievalResult> results = retriever.retrieve("test query", 5, 0.8d, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.scoreThresholds).containsExactly(0.8d);
    }

    @Test
    void retrieveFusionMultipleResults() {
        RecordingRetriever baseRetriever = RecordingRetriever.sequence(
                "hybrid",
                List.of(oneResult("Result 1", 0.9d), oneResult("Result 2", 0.8d))
        );
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query\"}",
                "[]"
        );
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("test query", 5, null, null, null);

        assertThat(results).extracting(RetrievalResult::getText).containsExactlyInAnyOrder("Result 1", "Result 2");
        assertThat(baseRetriever.retrieveCalls).isEqualTo(2);
    }

    @Test
    void fencedJsonResponsesAreParsedLikeRepairedJson() {
        RecordingRetriever baseRetriever = RecordingRetriever.sequence(
                "vector",
                List.of(oneResult("Result 1", 0.9d), oneResult("Result 2", 0.8d))
        );
        FakeModelClient llm = new FakeModelClient(
                "```json\n[[\"a\",\"b\",\"c\"]]\n```",
                "model output:\n```json\n{\"sufficient\": false, \"next_question\": \"follow up\"}\n```",
                "[]"
        );
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original", 5, null, null, null);

        assertThat(results).isNotEmpty();
        assertThat(baseRetriever.queries).containsExactly("original", "follow up");
    }

    @Test
    void invalidReadAndRewriteJsonFallsBackToSingleBaseRetrieval() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("doc", 0.9d));
        FakeModelClient llm = new FakeModelClient("not-json", "{broken");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original", 5, null, null, Map.of());

        assertThat(results).extracting(RetrievalResult::getText).containsExactly("doc");
        assertThat(baseRetriever.queries).containsExactly("original");
        assertThat(llm.prompts).hasSize(2);
    }

    @Test
    void batchRetrieve() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": true, \"next_question\": null}",
                "[]",
                "{\"sufficient\": true, \"next_question\": null}"
        );
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<List<RetrievalResult>> results = retriever.batchRetrieve(List.of("query 1", "query 2"), 5, null, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).hasSize(1);
        assertThat(results.get(1)).hasSize(1);
        assertThat(baseRetriever.queries).containsExactly("query 1", "query 2");
    }

    @Test
    void batchRetrieveEmptyQueriesReturnsEmptyList() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d)),
                new FakeModelClient()
        );

        assertThat(retriever.batchRetrieve(List.of(), 5, null, null)).isEmpty();
    }

    @Test
    void closeDelegatesToBaseRetriever() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9d));
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, new FakeModelClient());

        retriever.close();

        assertThat(baseRetriever.closeCalls).isEqualTo(1);
    }

    @Test
    void closeDelegatesToGraphRetriever() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, new FakeModelClient());

        retriever.close();

        assertThat(graphRetriever.closeCalls).isEqualTo(1);
    }

    @Test
    void closeWorksWithDefaultRetrieverClose() {
        Retriever retrieverWithoutOverride = new Retriever() {
            @Override
            public List<RetrievalResult> retrieve(
                    String query,
                    int topK,
                    Double scoreThreshold,
                    String mode,
                    Map<String, Object> options
            ) {
                return List.of();
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
        };
        AgenticRetriever retriever = new AgenticRetriever(retrieverWithoutOverride, new FakeModelClient());

        retriever.close();

        assertThat(retriever.getDefaultMode()).isEqualTo("hybrid");
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static List<RetrievalResult> oneResult(String text, double score) {
        return List.of(result(text, score));
    }

    private static RetrievalResult result(String text, double score) {
        return new RetrievalResult(text, score, Map.of(), "doc-" + text, "chunk-" + text);
    }

    private static SearchResult searchResult(String id, String text, double score) {
        return new SearchResult(id, text, score, Map.of("doc_id", "doc-" + id, "chunk_id", "chunk-" + id));
    }

    private static final class RecordingGraphRetriever extends GraphRetriever {
        private final RecordingRetriever chunkRetriever;
        private final RecordingRetriever tripleRetriever;
        private int graphExpansionCalls;
        private int closeCalls;
        private List<RetrievalResult> lastLinkedTriples = List.of();

        private RecordingGraphRetriever(String indexType) {
            this(
                    new RecordingRetriever(indexType, oneResult("Result 1", 0.9d)),
                    new RecordingRetriever(indexType, List.of())
            );
            setIndexType(indexType);
        }

        private RecordingGraphRetriever(RecordingRetriever chunkRetriever, RecordingRetriever tripleRetriever) {
            super(chunkRetriever, tripleRetriever);
            this.chunkRetriever = chunkRetriever;
            this.tripleRetriever = tripleRetriever;
            setIndexType(chunkRetriever.getIndexType());
        }

        @Override
        public Retriever getRetrieverForMode(String mode, boolean isChunk) {
            return isChunk ? chunkRetriever : tripleRetriever;
        }

        @Override
        public List<RetrievalResult> graphExpansion(
                String query,
                List<RetrievalResult> chunks,
                List<RetrievalResult> triples,
                Integer topk,
                String mode,
                Map<String, Object> options
        ) {
            graphExpansionCalls++;
            lastLinkedTriples = triples == null ? List.of() : List.copyOf(triples);
            return chunks == null ? List.of() : List.copyOf(chunks);
        }

        @Override
        public void close() {
            closeCalls++;
            super.close();
        }
    }

    private static final class RecordingRetriever implements Retriever {
        private final String indexType;
        private final List<List<RetrievalResult>> resultSequence;
        private final List<List<SearchResult>> searchResultSequence;
        private boolean failFirstSearch;
        private int retrieveCalls;
        private int searchCalls;
        private int closeCalls;
        private final List<String> queries = new ArrayList<>();
        private final List<String> modes = new ArrayList<>();
        private final List<Double> scoreThresholds = new ArrayList<>();
        private final List<Map<String, Object>> optionSnapshots = new ArrayList<>();

        private RecordingRetriever(String indexType, List<RetrievalResult> result) {
            this(indexType, List.of(result), List.of());
        }

        private static RecordingRetriever sequence(String indexType, List<List<RetrievalResult>> resultSequence) {
            return new RecordingRetriever(indexType, resultSequence, List.of());
        }

        private static RecordingRetriever searchSequence(String indexType, List<List<SearchResult>> searchResults) {
            return new RecordingRetriever(indexType, List.of(List.of()), searchResults);
        }

        private RecordingRetriever(
                String indexType,
                List<List<RetrievalResult>> resultSequence,
                List<List<SearchResult>> searchResultSequence
        ) {
            this.indexType = indexType;
            this.resultSequence = resultSequence == null || resultSequence.isEmpty() ? List.of(List.of()) : resultSequence;
            this.searchResultSequence = searchResultSequence == null ? List.of() : searchResultSequence;
        }

        @Override
        public List<RetrievalResult> retrieve(
                String query,
                int topK,
                Double scoreThreshold,
                String mode,
                Map<String, Object> options
        ) {
            retrieveCalls++;
            queries.add(query);
            modes.add(mode);
            scoreThresholds.add(scoreThreshold);
            optionSnapshots.add(options == null ? Map.of() : new LinkedHashMap<>(options));
            int index = Math.min(retrieveCalls - 1, resultSequence.size() - 1);
            return resultSequence.get(index);
        }

        @Override
        public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options) {
            searchCalls++;
            queries.add(query);
            modes.add(mode);
            optionSnapshots.add(options == null ? Map.of() : new LinkedHashMap<>(options));
            if (failFirstSearch && searchCalls == 1) {
                throw new IllegalStateException("search failed");
            }
            int index = Math.min(Math.max(searchCalls - 1 - (failFirstSearch ? 1 : 0), 0), searchResultSequence.size() - 1);
            return searchResultSequence.isEmpty() ? List.of() : searchResultSequence.get(index);
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options) {
            return queries.stream().map(query -> resultSequence.getFirst()).toList();
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

    private static final class FakeModelClient extends BaseModelClient {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        private final List<Object> prompts = new ArrayList<>();

        private FakeModelClient(String... responses) {
            super(null, null);
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected void validateConfig() {
        }

        @Override
        public AssistantMessage invoke(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            prompts.add(messages);
            return new AssistantMessage(responses.isEmpty() ? "[]" : responses.removeFirst());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages,
                String model,
                String size,
                String negativePrompt,
                int n,
                boolean promptExtend,
                boolean watermark,
                int seed,
                Map<String, Object> kwargs
        ) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages,
                String model,
                String voice,
                String languageType,
                Map<String, Object> kwargs
        ) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages,
                String imgUrl,
                String audioUrl,
                String model,
                String size,
                String resolution,
                int duration,
                boolean promptExtend,
                boolean watermark,
                String negativePrompt,
                Integer seed,
                Map<String, Object> kwargs
        ) {
            return null;
        }
    }
}
