/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.foundation.store.base_reranker.Document;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
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
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRerankerTest {

    @Test
    void constructorRequiresExactlyTwoYesNoIds() {
        RerankerConfig missing = baseConfig()
                .yesNoIds(null)
                .build();
        RerankerConfig oneId = baseConfig()
                .yesNoIds(List.of(123))
                .build();

        assertThatThrownBy(() -> new ChatReranker(missing))
                .hasMessageContaining("chat reranker require \"yes_no_ids\"");
        assertThatThrownBy(() -> new ChatReranker(oneId))
                .hasMessageContaining("chat reranker require \"yes_no_ids\"");
    }

    @Test
    void requestParamsBuildsChatPayloadAndMergesExtraBody() {
        RerankerConfig config = baseConfig()
                .extraBody(Map.of("custom_param", "custom-value"))
                .build();
        ChatReranker reranker = new ChatReranker(config);

        Map<String, Object> params = reranker.requestParams("test query", List.of("doc1"), "Custom instruction");

        assertThat(params).containsEntry("model", "test-model");
        assertThat(params).containsEntry("temperature", 0);
        assertThat(params).containsEntry("max_tokens", 1);
        assertThat(params).containsEntry("logprobs", true);
        assertThat(params).containsEntry("top_logprobs", 5);
        assertThat(params).containsEntry("custom_param", "custom-value");
        assertThat(params.get("logit_bias")).isEqualTo(Map.of(123, 5, 456, 5));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) params.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).containsEntry("role", "system");
        assertThat(messages.get(0).get("content"))
                .isEqualTo("Judge whether the Document meets the requirements based on the Query and the Instruct provided. ");
        assertThat(messages.get(1)).containsEntry("role", "user");
        assertThat(messages.get(1).get("content").toString())
                .contains("Custom instruction")
                .contains("test query")
                .contains("<Document>: doc1");
    }

    @Test
    void assembleParamsAcceptsSingleStringOrDocumentOnly() {
        ChatReranker reranker = new ChatReranker(baseConfig().build());
        Document document = Document.builder().id("doc-1").text("Document text").build();

        ChatReranker.AssembleResult stringResult =
                reranker.assembleParams("query", List.of("doc1"), Boolean.TRUE, Map.of());
        ChatReranker.AssembleResult documentResult =
                reranker.assembleParams("query", List.of(document), Boolean.TRUE, Map.of());

        assertThat(stringResult.headers()).containsEntry("Content-Type", "application/json");
        assertThat(stringResult.headers()).containsEntry("Authorization", "Bearer test-api-key");
        assertThat(documentResult.params().get("messages").toString()).contains("Document text");
        assertThatThrownBy(() -> reranker.assembleParams("query", "not-a-list", Boolean.TRUE, Map.of()))
                .hasMessageContaining("input to chat reranker must be a list[str | Document] of size 1");
        assertThatThrownBy(() -> reranker.assembleParams("query", List.of("doc1", "doc2"), Boolean.TRUE, Map.of()))
                .hasMessageContaining("input to chat reranker must be a list[str | Document] of size 1");
        assertThatThrownBy(() -> reranker.assembleParams("query", List.of(), Boolean.TRUE, Map.of()))
                .hasMessageContaining("input to chat reranker must be a list[str | Document] of size 1");
    }

    @Test
    void parseResponseScoresYesNoTokensAndUsesDocumentId() {
        ChatReranker reranker = new ChatReranker(baseConfig().build());
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "logprobs", Map.of(
                                "content", List.of(Map.of(
                                        "top_logprobs", List.of(
                                                Map.of("token", " YES,", "logprob", Math.log(0.8d)),
                                                Map.of("token", "No.", "logprob", Math.log(0.2d))
                                        )
                                ))
                        )
                ))
        );

        Map<String, Double> stringScore = reranker.parseResponse(response, List.of("doc1"));
        Map<String, Double> documentScore = reranker.parseResponse(
                response,
                List.of(Document.builder().id("doc-1").text("Document text").build())
        );

        assertThat(stringScore).containsKey("doc1");
        assertThat(stringScore.get("doc1")).isGreaterThan(0.5d);
        assertThat(documentScore).containsKey("doc-1");
    }

    @Test
    void parseResponseReturnsZeroWhenNoYesOrNoProbabilityExists() {
        ChatReranker reranker = new ChatReranker(baseConfig().build());
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "logprobs", Map.of(
                                "content", List.of(Map.of(
                                        "top_logprobs", List.of(Map.of("token", "maybe", "logprob", -1000d))
                                ))
                        )
                ))
        );

        assertThat(reranker.parseResponse(response, List.of("doc1"))).containsEntry("doc1", 0.0d);
    }

    @Test
    void parseResponseRejectsMissingOrEmptyLogprobs() {
        ChatReranker reranker = new ChatReranker(baseConfig().build());

        assertThatThrownBy(() -> reranker.parseResponse(Map.of("choices", List.of(Map.of())), List.of("doc1")))
                .hasMessageContaining("the service does not support logprobs");
        assertThatThrownBy(() -> reranker.parseResponse(
                Map.of("choices", List.of(Map.of("logprobs", Map.of()))),
                List.of("doc1")
        ))
                .hasMessageContaining("the service does not support logprobs");
    }

    @Test
    void rerankSyncPostsToChatEndpointAndParsesScore() {
        FakeHttpClient client = new FakeHttpClient(200, """
                {
                  "choices": [
                    {
                      "logprobs": {
                        "content": [
                          {
                            "top_logprobs": [
                              {"token": "yes", "logprob": -0.2231435513},
                              {"token": "no", "logprob": -1.6094379124}
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """);
        ChatReranker reranker = new ChatReranker(baseConfig().build(), 1, 0.1d, Map.of("X-Test", "yes"), client);

        Map<String, Double> result = reranker.rerankSync("test query", List.of("doc1"), Boolean.TRUE, Map.of());

        assertThat(result.get("doc1")).isGreaterThan(0.5d);
        assertThat(client.lastRequest.uri().toString()).isEqualTo("https://api.example.com/v1/chat/completions");
        assertThat(client.lastRequest.headers().firstValue("X-Test")).contains("yes");
    }

    @Test
    void testCompatibilityReturnsFalseOnRequestFailure() {
        FakeHttpClient client = new FakeHttpClient(new IOException("service error"));
        ChatReranker reranker = new ChatReranker(baseConfig().build(), 1, 0.1d, null, client);

        assertThat(reranker.testCompatibility()).isFalse();
    }

    private static RerankerConfig.RerankerConfigBuilder baseConfig() {
        return RerankerConfig.builder()
                .modelName("test-model")
                .apiKey("test-api-key")
                .apiBase("https://api.example.com/v1")
                .yesNoIds(List.of(123, 456));
    }

    private static final class FakeHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private final IOException failure;
        private HttpRequest lastRequest;

        private FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
            this.failure = null;
        }

        private FakeHttpClient(IOException failure) {
            this.statusCode = 500;
            this.body = "";
            this.failure = failure;
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
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
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
            if (failure != null) {
                throw failure;
            }
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
