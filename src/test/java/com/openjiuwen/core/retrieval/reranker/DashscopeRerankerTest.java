/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashscopeRerankerTest {

    @Test
    void exposesEndpointAndStripsEndpointFromApiBase() {
        RerankerConfig config = baseConfig()
                .apiBase("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank")
                .build();

        DashscopeReranker reranker = new DashscopeReranker(config);

        assertThat(DashscopeReranker.END_POINT).isEqualTo("/services/rerank/text-rerank/text-rerank");
        assertThat(reranker.getApiUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
    }

    @Test
    void requestParamsUsesDashscopeShapeAndStringOnlyInstruction() {
        DashscopeReranker reranker = new DashscopeReranker(baseConfig().build());

        Map<String, Object> withBoolean = reranker.requestParams("q1", List.of("a", "b"), 2, Boolean.TRUE);
        Map<String, Object> withString = reranker.requestParams("q1", List.of("x"), 1, "Rank by relevance");
        Map<String, Object> defaultTopN = reranker.requestParams("q", List.of("a", "b", "c"), null, Boolean.FALSE);

        assertThat(withBoolean).containsEntry("model", "qwen3-rerank");
        assertThat(withBoolean.get("input")).isEqualTo(Map.of("query", "q1", "documents", List.of("a", "b")));
        assertThat(withBoolean.get("parameters")).isEqualTo(Map.of("return_documents", false, "top_n", 2));
        assertThat(((Map<?, ?>) withString.get("parameters")).get("instruct")).isEqualTo("Rank by relevance");
        assertThat(((Map<?, ?>) defaultTopN.get("parameters")).get("top_n")).isEqualTo(3);
    }

    @Test
    void assembleParamsNormalizesTextAndMultimodalInputs() {
        DashscopeReranker reranker = new DashscopeReranker(baseConfig().build());
        Document first = new Document("id1", "First");
        Document second = new Document("id2", "Second");
        MultimodalDocument query = new MultimodalDocument().addField("text", "query text");
        MultimodalDocument multimodalDocument = new MultimodalDocument().addField("text", "mm body");

        DashscopeReranker.AssembleResult textResult =
                reranker.assembleParams("test query", List.of("d1", "d2"), Boolean.FALSE, Map.of());
        DashscopeReranker.AssembleResult documentResult =
                reranker.assembleParams("q", List.of(first, second), Boolean.FALSE, Map.of());
        DashscopeReranker.AssembleResult queryResult =
                reranker.assembleParams(query, List.of("doc"), Boolean.FALSE, Map.of());
        DashscopeReranker.AssembleResult mixedResult =
                reranker.assembleParams("q", List.of(multimodalDocument, "plain"), Boolean.FALSE, Map.of("custom", 1));

        assertThat(textResult.headers()).containsEntry("Content-Type", "application/json");
        assertThat(textResult.headers()).containsEntry("Authorization", "Bearer test-api-key");
        assertThat(inputOf(textResult).get("documents")).isEqualTo(List.of("d1", "d2"));
        assertThat(inputOf(documentResult).get("documents")).isEqualTo(List.of("First", "Second"));
        assertThat(inputOf(queryResult).get("query")).isEqualTo(Map.of("text", "query text"));
        assertThat(inputOf(mixedResult).get("documents"))
                .isEqualTo(List.of(Map.of("text", "mm body"), Map.of("text", "plain")));
        assertThat(((Map<?, ?>) mixedResult.params().get("parameters")).get("custom")).isEqualTo(1);
    }

    @Test
    void assembleParamsRejectsInvalidDocumentTypes() {
        DashscopeReranker reranker = new DashscopeReranker(baseConfig().build());

        assertThatThrownBy(() -> reranker.assembleParams("q", List.of(123, 456), Boolean.FALSE, Map.of()))
                .hasMessageContaining("input to reranker must be either list[str | Document]");
        assertThatThrownBy(() -> reranker.assembleParams("q", "not-a-list", Boolean.FALSE, Map.of()))
                .hasMessageContaining("input to reranker must be either list[str | Document]");
    }

    @Test
    void rerankSyncPostsToDashscopeEndpointAndMapsScores() {
        FakeHttpClient client = new FakeHttpClient(200, """
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.88},
                      {"index": 1, "relevance_score": 0.44}
                    ]
                  }
                }
                """);
        DashscopeReranker reranker = new DashscopeReranker(baseConfig().build(), 1, 0.1d, null, client);

        Map<String, Double> result = reranker.rerankSync("q", List.of("first", "second"), Boolean.FALSE, Map.of());

        assertThat(result).containsEntry("first", 0.88d);
        assertThat(result).containsEntry("second", 0.44d);
        assertThat(client.lastRequest.uri().toString())
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
    }

    private static Map<String, Object> inputOf(DashscopeReranker.AssembleResult result) {
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) result.params().get("input");
        return input;
    }

    private static RerankerConfig.RerankerConfigBuilder baseConfig() {
        return RerankerConfig.builder()
                .modelName("qwen3-rerank")
                .apiKey("test-api-key")
                .apiBase("https://dashscope.aliyuncs.com/api/v1");
    }

    private static final class FakeHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private HttpRequest lastRequest;

        private FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            this.lastRequest = request;
            return (HttpResponse<T>) new FakeHttpResponse(request, statusCode, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.failedFuture(new IllegalStateException("sendAsync is not used"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record FakeHttpResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (key, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
