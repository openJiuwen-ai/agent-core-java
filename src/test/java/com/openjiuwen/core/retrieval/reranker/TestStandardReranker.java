/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StandardReranker.
 *
 * <p>Mirrors Python's {@code test_standard_reranker.py} in
 * {@code tests/unit_tests/core/retrieval/reranker}.</p>
 */
@ExtendWith(MockitoExtension.class)
class TestStandardReranker {

    private RerankerConfig createRerankerConfig() {
        RerankerConfig config = new RerankerConfig();
        config.setModelName("test-model");
        config.setApiKey("test-api-key");
        config.setApiBase("https://api.example.com/v1");
        return config;
    }

    private RerankerConfig createRerankerConfigNoKey() {
        RerankerConfig config = new RerankerConfig();
        config.setModelName("test-model");
        config.setApiBase("https://api.example.com/v1");
        return config;
    }

    @Nested
    @DisplayName("StandardReranker initialization tests")
    class InitializationTests {

        @Test
        @DisplayName("test_init_with_api_key")
        void testInitWithApiKey() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            assertEquals("test-model", model.modelName);
            assertEquals("test-api-key", model.apiKey);
            assertEquals("https://api.example.com/v1", model.apiUrl);
        }

        @Test
        @DisplayName("test_init_without_api_key")
        void testInitWithoutApiKey() {
            RerankerConfig rerankerConfigNoKey = createRerankerConfigNoKey();
            StandardReranker model = new StandardReranker(rerankerConfigNoKey);
            
            assertEquals("", model.apiKey);
        }

        @Test
        @DisplayName("test_init_with_extra_headers")
        void testInitWithExtraHeaders() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            Map<String, String> extraHeaders = new LinkedHashMap<>();
            extraHeaders.put("X-Custom-Header", "custom-value");
            
            StandardReranker model = new StandardReranker(rerankerConfig, 3, extraHeaders, null);
            
