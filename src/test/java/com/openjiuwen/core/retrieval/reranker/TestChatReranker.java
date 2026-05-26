/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChatReranker.
 * Mirrors Python's tests/unit_tests/core/retrieval/reranker/test_chat_reranker.py
 */
class TestChatReranker {

    private static RerankerConfig createConfigWithYesNoIds() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1");
        config.setApiKey("test-api-key");
        config.setModelName("test-model");
        config.setYesNoIds(Arrays.asList(123, 456));
        return config;
    }

    private static RerankerConfig createConfigWithoutYesNoIds() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1");
        config.setApiKey("test-api-key");
        config.setModelName("test-model");
        return config;
    }

    @Nested
    @DisplayName("ChatReranker initialization tests")
    class InitTests {

        @Test
        @DisplayName("test init with valid yes_no_ids")
        void testInitWithValidYesNoIds() {
            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config);
            assertNotNull(model);
        }

        @Test
        @DisplayName("test init without yes_no_ids should raise error")
        void testInitWithoutYesNoIds() {
            RerankerConfig config = createConfigWithoutYesNoIds();
            BaseError error = assertThrows(BaseError.class, () -> new ChatReranker(config));
            assertTrue(error.getMessage().contains("chat reranker require yes_no_ids"));
        }

        @Test
        @DisplayName("test init with invalid yes_no_ids (null)")
        void testInitWithNullYesNoIds() {
            RerankerConfig config = createConfigWithoutYesNoIds();
            assertThrows(BaseError.class, () -> new ChatReranker(config));
        }

        @Test
        @DisplayName("test init with invalid yes_no_ids (wrong size)")
        void testInitWithWrongSizeYesNoIds() {
            RerankerConfig config = new RerankerConfig();
            config.setApiBase("https://api.example.com/v1");
            config.setApiKey("test-api-key");
            config.setModelName("test-model");
            config.setYesNoIds(Arrays.asList(123));
            assertThrows(BaseError.class, () -> new ChatReranker(config));
        }

        @Test
        @DisplayName("test init with invalid yes_no_ids (three elements)")
        void testInitWithThreeYesNoIds() {
            RerankerConfig config = new RerankerConfig();
            config.setApiBase("https://api.example.com/v1");
            config.setApiKey("test-api-key");
            config.setModelName("test-model");
            config.setYesNoIds(Arrays.asList(123, 456, 789));
            assertThrows(BaseError.class, () -> new ChatReranker(config));
        }
    }

    @Nested
    @DisplayName("ChatReranker buildChatPayload tests")
    class BuildChatPayloadTests {

        @Test
        @DisplayName("test buildChatPayload with instruct true")
        void testBuildChatPayloadWithInstructTrue() throws Exception {
            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config);

            Method method = ChatReranker.class.getDeclaredMethod(
                    "buildChatPayload", String.class, String.class, Object.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> payload = (Map<String, Object>) method.invoke(
                    model, "test query", "doc1", true, null);

            assertEquals("test-model", payload.get("model"));
            assertEquals(0, payload.get("temperature"));
            assertEquals(1, payload.get("max_tokens"));
            assertEquals(true, payload.get("logprobs"));
            assertEquals(5, payload.get("top_logprobs"));

            List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).get("role"));
            assertEquals("user", messages.get(1).get("role"));
            assertTrue(messages.get(1).get("content").toString().contains("test query"));
            assertTrue(messages.get(1).get("content").toString().contains("<Document>"));

            Map<String, Integer> logitBias = (Map<String, Integer>) payload.get("logit_bias");
            assertEquals(5, logitBias.get("123"));
            assertEquals(5, logitBias.get("456"));
        }

        @Test
        @DisplayName("test buildChatPayload with instruct false")
        void testBuildChatPayloadWithInstructFalse() throws Exception {
            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config);

            Method method = ChatReranker.class.getDeclaredMethod(
                    "buildChatPayload", String.class, String.class, Object.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> payload = (Map<String, Object>) method.invoke(
                    model, "test query", "doc1", false, null);

            List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
            assertEquals(2, messages.size());
            assertTrue(messages.get(1).get("content").toString().contains("test query"));
        }

        @Test
        @DisplayName("test buildChatPayload with custom instruct")
        void testBuildChatPayloadWithCustomInstruct() throws Exception {
            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config);

            Method method = ChatReranker.class.getDeclaredMethod(
                    "buildChatPayload", String.class, String.class, Object.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> payload = (Map<String, Object>) method.invoke(
                    model, "test query", "doc1", "Custom instruction", null);

            List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
            assertTrue(messages.get(1).get("content").toString().contains("Custom instruction"));
        }

        @Test
        @DisplayName("test buildChatPayload with extra body")
        void testBuildChatPayloadWithExtraBody() throws Exception {
            RerankerConfig config = new RerankerConfig();
            config.setApiBase("https://api.example.com/v1");
            config.setApiKey("test-api-key");
            config.setModelName("test-model");
            config.setYesNoIds(Arrays.asList(123, 456));
            Map<String, Object> extraBody = new LinkedHashMap<>();
            extraBody.put("custom_param", "custom_value");
            config.setExtraBody(extraBody);

            ChatReranker model = new ChatReranker(config);

            Method method = ChatReranker.class.getDeclaredMethod(
                    "buildChatPayload", String.class, String.class, Object.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> payload = (Map<String, Object>) method.invoke(
                    model, "test query", "doc1", true, null);

            assertEquals("custom_value", payload.get("custom_param"));
        }
    }

    @Nested
    @DisplayName("ChatReranker parseChatScore tests")
    class ParseChatScoreTests {

        private ChatReranker createModelWithMockedClient() throws Exception {
            RerankerConfig config = createConfigWithYesNoIds();
            HttpClient httpClient = mock(HttpClient.class);
            return new ChatReranker(config, 3, null, httpClient);
        }

        @Test
        @DisplayName("test parseChatScore with yes token")
        void testParseChatScoreWithYesToken() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "yes", "logprob": -0.2231435513},
                              {"token": "no", "logprob": -1.609437912}
                            ]
                          }]
                        }
                      }]
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            double score = (double) method.invoke(model, response);
            assertTrue(score > 0.5);
        }

        @Test
        @DisplayName("test parseChatScore with no token")
        void testParseChatScoreWithNoToken() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "no", "logprob": -0.2231435513},
                              {"token": "yes", "logprob": -1.609437912}
                            ]
                          }]
                        }
                      }]
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            double score = (double) method.invoke(model, response);
            assertTrue(score < 0.5);
        }

        @Test
        @DisplayName("test parseChatScore with case-insensitive tokens")
        void testParseChatScoreWithCaseInsensitiveTokens() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "YES", "logprob": -0.2231435513},
                              {"token": "No", "logprob": -1.609437912}
                            ]
                          }]
                        }
                      }]
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            double score = (double) method.invoke(model, response);
            assertTrue(score > 0.5);
        }

        @Test
        @DisplayName("test parseChatScore with token prefix")
        void testParseChatScoreWithTokenPrefix() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "yes,", "logprob": -0.2231435513},
                              {"token": "no.", "logprob": -1.609437912}
                            ]
                          }]
                        }
                      }]
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            double score = (double) method.invoke(model, response);
            assertTrue(score > 0.5);
        }

        @Test
        @DisplayName("test parseChatScore without logprobs should raise error")
        void testParseChatScoreWithoutLogprobs() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {"choices": [{}]}
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            assertThrows(Exception.class, () -> {
                try {
                    method.invoke(model, response);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        }

        @Test
        @DisplayName("test parseChatScore with empty logprobs should raise error")
        void testParseChatScoreWithEmptyLogprobs() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {"choices": [{"logprobs": {}}]}
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            assertThrows(Exception.class, () -> {
                try {
                    method.invoke(model, response);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        }

        @Test
        @DisplayName("test parseChatScore with zero total probability")
        void testParseChatScoreWithZeroTotalProb() throws Exception {
            ChatReranker model = createModelWithMockedClient();

            Method method = ChatReranker.class.getDeclaredMethod("parseChatScore", com.fasterxml.jackson.databind.JsonNode.class);
            method.setAccessible(true);

            String jsonResponse = """
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "maybe", "logprob": -1000}
                            ]
                          }]
                        }
                      }]
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode response = mapper.readTree(jsonResponse);

            double score = (double) method.invoke(model, response);
            assertEquals(0.0, score, 1e-6);
        }
    }

    @Nested
    @DisplayName("ChatReranker rerank tests")
    class RerankTests {

        @Test
        @DisplayName("test rerank success with string documents")
        void testRerankSuccessWithStringDocuments() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("""
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "yes", "logprob": -0.2231435513},
                              {"token": "no", "logprob": -1.609437912}
                            ]
                          }]
                        }
                      }]
                    }
                    """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config, 3, null, httpClient);

            Map<String, Double> scores = model.rerankScores("test query", Arrays.asList("doc1"));
            assertTrue(scores.containsKey("doc1"));
            assertTrue(scores.get("doc1") > 0.5);
        }

        @Test
        @DisplayName("test rerank success with Document objects")
        void testRerankSuccessWithDocumentObjects() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("""
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "yes", "logprob": -0.2231435513}
                            ]
                          }]
                        }
                      }]
                    }
                    """);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config, 3, null, httpClient);

            Document doc = new Document("doc1", "Test document");
            List<RetrievalResult> results = model.rerank(
                    "test query",
                    Arrays.asList(new RetrievalResult(doc.getText(), 0.0)),
                    1);

            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("test rerank with multiple documents")
        void testRerankWithMultipleDocuments() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response1 = mock(HttpResponse.class);
            when(response1.statusCode()).thenReturn(200);
            when(response1.body()).thenReturn("""
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "yes", "logprob": -0.1}
                            ]
                          }]
                        }
                      }]
                    }
                    """);

            HttpResponse<String> response2 = mock(HttpResponse.class);
            when(response2.statusCode()).thenReturn(200);
            when(response2.body()).thenReturn("""
                    {
                      "choices": [{
                        "logprobs": {
                          "content": [{
                            "top_logprobs": [
                              {"token": "no", "logprob": -0.1}
                            ]
                          }]
                        }
                      }]
                    }
                    """);

            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response1)
                    .thenReturn(response2);

            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config, 3, null, httpClient);

            List<RetrievalResult> results = model.rerank(
                    "test query",
                    Arrays.asList(
                            new RetrievalResult("doc1", 0.0),
                            new RetrievalResult("doc2", 0.0)),
                    2);

            assertEquals(2, results.size());
            assertEquals("doc1", results.get(0).getText());
        }

        @Test
        @DisplayName("test rerank with service failure")
        void testRerankWithServiceFailure() throws Exception {
            HttpClient httpClient = mock(HttpClient.class);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new RuntimeException("Service error"));

            RerankerConfig config = createConfigWithYesNoIds();
            ChatReranker model = new ChatReranker(config, 1, null, httpClient);

            assertThrows(Exception.class, () -> model.rerankScores("test query", Arrays.asList("doc1")));
        }
    }

    }