/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.reranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.reranker.DashscopeReranker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dashscope Reranker model implementation test cases.
 *
 * <p>Mirrors Python's {@code test_dashscope_reranker.py} in
 * {@code tests/unit_tests/core/retrieval/reranker/}.</p>
 */
@DisplayName("DashscopeReranker")
class TestDashscopeReranker {

    private RerankerConfig dashscopeRerankerConfig;

    @BeforeEach
    void setUp() {
        dashscopeRerankerConfig = new RerankerConfig();
        dashscopeRerankerConfig.setModelName("qwen3-rerank");
        dashscopeRerankerConfig.setApiKey("test-api-key");
        dashscopeRerankerConfig.setApiBase("https://dashscope.aliyuncs.com/api/v1");
    }

    @Nested
    @DisplayName("Endpoint Constant")
    class EndpointConstantTests {
        @Test
        @DisplayName("verify static endpoint constant")
        void test_endpoint_constant() {
            assertThat(DashscopeReranker.END_POINT)
                    .isEqualTo("/services/rerank/text-rerank/text-rerank");
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("strips dashscope endpoint from api_base")
        void test_init_strips_dashscope_endpoint_from_api_base() {
            String base = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
            RerankerConfig config = new RerankerConfig();
            config.setModelName("qwen3-rerank");
            config.setApiKey("test-api-key");
            config.setApiBase(base);
            DashscopeReranker model = new DashscopeReranker(config);
            assertThat(model.getApiUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        }
    }

    @Nested
    @DisplayName("RequestParams")
    class RequestParamsTests {
        @Test
        @DisplayName("shape without string instruct")
        void test_request_params_shape_without_string_instruct() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            Map<String, Object> params = model.requestParams(
                    "q1",
                    List.of("a", "b"),
                    2,
                    true
            );
            assertThat(params.get("model")).isEqualTo("qwen3-rerank");
            assertThat(params.get("input")).isInstanceOf(Map.class);
            Map<String, Object> input = (Map<String, Object>) params.get("input");
            assertThat(input.get("query")).isEqualTo("q1");
            assertThat(input.get("documents")).isEqualTo(List.of("a", "b"));
            assertThat(params.get("parameters")).isInstanceOf(Map.class);
            Map<String, Object> parameters = (Map<String, Object>) params.get("parameters");
            assertThat(parameters.get("return_documents")).isEqualTo(false);
            assertThat(parameters.get("top_n")).isEqualTo(2);
            assertThat(parameters).doesNotContainKey("instruct");
        }

        @Test
        @DisplayName("adds instruct when string")
        void test_request_params_adds_instruct_when_string() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            Map<String, Object> params = model.requestParams(
                    "q1",
                    List.of("x"),
                    1,
                    "Rank by relevance"
            );
            Map<String, Object> parameters = (Map<String, Object>) params.get("parameters");
            assertThat(parameters.get("instruct")).isEqualTo("Rank by relevance");
        }

        @Test
        @DisplayName("top_n defaults to document count")
        void test_request_params_top_n_defaults_to_document_count() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            Map<String, Object> params = model.requestParams(
                    "q",
                    List.of("a", "b", "c"),
                    null,
                    false
            );
            Map<String, Object> parameters = (Map<String, Object>) params.get("parameters");
            assertThat(parameters.get("top_n")).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("AssembleParams")
    class AssembleParamsTests {
        @Test
        @DisplayName("string documents")
        void test_assemble_params_string_documents() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            DashscopeReranker.AssembleResult result = model.assembleParams(
                    "test query",
                    List.of("d1", "d2"),
                    false,
                    Map.of()
            );
            assertThat(result.headers()).containsKey("Content-Type");
            assertThat(result.params().get("input")).isInstanceOf(Map.class);
            Map<String, Object> input = (Map<String, Object>) result.params().get("input");
            assertThat(input.get("query")).isEqualTo("test query");
            assertThat(input.get("documents")).isEqualTo(List.of("d1", "d2"));
            Map<String, Object> parameters = (Map<String, Object>) result.params().get("parameters");
            assertThat(parameters.get("top_n")).isEqualTo(2);
        }

