/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StandardRerankerTest {

    @Test
    void initNormalizesBaseUrl() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1/rerank");
        config.setApiKey("test-key");
        config.setModelName("reranker-model");

        StandardReranker reranker = new StandardReranker(config, 3, null, mock(HttpClient.class));

        assertEquals("https://api.example.com/v1", reranker.apiUrl);
    }

    @Test
    void rerankScoresParsesRemoteResults() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"results":[
                  {"index":0,"relevance_score":0.25},
                  {"index":1,"relevance_score":0.75}
                ]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StandardReranker reranker = new StandardReranker(config(), 3, null, httpClient);
        Map<String, Double> scores = reranker.rerankScores("query", List.of("doc-1", "doc-2"));

        assertEquals(0.25, scores.get("doc-1"), 1e-6);
        assertEquals(0.75, scores.get("doc-2"), 1e-6);
    }

    @Test
    void interfaceRerankSortsCandidatesByReturnedScore() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"results":[
                  {"index":0,"relevance_score":0.1},
                  {"index":1,"relevance_score":0.9}
                ]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StandardReranker reranker = new StandardReranker(config(), 3, null, httpClient);
        List<RetrievalResult> results = reranker.rerank(
                "query",
                List.of(new RetrievalResult("doc one", 0.0), new RetrievalResult("doc two", 0.0)),
                2);

        assertEquals("doc two", results.getFirst().getText());
    }

    @Test
    void lexicalRerankerStillImplementsInterface() {
        assertInstanceOf(Reranker.class, new LexicalReranker());
    }

    private static RerankerConfig config() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1");
        config.setApiKey("test-key");
        config.setModelName("reranker-model");
        return config;
    }
}