            assertTrue(model.headers.containsKey("X-Custom-Header"));
            assertEquals("custom-value", model.headers.get("X-Custom-Header"));
        }

        @Test
        @DisplayName("test_init_with_custom_params")
        void testInitWithCustomParams() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            
            StandardReranker model = new StandardReranker(
                rerankerConfig,
                5,
                null,
                null
            );
            
            assertEquals(5, model.maxRetries);
        }

        @Test
        @DisplayName("test_init_api_url_with_trailing_slash")
        void testInitApiUrlWithTrailingSlash() {
            RerankerConfig config = new RerankerConfig();
            config.setModelName("test-model");
            config.setApiKey("test-api-key");
            config.setApiBase("https://api.example.com/v1/");
            
            StandardReranker model = new StandardReranker(config);
            
            assertEquals("https://api.example.com/v1", model.apiUrl);
        }

        @Test
        @DisplayName("test_init_api_url_with_endpoint")
        void testInitApiUrlWithEndpoint() {
            RerankerConfig config = new RerankerConfig();
            config.setModelName("test-model");
            config.setApiKey("test-api-key");
            config.setApiBase("https://api.example.com/v1/rerank");
            
            StandardReranker model = new StandardReranker(config);
            
            assertEquals("https://api.example.com/v1", model.apiUrl);
        }
    }

    @Nested
    @DisplayName("Request headers tests")
    class RequestHeadersTests {

        @Test
        @DisplayName("test_request_headers")
        void testRequestHeaders() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            assertTrue(model.headers.containsKey("Content-Type"));
            assertEquals("application/json", model.headers.get("Content-Type"));
            assertTrue(model.headers.containsKey("Authorization"));
            assertEquals("Bearer test-api-key", model.headers.get("Authorization"));
        }

        @Test
        @DisplayName("test_request_headers_no_api_key")
        void testRequestHeadersNoApiKey() {
            RerankerConfig rerankerConfigNoKey = createRerankerConfigNoKey();
            StandardReranker model = new StandardReranker(rerankerConfigNoKey);
            
            assertTrue(model.headers.containsKey("Content-Type"));
            assertFalse(model.headers.containsKey("Authorization"));
        }
    }

    @Nested
    @DisplayName("Request params tests")
    class RequestParamsTests {

        @Test
        @DisplayName("test_request_params_with_instruct_true")
        void testRequestParamsWithInstructTrue() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            Map<String, Object> params = model.buildRequestPayload(
                "test query",
                List.of("doc1", "doc2"),
                Boolean.TRUE,
                Map.of()
            );
            
            assertEquals("test-model", params.get("model"));
            assertEquals(false, params.get("return_documents"));
            assertTrue(params.get("query").toString().contains("<Instruct>"));
            assertTrue(params.get("query").toString().contains("<Query>"));
            assertTrue(params.get("query").toString().contains("test query"));
        }

        @Test
        @DisplayName("test_request_params_with_instruct_false")
        void testRequestParamsWithInstructFalse() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            Map<String, Object> params = model.buildRequestPayload(
                "test query",
                List.of("doc1", "doc2"),
                Boolean.FALSE,
                Map.of()
            );
            
            assertEquals("test-model", params.get("model"));
            assertEquals("test query", params.get("query"));
        }

        @Test
        @DisplayName("test_request_params_with_custom_instruct")
        void testRequestParamsWithCustomInstruct() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            Map<String, Object> params = model.buildRequestPayload(
                "test query",
                List.of("doc1", "doc2"),
                "Custom instruction",
                Map.of()
            );
            
            assertEquals("test-model", params.get("model"));
            assertTrue(params.get("query").toString().contains("Custom instruction"));
            assertTrue(params.get("query").toString().contains("test query"));
        }

        @Test
        @DisplayName("test_request_params_with_extra_body")
        void testRequestParamsWithExtraBody() {
            RerankerConfig config = new RerankerConfig();
            config.setModelName("test-model");
            config.setApiKey("test-api-key");
            config.setApiBase("https://api.example.com/v1");
            Map<String, Object> extraBody = new LinkedHashMap<>();
            extraBody.put("custom_param", "custom_value");
            config.setExtraBody(extraBody);
            
            StandardReranker model = new StandardReranker(config);
            
            Map<String, Object> params = model.buildRequestPayload(
                "test query",
                List.of("doc1"),
                Boolean.TRUE,
                Map.of()
            );
            
            assertEquals("custom_value", params.get("custom_param"));
        }
    }

    @Nested
    @DisplayName("Parse response tests")
    class ParseResponseTests {

        private ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("test_parse_response_with_string_docs")
        void testParseResponseWithStringDocs() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String responseJson = """
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9},
                      {"index": 1, "relevance_score": 0.7}
                    ]
                  }
                }
                """;
            JsonNode response = objectMapper.readTree(responseJson);
            
            List<String> docs = List.of("doc1", "doc2");
            List<Double> scores = model.parseOrderedScores(response, docs.size());
            
            assertEquals(0.9, scores.get(0), 1e-6);
            assertEquals(0.7, scores.get(1), 1e-6);
        }

        @Test
        @DisplayName("test_parse_response_with_document_objects")
        void testParseResponseWithDocumentObjects() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String responseJson = """
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9},
                      {"index": 1, "relevance_score": 0.7}
                    ]
                  }
                }
                """;
            JsonNode response = objectMapper.readTree(responseJson);
            
            List<Document> docs = List.of(
                new Document("doc1", "First document"),
                new Document("doc2", "Second document")
            );
            
            StandardReranker.CandidateBatch batch = StandardReranker.prepareCandidates(docs);
            List<Double> scores = model.parseOrderedScores(response, batch.texts().size());
            
            assertEquals(0.9, scores.get(0), 1e-6);
            assertEquals(0.7, scores.get(1), 1e-6);
        }

        @Test
        @DisplayName("test_parse_response_without_output_key")
        void testParseResponseWithoutOutputKey() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String responseJson = """
                {
                  "results": [
                    {"index": 0, "relevance_score": 0.9},
                    {"index": 1, "relevance_score": 0.7}
                  ]
                }
                """;
            JsonNode response = objectMapper.readTree(responseJson);
            
            List<String> docs = List.of("doc1", "doc2");
            List<Double> scores = model.parseOrderedScores(response, docs.size());
            
            assertEquals(0.9, scores.get(0), 1e-6);
            assertEquals(0.7, scores.get(1), 1e-6);
        }

        @Test
        @DisplayName("test_parse_response_missing_index")
        void testParseResponseMissingIndex() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String responseJson = """
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9},
                      {"relevance_score": 0.7}
                    ]
                  }
                }
                """;
            JsonNode response = objectMapper.readTree(responseJson);
            
            List<String> docs = List.of("doc1", "doc2");
            
            // Missing index will result in -1 from asInt(-1), which will not update the score
            // The test should not throw KeyError but should have a default score
            List<Double> scores = model.parseOrderedScores(response, docs.size());
            assertEquals(0.9, scores.get(0), 1e-6);
            assertEquals(0.0, scores.get(1), 1e-6); // Missing index results in default 0.0
        }
    }

    @Nested
    @DisplayName("Assemble params tests")
    class AssembleParamsTests {

        @Test
        @DisplayName("test_assemble_params_with_string_docs")
        void testAssembleParamsWithStringDocs() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String query = "test query";
            List<String> docs = List.of("doc1", "doc2");
            
            StandardReranker.CandidateBatch batch = StandardReranker.prepareCandidates(docs);
            Map<String, Object> params = model.buildRequestPayload(query, batch.texts(), Boolean.TRUE, Map.of());
            
            assertTrue(model.headers.containsKey("Content-Type"));
            assertEquals("test-model", params.get("model"));
            assertEquals(List.of("doc1", "doc2"), params.get("documents"));
            assertEquals(2, params.get("top_n"));
        }

        @Test
        @DisplayName("test_assemble_params_with_document_objects")
        void testAssembleParamsWithDocumentObjects() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String query = "test query";
            List<Document> docs = List.of(
                new Document("doc1", "First document"),
                new Document("doc2", "Second document")
            );
            
            StandardReranker.CandidateBatch batch = StandardReranker.prepareCandidates(docs);
            Map<String, Object> params = model.buildRequestPayload(query, batch.texts(), Boolean.TRUE, Map.of());
            
            assertEquals(List.of("First document", "Second document"), params.get("documents"));
        }

        @Test
        @DisplayName("test_assemble_params_invalid_input")
        void testAssembleParamsInvalidInput() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String query = "test query";
            List<Integer> docs = List.of(123, 456);
            
            Exception exception = assertThrows(
                BaseError.class,
                () -> StandardReranker.prepareCandidates(docs)
            );
            
            assertTrue(exception.getMessage().contains("input to reranker must be either list"));
        }

        @Test
        @DisplayName("test_assemble_params_with_multimodal_document")
        void testAssembleParamsWithMultimodalDocument() {
            RerankerConfig rerankerConfig = createRerankerConfig();
            StandardReranker model = new StandardReranker(rerankerConfig);
            
            String query = "test query";
            MultimodalDocument doc = new MultimodalDocument();
            doc.addField("text", "Hello world");
            doc.setText("Hello world");
            
            StandardReranker.CandidateBatch batch = StandardReranker.prepareCandidates(List.of(doc));
            
            assertEquals(List.of("Hello world"), batch.texts());
        }
    }

    @Nested
    @DisplayName("Rerank sync tests")
    class RerankSyncTests {

        private ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("test_rerank_sync_success")
        void testRerankSyncSuccess() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("""
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9},
                      {"index": 1, "relevance_score": 0.7}
                    ]
                  }
                }
                """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
            
            StandardReranker model = new StandardReranker(rerankerConfig, 3, null, httpClient);
            
            Map<String, Double> result = model.rerankScores("test query", List.of("doc1", "doc2"));
            
            assertEquals(0.9, result.get("doc1"), 1e-6);
            assertEquals(0.7, result.get("doc2"), 1e-6);
        }

        @Test
        @DisplayName("test_rerank_sync_with_documents")
        void testRerankSyncWithDocuments() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("""
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9}
                    ]
                  }
                }
                """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
            
            StandardReranker model = new StandardReranker(rerankerConfig, 3, null, httpClient);
            
            List<Document> docs = List.of(new Document("doc1", "Test document"));
            Map<String, Double> result = model.rerankScores("test query", docs);
            
            assertEquals(0.9, result.get("doc1"), 1e-6);
        }

        @Test
        @DisplayName("test_rerank_sync_with_instruct_false")
        void testRerankSyncWithInstructFalse() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("""
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9}
                    ]
                  }
                }
                """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
            
            StandardReranker model = new StandardReranker(rerankerConfig, 3, null, httpClient);
            
            Map<String, Double> result = model.rerankScores(
                "test query",
                List.of("doc1"),
                Boolean.FALSE,
                Map.of()
            );
            
            assertEquals(0.9, result.get("doc1"), 1e-6);
        }

        @Test
        @DisplayName("test_rerank_sync_with_custom_instruct")
        void testRerankSyncWithCustomInstruct() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("""
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9}
                    ]
                  }
                }
                """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
            
            StandardReranker model = new StandardReranker(rerankerConfig, 3, null, httpClient);
            
            Map<String, Double> result = model.rerankScores(
                "test query",
                List.of("doc1"),
                "Custom instruction",
                Map.of()
            );
            
            assertEquals(0.9, result.get("doc1"), 1e-6);
        }

        @Test
        @DisplayName("test_rerank_success")
        void testRerankSuccess() throws Exception {
            testRerankSyncSuccess();
        }

        @Test
        @DisplayName("test_rerank_with_documents")
        void testRerankWithDocuments() throws Exception {
            testRerankSyncWithDocuments();
        }

        @Test
        @DisplayName("test_rerank_with_instruct_false")
        void testRerankWithInstructFalse() throws Exception {
            testRerankSyncWithInstructFalse();
        }

        @Test
        @DisplayName("test_rerank_with_custom_instruct")
        void testRerankWithCustomInstruct() throws Exception {
            testRerankSyncWithCustomInstruct();
        }
    }

    @Nested
    @DisplayName("Rerank interface tests")
    class RerankInterfaceTests {

        @Test
        @DisplayName("test_rerank_interface_with_retrieval_results")
        void testRerankInterfaceWithRetrievalResults() throws Exception {
            RerankerConfig rerankerConfig = createRerankerConfig();
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("""
                {
                  "results": [
                    {"index": 0, "relevance_score": 0.1},
                    {"index": 1, "relevance_score": 0.9}
                  ]
                }
                """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
            
            StandardReranker model = new StandardReranker(rerankerConfig, 3, null, httpClient);
            
            RetrievalResult result1 = new RetrievalResult("text1", 0.0);
            result1.setChunkId("doc1");
            RetrievalResult result2 = new RetrievalResult("text2", 0.0);
            result2.setChunkId("doc2");
            List<RetrievalResult> candidates = List.of(result1, result2);
            
            List<RetrievalResult> reranked = model.rerank("test query", candidates, 2);
            
            assertEquals(2, reranked.size());
            assertEquals("doc2", reranked.get(0).getChunkId());
            assertEquals(0.9, reranked.get(0).getScore(), 1e-6);
            assertEquals("doc1", reranked.get(1).getChunkId());
            assertEquals(0.1, reranked.get(1).getScore(), 1e-6);
        }
    }
}