        @Test
        @DisplayName("document objects")
        void test_assemble_params_document_objects() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            Document doc1 = new Document("id1", "First", null);
            Document doc2 = new Document("id2", "Second", null);
            DashscopeReranker.AssembleResult result = model.assembleParams(
                    "q",
                    List.of(doc1, doc2),
                    false,
                    Map.of()
            );
            Map<String, Object> input = (Map<String, Object>) result.params().get("input");
            assertThat(input.get("documents")).isEqualTo(List.of("First", "Second"));
        }

        @Test
        @DisplayName("multimodal query")
        void test_assemble_params_multimodal_query() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            MultimodalDocument queryDoc = new MultimodalDocument().addField("text", "query text");
            DashscopeReranker.AssembleResult result = model.assembleParams(
                    queryDoc,
                    List.of("doc"),
                    false,
                    Map.of()
            );
            Map<String, Object> input = (Map<String, Object>) result.params().get("input");
            assertThat(input.get("query")).isInstanceOf(Map.class);
            Map<String, Object> queryMap = (Map<String, Object>) input.get("query");
            assertThat(queryMap.get("text")).isEqualTo("query text");
        }

        @Test
        @DisplayName("mixed multimodal documents wraps plain text")
        void test_assemble_params_mixed_multimodal_documents_wraps_plain_text() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            MultimodalDocument mm = new MultimodalDocument().addField("text", "mm body");
            DashscopeReranker.AssembleResult result = model.assembleParams(
                    "q",
                    List.of(mm, "plain"),
                    false,
                    Map.of()
            );
            Map<String, Object> input = (Map<String, Object>) result.params().get("input");
            assertThat(input.get("documents")).isEqualTo(List.of(
                    Map.of("text", "mm body"),
                    Map.of("text", "plain")
            ));
        }

        @Test
        @DisplayName("invalid document types throws BaseError")
        void test_assemble_params_invalid_document_types() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            assertThatThrownBy(() -> model.assembleParams(
                    "q",
                    List.of(123, 456),
                    false,
                    Map.of()
            ))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("input to reranker must be either list");
        }

        @Test
        @DisplayName("merges extra kwargs into parameters")
        void test_assemble_params_merges_extra_kwargs_into_parameters() {
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig);
            DashscopeReranker.AssembleResult result = model.assembleParams(
                    "q",
                    List.of("a"),
                    false,
                    Map.of("custom", 1)
            );
            Map<String, Object> parameters = (Map<String, Object>) result.params().get("parameters");
            assertThat(parameters.get("custom")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Rerank")
    class RerankTests {
        @Test
        @DisplayName("posts to dashscope endpoint async")
        void test_rerank_posts_to_dashscope_endpoint() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> mockPayload = Map.of(
                    "output", Map.of(
                            "results", List.of(
                                    Map.of("index", 0, "relevance_score", 0.9),
                                    Map.of("index", 1, "relevance_score", 0.5)
                            )
                    )
            );
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(mapper.writeValueAsString(mockPayload));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig, httpClient);
            Map<String, Double> result = model.rerankScores(
                    "question",
                    List.of("doc1", "doc2"),
                    false,
                    Map.of()
            );
            assertThat(result).isEqualTo(Map.of("doc1", 0.9, "doc2", 0.5));
        }

        @Test
        @DisplayName("sync success")
        void test_rerank_sync_success() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> mockPayload = Map.of(
                    "output", Map.of(
                            "results", List.of(
                                    Map.of("index", 0, "relevance_score", 0.88)
                            )
                    )
            );
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(mapper.writeValueAsString(mockPayload));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);
            DashscopeReranker model = new DashscopeReranker(dashscopeRerankerConfig, httpClient);
            Map<String, Double> result = model.rerankScores(
                    "q",
                    List.of("only"),
                    false,
                    Map.of()
            );
            assertThat(result).isEqualTo(Map.of("only", 0.88));
        }
    }
}