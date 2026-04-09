/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRerankerTest {

    @Test
    void constructorRequiresYesNoIds() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1");
        config.setApiKey("test-key");
        config.setModelName("chat-reranker");

        assertThrows(BaseError.class, () -> new ChatReranker(config));
    }

    @Test
    void rerankUsesLogprobConfidence() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "choices": [{
                    "logprobs": {
                      "content": [{
                        "top_logprobs": [
                          {"token":"yes","logprob":-0.1},
                          {"token":"no","logprob":-2.0}
                        ]
                      }]
                    }
                  }]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ChatReranker reranker = new ChatReranker(config(), 3, null, httpClient);
        List<RetrievalResult> results = reranker.rerank(
                "query",
                List.of(new RetrievalResult("candidate one", 0.0), new RetrievalResult("candidate two", 0.0)),
                2);

        assertEquals(2, results.size());
        assertEquals("candidate one", results.getFirst().getText());
        assertEquals(results.get(0).getScore(), results.get(1).getScore(), 1e-6);
    }

    private static RerankerConfig config() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1");
        config.setApiKey("test-key");
        config.setModelName("chat-reranker");
        config.setYesNoIds(List.of(1, 2));
        return config;
    }
}
