/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

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
import com.openjiuwen.core.retrieval.retriever.AgenticRetriever;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agentic retriever test cases.
 *
 * <p>Mirrors Python's {@code test_agentic_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_agentic_retriever.py}.</p>
 */
@DisplayName("AgenticRetriever Tests")
class TestAgenticRetriever {

    @Test
    @DisplayName("test_init_success_with_graph_retriever")
    void testInitSuccessWithGraphRetriever() throws Exception {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient();

        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 3);

        assertThat(field(retriever, "retriever")).isSameAs(graphRetriever);
        assertThat(field(retriever, "llm")).isSameAs(llm);
        assertThat(field(retriever, "maxIter")).isEqualTo(3);
        assertThat(retriever.isGraphRetriever()).isTrue();
    }

    @Test
    @DisplayName("test_init_success_with_base_retriever")
    void testInitSuccessWithBaseRetriever() throws Exception {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient();

        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        assertThat(field(retriever, "retriever")).isSameAs(baseRetriever);
        assertThat(field(retriever, "llm")).isSameAs(llm);
        assertThat(field(retriever, "maxIter")).isEqualTo(2);
        assertThat(retriever.isGraphRetriever()).isFalse();
    }

    @Test
    @DisplayName("test_init_with_defaults")
    void testInitWithDefaults() throws Exception {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"), new FakeModelClient());

        assertThat(field(retriever, "maxIter")).isEqualTo(2);
    }

    @Test
    @DisplayName("test_init_with_invalid_max_iter")
    void testInitWithInvalidMaxIter() throws Exception {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"),
                new FakeModelClient(), -1);

        assertThat(field(retriever, "maxIter")).isEqualTo(2);
    }

    @Test
    @DisplayName("test_init_without_retriever")
    void testInitWithoutRetriever() {
        assertThatThrownBy(() -> new AgenticRetriever(null, new FakeModelClient()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("retriever is required");
    }

    @Test
    @DisplayName("test_init_without_llm_client")
    void testInitWithoutLlmClient() {
        assertThatThrownBy(() -> new AgenticRetriever(new RecordingGraphRetriever("hybrid"), null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("llm_client is required");
    }

    @Test
    @DisplayName("test_default_mode_vector")
    void testDefaultModeVector() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("vector", oneResult("Result 1", 0.9)), new FakeModelClient());

        assertThat(retriever.getDefaultMode()).isEqualTo("vector");
    }

    @Test
    @DisplayName("test_default_mode_bm25")
    void testDefaultModeBm25() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("bm25", oneResult("Result 1", 0.9)), new FakeModelClient());

        assertThat(retriever.getDefaultMode()).isEqualTo("sparse");
    }

    @Test
    @DisplayName("test_default_mode_hybrid")
    void testDefaultModeHybrid() {
        AgenticRetriever retriever = new AgenticRetriever(
                new RecordingRetriever("hybrid", oneResult("Result 1", 0.9)), new FakeModelClient());

        assertThat(retriever.getDefaultMode()).isEqualTo("hybrid");
    }

    @Test
    @DisplayName("test_retrieve_with_graph_single_iteration")
    void testRetrieveWithGraphSingleIteration() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "[]",
                "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).isNotEmpty();
        assertThat(graphRetriever.chunkRetriever.retrieveCalls).isEqualTo(1);
        assertThat(graphRetriever.graphExpansionCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_retrieve_with_graph_multiple_iterations")
    void testRetrieveWithGraphMultipleIterations() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 1\"}",
                "[]",
                "[]");
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).isNotEmpty();
        assertThat(graphRetriever.chunkRetriever.retrieveCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("test_retrieve_generic_single_iteration")
    void testRetrieveGenericSingleIteration() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.retrieveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_retrieve_generic_multiple_iterations")
    void testRetrieveGenericMultipleIterations() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 1\"}",
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 2\"}",
                "[]");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 3);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.retrieveCalls).isEqualTo(3);
    }

    @Test
    @DisplayName("test_retrieve_generic_max_iterations")
    void testRetrieveGenericMaxIterations() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query 1\"}",
                "[]");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<RetrievalResult> results = retriever.retrieve("original query", 5, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.retrieveCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("test_retrieve_without_valid_top_k")
    void testRetrieveWithoutValidTopK() {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"), new FakeModelClient());

        assertThatThrownBy(() -> retriever.retrieve("test query", 0, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("top_k is invalid");
    }

    @Test
    @DisplayName("test_retrieve_with_negative_top_k")
    void testRetrieveWithNegativeTopK() {
        AgenticRetriever retriever = new AgenticRetriever(new RecordingGraphRetriever("hybrid"), new FakeModelClient());

        assertThatThrownBy(() -> retriever.retrieve("test query", -1, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("top_k is invalid");
    }

    @Test
    @DisplayName("test_retrieve_with_custom_mode")
    void testRetrieveWithCustomMode() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "vector", null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.modes).containsExactly("vector");
    }

    @Test
    @DisplayName("test_retrieve_with_score_threshold")
    void testRetrieveWithScoreThreshold() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<RetrievalResult> results = retriever.retrieve("test query", 5, 0.8, null, null);

        assertThat(results).hasSize(1);
        assertThat(baseRetriever.scoreThresholds).containsExactly(0.8);
    }

    @Test
    @DisplayName("test_retrieve_fusion_multiple_results")
    void testRetrieveFusionMultipleResults() {
        RecordingRetriever baseRetriever = RecordingRetriever.sequence("hybrid",
                List.of(oneResult("Result 1", 0.9), oneResult("Result 2", 0.8)));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": false, \"next_question\": \"rewritten query\"}",
                "[]");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm, 2);

        List<RetrievalResult> results = retriever.retrieve("test query", 5, null, null, null);

        assertThat(results).isNotEmpty();
        assertThat(baseRetriever.retrieveCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("test_batch_retrieve")
    void testBatchRetrieve() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        FakeModelClient llm = new FakeModelClient(
                "[]",
                "{\"sufficient\": true, \"next_question\": null}",
                "[]",
                "{\"sufficient\": true, \"next_question\": null}");
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, llm);

        List<List<RetrievalResult>> results = retriever.batchRetrieve(List.of("query 1", "query 2"), 5, null, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).hasSize(1);
        assertThat(results.get(1)).hasSize(1);
    }

    @Test
    @DisplayName("test_close")
    void testClose() {
        RecordingRetriever baseRetriever = new RecordingRetriever("hybrid", oneResult("Result 1", 0.9));
        AgenticRetriever retriever = new AgenticRetriever(baseRetriever, new FakeModelClient());

        retriever.close();

        assertThat(baseRetriever.closeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_close_sync_close")
    void testCloseSyncClose() {
        RecordingGraphRetriever graphRetriever = new RecordingGraphRetriever("hybrid");
        AgenticRetriever retriever = new AgenticRetriever(graphRetriever, new FakeModelClient());

        retriever.close();

        assertThat(graphRetriever.closeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("test_close_no_close_method")
    void testCloseNoCloseMethod() {
        Retriever retrieverWithoutOverride = new Retriever() {
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
        AgenticRetriever retriever = new AgenticRetriever(retrieverWithoutOverride, new FakeModelClient());

        retriever.close();

        assertThat(retriever.getDefaultMode()).isEqualTo("hybrid");
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static List<RetrievalResult> oneResult(String text, double score) {
        return List.of(new RetrievalResult(text, score));
    }

    private static final class RecordingGraphRetriever extends GraphRetriever {
        private final RecordingRetriever chunkRetriever;
        private final RecordingRetriever tripleRetriever;
        private int graphExpansionCalls;
        private int closeCalls;

        private RecordingGraphRetriever(String indexType) {
            this(new RecordingRetriever(indexType, oneResult("Result 1", 0.9)),
                    new RecordingRetriever(indexType, List.<RetrievalResult>of()));
            setIndexType(indexType);
        }

        private RecordingGraphRetriever(RecordingRetriever chunkRetriever, RecordingRetriever tripleRetriever) {
            super(chunkRetriever, tripleRetriever);
            this.chunkRetriever = chunkRetriever;
            this.tripleRetriever = tripleRetriever;
        }

        @Override
        public Retriever getRetrieverForMode(String mode, boolean isChunk) {
            return isChunk ? chunkRetriever : tripleRetriever;
        }

        @Override
        public List<RetrievalResult> graphExpansion(String query, List<RetrievalResult> chunks,
                                                    List<RetrievalResult> triples, Integer topK,
                                                    String mode, Map<String, Object> options) {
            graphExpansionCalls++;
            return chunks;
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
        private int retrieveCalls;
        private int closeCalls;
        private final List<String> modes = new ArrayList<>();
        private final List<Double> scoreThresholds = new ArrayList<>();

        private RecordingRetriever(String indexType, List<RetrievalResult> result) {
            this.indexType = indexType;
            this.resultSequence = List.of(result);
        }

        private static RecordingRetriever sequence(String indexType, List<List<RetrievalResult>> resultSequence) {
            return new RecordingRetriever(indexType, resultSequence, true);
        }

        private RecordingRetriever(String indexType, List<List<RetrievalResult>> resultSequence, boolean ignored) {
            this.indexType = indexType;
            this.resultSequence = resultSequence;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold,
                                              String mode, Map<String, Object> options) {
            retrieveCalls++;
            modes.add(mode);
            scoreThresholds.add(scoreThreshold);
            int index = Math.min(retrieveCalls - 1, resultSequence.size() - 1);
            return resultSequence.get(index);
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK,
                                                         String mode, Map<String, Object> options) {
            return queries.stream().map(query -> resultSequence.get(0)).toList();
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

        private FakeModelClient(String... responses) {
            super(null, null);
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected void validateConfig() {
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            return new AssistantMessage(responses.isEmpty() ? "[]" : responses.removeFirst());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark,
                                                     String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) {
            return null;
        }
    }
}
