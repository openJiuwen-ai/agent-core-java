/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestDashscopeReranker} in
 * {@code tests/unit_tests/core/retrieval/reranker/test_dashscope_reranker.py}.
 */
class DashscopeRerankerPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void endpointConstantMatchesPythonValue() {
        assertThat(DashscopeReranker.END_POINT).isEqualTo("/services/rerank/text-rerank/text-rerank");
    }

    @Test
    void initStripsDashscopeEndpointFromApiBase() {
        RerankerConfig config = baseConfig()
                .apiBase("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank")
                .build();

        DashscopeReranker model = new DashscopeReranker(config);

        assertThat(model.getApiUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
    }

    @Test
    void requestParamsShapeWithoutStringInstruct() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());

        Map<String, Object> params = model.requestParams("q1", List.of("a", "b"), 2, Boolean.TRUE);

        assertThat(params).containsEntry("model", "qwen3-rerank");
        assertThat(params.get("input")).isEqualTo(Map.of("query", "q1", "documents", List.of("a", "b")));
        assertThat(parametersOf(params)).containsEntry("return_documents", false).containsEntry("top_n", 2);
        assertThat(parametersOf(params)).doesNotContainKey("instruct");
    }

    @Test
    void requestParamsAddsInstructWhenString() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());

        Map<String, Object> params = model.requestParams("q1", List.of("x"), 1, "Rank by relevance");

        assertThat(parametersOf(params)).containsEntry("instruct", "Rank by relevance");
    }

    @Test
    void requestParamsTopNDefaultsToDocumentCount() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());

        Map<String, Object> params = model.requestParams("q", List.of("a", "b", "c"), null, Boolean.FALSE);

        assertThat(parametersOf(params)).containsEntry("top_n", 3);
    }

    @Test
    void assembleParamsStringDocuments() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());

        DashscopeReranker.AssembleResult result =
                model.assembleParams("test query", List.of("d1", "d2"), Boolean.FALSE, Map.of());

        assertThat(result.headers()).containsKey("Content-Type");
        assertThat(inputOf(result.params())).containsEntry("query", "test query");
        assertThat(inputOf(result.params())).containsEntry("documents", List.of("d1", "d2"));
        assertThat(parametersOf(result.params())).containsEntry("top_n", 2);
    }

    @Test
    void assembleParamsDocumentObjects() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());
        Document first = new Document("id1", "First");
        Document second = new Document("id2", "Second");

        DashscopeReranker.AssembleResult result =
                model.assembleParams("q", List.of(first, second), Boolean.FALSE, Map.of());

        assertThat(inputOf(result.params())).containsEntry("documents", List.of("First", "Second"));
    }

    @Test
    void assembleParamsMultimodalQuery() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());
        MultimodalDocument query = new MultimodalDocument().addField("text", "query text");

        DashscopeReranker.AssembleResult result =
                model.assembleParams(query, List.of("doc"), Boolean.FALSE, Map.of());

        assertThat(inputOf(result.params())).containsEntry("query", Map.of("text", "query text"));
    }

    @Test
    void assembleParamsMixedMultimodalDocumentsWrapsPlainText() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());
        MultimodalDocument multimodal = new MultimodalDocument().addField("text", "mm body");

        DashscopeReranker.AssembleResult result =
                model.assembleParams("q", List.of(multimodal, "plain"), Boolean.FALSE, Map.of());

        assertThat(inputOf(result.params()))
                .containsEntry("documents", List.of(Map.of("text", "mm body"), Map.of("text", "plain")));
    }

    @Test
    void assembleParamsInvalidDocumentTypes() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());

        assertThatThrownBy(() -> model.assembleParams("q", List.of(123, 456), Boolean.FALSE, Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("input to reranker must be either list");
    }

    @Test
    void assembleParamsMergesExtraKwargsIntoParameters() {
        DashscopeReranker model = new DashscopeReranker(baseConfig().build());

        DashscopeReranker.AssembleResult result =
                model.assembleParams("q", List.of("a"), Boolean.FALSE, Map.of("custom", 1));

        assertThat(parametersOf(result.params())).containsEntry("custom", 1);
    }

    @Test
    void rerankPostsToDashscopeEndpoint() {
        FakeHttpClient client = new FakeHttpClient("""
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.9},
                      {"index": 1, "relevance_score": 0.5}
                    ]
                  }
                }
                """);
        DashscopeReranker model = new DashscopeReranker(baseConfig().build(), 1, 0.1d, null, client);

        Map<String, Double> result = model.rerank("question", List.of("doc1", "doc2"), Boolean.FALSE, Map.of()).join();

        assertThat(result).containsEntry("doc1", 0.9d).containsEntry("doc2", 0.5d);
        assertThat(client.lastRequest.uri().toString())
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
        assertThat(inputOf(client.lastJsonBody)).containsEntry("query", "question");
        assertThat(inputOf(client.lastJsonBody)).containsEntry("documents", List.of("doc1", "doc2"));
        assertThat(parametersOf(client.lastJsonBody)).containsEntry("return_documents", false);
    }

    @Test
    void rerankSyncSuccess() {
        FakeHttpClient client = new FakeHttpClient("""
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.88}
                    ]
                  }
                }
                """);
        DashscopeReranker model = new DashscopeReranker(baseConfig().build(), 1, 0.1d, null, client);

        Map<String, Double> result = model.rerankSync("q", List.of("only"), Boolean.FALSE, Map.of());

        assertThat(result).containsEntry("only", 0.88d);
        assertThat(client.lastRequest.uri().toString())
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parametersOf(Map<String, Object> params) {
        return (Map<String, Object>) params.get("parameters");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inputOf(Map<String, Object> params) {
        return (Map<String, Object>) params.get("input");
    }

    private static RerankerConfig.RerankerConfigBuilder baseConfig() {
        return RerankerConfig.builder()
                .modelName("qwen3-rerank")
                .apiKey("test-api-key")
                .apiBase("https://dashscope.aliyuncs.com/api/v1");
    }

    private static String readBody(HttpRequest request) throws IOException {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher()
                .orElseThrow(() -> new IOException("request body is missing"));
        CompletableFuture<String> body = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                body.complete(bytes.toString(StandardCharsets.UTF_8));
            }
        });
        try {
            return body.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading request body", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("failed to read request body", exception);
        }
    }

    /**
     * Mirrors Python's patched {@code model.client.post} and {@code sync_client.post} in
     * {@code tests/unit_tests/core/retrieval/reranker/test_dashscope_reranker.py}.
     */
    private static final class FakeHttpClient extends HttpClient {
        private final String body;
        private HttpRequest lastRequest;
        private Map<String, Object> lastJsonBody;

        private FakeHttpClient(String body) {
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            this.lastRequest = request;
            this.lastJsonBody = OBJECT_MAPPER.readValue(readBody(request), MAP_TYPE);
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new FakeHttpResponse(request, body);
            return response;
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

    /**
     * Mirrors Python's mocked successful Dashscope response object in
     * {@code tests/unit_tests/core/retrieval/reranker/test_dashscope_reranker.py}.
     */
    private record FakeHttpResponse(HttpRequest request, String body) implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (key, value) -> true);
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
