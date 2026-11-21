/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ModelError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Focused parity tests for {@link DashScopeModelClient}.
 *
 * <p>Mirrors Python's {@code DashScopeModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.</p>
 */
@Isolated
class DashScopeModelClientTest {
    @Test
    void generateImageBuildsDashScopeMessageAndExtractsImages() throws Exception {
        CapturingTransport transport = new CapturingTransport(Map.of(
                "status_code", 200,
                "output", Map.of(
                        "choices", List.of(
                                Map.of("message", Map.of("content", List.of(
                                        Map.of("image", "https://example.test/a.png"),
                                        Map.of("text", "ignored")))),
                                Map.of("message", Map.of("content", List.of(
                                        Map.of("image", "https://example.test/b.png"))))))));
        DashScopeModelClient client = client(transport);
        Map<String, Object> textThenImage = new LinkedHashMap<>();
        textThenImage.put("text", "style prompt");
        textThenImage.put("image", "ignored-because-python-prefers-text-key");
        List<Object> content = new ArrayList<>();
        content.add("draw a fox");
        content.add(textThenImage);
        content.add(Map.of("image", "https://example.test/input.png"));
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("extra_flag", true);
        kwargs.put("n", 3);

        ImageGenerationResponse response = client.generateImage(
                List.of(UserMessage.builder().content(content).build()),
                null,
                null,
                "low quality",
                1,
                true,
                false,
                42,
                kwargs);

        assertThat(response.getModel()).isEqualTo("dashscope-default");
        assertThat(response.getImages()).containsExactly(
                "https://example.test/a.png",
                "https://example.test/b.png");
        assertThat(transport.lastApi).isEqualTo(DashScopeModelClient.DashScopeApi.MULTIMODAL_CONVERSATION);
        assertThat(transport.lastParams).containsEntry("api_key", "sk-test")
                .containsEntry("model", "dashscope-default")
                .containsEntry("result_format", "message")
                .containsEntry("stream", false)
                .containsEntry("watermark", false)
                .containsEntry("prompt_extend", true)
                .containsEntry("size", "1664*928")
                .containsEntry("negative_prompt", "low quality")
                .containsEntry("seed", 42)
                .containsEntry("n", 3)
                .containsEntry("extra_flag", true);
        List<?> messages = (List<?>) transport.lastParams.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstMessage = (Map<String, Object>) messages.get(0);
        assertThat(firstMessage).containsEntry("role", "user");
        @SuppressWarnings("unchecked")
        List<Object> dashScopeContent = (List<Object>) firstMessage.get("content");
        assertThat(dashScopeContent).containsExactly(
                Map.<String, Object>of("text", "draw a fox"),
                Map.<String, Object>of("text", "style prompt"),
                Map.<String, Object>of("image", "https://example.test/input.png"));
    }

    @Test
    void generateImageWrapsValidationErrorsLikePythonCatchAll() {
        DashScopeModelClient client = client(new CapturingTransport(Map.of()));

        assertThatThrownBy(() -> client.generateImage(
                List.of(UserMessage.builder().content(List.of(Map.of("audio", "bad"))).build()),
                null,
                null,
                null,
                1,
                true,
                false,
                0,
                null))
                .isInstanceOf(ModelError.class)
                .satisfies(error -> {
                    BaseError baseError = (BaseError) error;
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
                    assertThat(baseError.getMessage())
                            .contains("Unexpected error during DashScope image generation");
                    BaseError cause = assertInstanceOf(BaseError.class, error.getCause());
                    assertThat(cause.getStatus()).isEqualTo(StatusCode.MODEL_INVOKE_PARAM_ERROR);
                    assertThat(cause.getMessage())
                            .contains("Content dict must contain 'text' or 'image' key");
                });
    }

    @Test
    void generateSpeechPassesVoiceAndLanguageThroughWithoutExtraValidation() throws Exception {
        CapturingTransport transport = new CapturingTransport(Map.of(
                "status_code", 200,
                "output", Map.of("audio", Map.of(
                        "url", "https://example.test/speech.wav",
                        "data", "audio-bytes"))));
        DashScopeModelClient client = client(transport);

        AudioGenerationResponse response = client.generateSpeech(
                List.of(new UserMessage("hello")),
                "cosyvoice-v1",
                "CustomVoice",
                "Klingon",
                Map.of("sample_rate", 24000));

        assertThat(response.getModel()).isEqualTo("cosyvoice-v1");
        assertThat(response.getAudioUrl()).isEqualTo("https://example.test/speech.wav");
        assertThat(response.getAudioData()).isEqualTo("audio-bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(response.getFormat()).isEqualTo("wav");
        assertThat(transport.lastParams).containsEntry("voice", "CustomVoice")
                .containsEntry("language_type", "Klingon")
                .containsEntry("sample_rate", 24000);
    }

    @Test
    void generateSpeechMirrorsPythonMessageCountErrorText() {
        DashScopeModelClient client = client(new CapturingTransport(Map.of()));

        assertThatThrownBy(() -> client.generateSpeech(
                List.of(new UserMessage("one"), new UserMessage("two")),
                null,
                null,
                null,
                null))
                .isInstanceOf(ModelError.class)
                .hasMessageContaining("Unexpected error during DashScope speech generation")
                .satisfies(error -> assertThat(error.getCause())
                        .hasMessageContaining("Speech generation requires at least one message, but got 0."));
    }

    @Test
    void generateVideoPreservesPythonSizeResolutionBranching() throws Exception {
        CapturingTransport transport = new CapturingTransport(Map.of(
                "status_code", 200,
                "output", Map.of("video_url", "https://example.test/video.mp4"),
                "usage", Map.of("output_video_duration", 6, "size", "1080P")));
        DashScopeModelClient client = client(transport);

        VideoGenerationResponse imageToVideo = client.generateVideo(
                List.of(new UserMessage("animate this image")),
                "https://example.test/first-frame.png",
                "https://example.test/audio.mp3",
                null,
                "1280*720",
                "1080P",
                6,
                false,
                true,
                "blur",
                7,
                Map.of("style", "cinematic"));

        assertThat(imageToVideo.getModel()).isEqualTo("dashscope-default");
        assertThat(imageToVideo.getVideoUrl()).isEqualTo("https://example.test/video.mp4");
        assertThat(imageToVideo.getDuration()).isEqualTo(6.0D);
        assertThat(imageToVideo.getResolution()).isEqualTo("1080P");
        assertThat(imageToVideo.getFormat()).isEqualTo("mp4");
        assertThat(transport.lastApi).isEqualTo(DashScopeModelClient.DashScopeApi.VIDEO_SYNTHESIS);
        assertThat(transport.lastParams).containsEntry("img_url", "https://example.test/first-frame.png")
                .containsEntry("audio_url", "https://example.test/audio.mp3")
                .containsEntry("resolution", "1080P")
                .containsEntry("duration", 6)
                .containsEntry("prompt_extend", false)
                .containsEntry("watermark", true)
                .containsEntry("negative_prompt", "blur")
                .containsEntry("seed", 7)
                .containsEntry("style", "cinematic");
        assertThat(transport.lastParams).doesNotContainKey("size");

        transport.nextResponse = Map.of(
                "status_code", 200,
                "output", Map.of("video_url", "https://example.test/text-video.mp4"));
        VideoGenerationResponse textToVideo = client.generateVideo(
                List.of(new UserMessage("city at sunrise")),
                null,
                null,
                "wan2.6-t2v",
                null,
                "720P",
                5,
                true,
                false,
                null,
                null,
                null);

        assertThat(textToVideo.getVideoUrl()).isEqualTo("https://example.test/text-video.mp4");
        assertThat(transport.lastParams).containsEntry("resolution", "720P")
                .containsEntry("model", "wan2.6-t2v");
    }

    @Test
    void exposesPythonClientNameLedger() {
        DashScopeModelClient client = client(new CapturingTransport(Map.of()));

        assertThat(DashScopeModelClient.CLIENT_NAME).isEqualTo("DashScope");
        assertThat(client.getClientName()).isEqualTo("DashScope client");
    }

    @Test
    void dashScopeTransportHttpClientAppliesConfiguredHttpVersion() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.DASH_SCOPE)
                .apiKey("sk-test")
                .apiBase("https://dashscope.example.test")
                .verifySsl(false)
                .httpVersion(ModelHttpVersion.HTTP_1_1)
                .connectTimeoutSeconds(5.0D)
                .build();

        HttpClient client = DashScopeModelClient.createDashScopeHttpClient(clientConfig);

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(5));
    }

    @Test
    void generateImage_officialSignalDeath_surfacesAndRecoversNext() throws Exception {
        // Given the sole pooled multimodal client dies with the JDK's official selector-closed
        // signal, When generating an image, Then the failure surfaces without resend (the
        // official signal can also fire after the request bytes reached the wire) and the
        // next call recovers on a fresh pooled client
        try (StubServer server = new StubServer()
            .enqueue(200, imageResponse("https://example.test/first.png"))
            .enqueue(200, imageResponse("https://example.test/recovered.png"))) {
            DashScopeModelClient client = dashScopeClient(server);
            // Clear after construction: the ctor also builds the pooled chat delegate entry.
            ModelHttpClients.clearSharedClientsForTesting();
            client.generateImage(imageMessages(), null, null, null, 1, false, false, 0,
                    new LinkedHashMap<>());
            ModelHttpClients.replaceSolePooledClientForTesting(
                    new FailingHttpClient("selector manager closed"));

            assertThatThrownBy(() -> client.generateImage(imageMessages(), null, null, null, 1,
                    false, false, 0, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);

            ImageGenerationResponse recovered = client.generateImage(imageMessages(), null, null,
                    null, 1, false, false, 0, new LinkedHashMap<>());

            assertThat(recovered.getImages()).containsExactly("https://example.test/recovered.png");
            assertThat(server.requests).hasSize(2);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void generateImage_thresholdEviction_surfacesAndRecoversNext() throws Exception {
        // Given the sole pooled multimodal client keeps failing with an unrecognized wording,
        // When generating images, Then failures surface without resend, the third consecutive
        // failure reaches the threshold (evict + register), and the next call recovers
        try (StubServer server = new StubServer()
            .enqueue(200, imageResponse("https://example.test/first.png"))
            .enqueue(200, imageResponse("https://example.test/recovered.png"))) {
            DashScopeModelClient client = dashScopeClient(server);
            // Clear after construction: the ctor also builds the pooled chat delegate entry.
            ModelHttpClients.clearSharedClientsForTesting();
            client.generateImage(imageMessages(), null, null, null, 1, false, false, 0,
                    new LinkedHashMap<>());
            ModelHttpClients.replaceSolePooledClientForTesting(
                    new FailingHttpClient("connection reset"));

            assertThatThrownBy(() -> client.generateImage(imageMessages(), null, null, null, 1,
                    false, false, 0, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThatThrownBy(() -> client.generateImage(imageMessages(), null, null, null, 1,
                    false, false, 0, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThatThrownBy(() -> client.generateImage(imageMessages(), null, null, null, 1,
                    false, false, 0, new LinkedHashMap<>()))
                .isInstanceOf(BaseError.class);
            assertThat(server.requests).hasSize(1);

            ImageGenerationResponse recovered = client.generateImage(imageMessages(), null, null,
                    null, 1, false, false, 0, new LinkedHashMap<>());

            assertThat(recovered.getImages()).containsExactly("https://example.test/recovered.png");
            assertThat(server.requests).hasSize(2);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    private static List<UserMessage> imageMessages() {
        return List.of(UserMessage.builder().content(List.of("draw a fox")).build());
    }

    private static String imageResponse(String imageUrl) {
        return "{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\""
                + imageUrl + "\"}]}}]}}";
    }

    private static DashScopeModelClient dashScopeClient(StubServer server) {
        return new DashScopeModelClient(
                ModelRequestConfig.builder().modelName("dashscope-default").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.DASH_SCOPE)
                        .apiKey("sk-test")
                        .apiBase(server.baseUrl())
                        .verifySsl(false)
                        .build());
    }

    // A client whose sends always fail with the given wording, so hook tests can poison the
    // pooled entry: "selector manager closed" exercises the official-signal path, any other
    // wording exercises the failure-count fallback.
    private static final class FailingHttpClient extends HttpClient {
        private final String failureMessage;

        private FailingHttpClient(String failureMessage) {
            this.failureMessage = failureMessage;
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
            return new SSLParameters();
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
            throw new IOException(failureMessage);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    // Minimal HTTP stub serving the multimodal generation endpoint; requests are counted so
    // tests can assert zero in-call resend.
    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;
        private final Deque<Map<String, Object>> responses = new ArrayDeque<>();
        private final List<String> requests = new ArrayList<>();

        private StubServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/", this::handle);
            this.server.start();
        }

        private StubServer enqueue(int status, String body) {
            responses.add(Map.of("status", status, "body", body));
            return this;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            requests.add(exchange.getRequestMethod());
            Map<String, Object> response = responses.size() > 1 ? responses.poll() : responses.peek();
            Object statusValue = response.get("status");
            Object bodyValue = response.get("body");
            if (!(statusValue instanceof Integer status) || !(bodyValue instanceof String bodyText)) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            byte[] bytes = bodyText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @Test
    void mediaHttpClientDisablesEndpointVerificationWhenVerifySslIsOff() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.DASH_SCOPE)
                .apiKey("sk-test")
                .apiBase("https://dashscope.example.test")
                .verifySsl(false)
                .build();

        HttpClient client = DashScopeModelClient.createDashScopeHttpClient(clientConfig);

        assertThat(client.sslContext()).isNotNull();
        assertThat(client.sslParameters().getEndpointIdentificationAlgorithm()).isEqualTo("");
    }

    @Test
    void mediaHttpClientAppliesConfiguredSslContextByDefault() throws Exception {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.DASH_SCOPE)
                .apiKey("sk-test")
                .apiBase("https://dashscope.example.test")
                .build();

        HttpClient client = DashScopeModelClient.createDashScopeHttpClient(clientConfig);

        assertThat(client.sslContext()).isNotSameAs(SSLContext.getDefault());
        assertThat(client.sslContext().getDefaultSSLParameters().getProtocols()).contains("TLSv1.3");
    }

    @Test
    void mediaHttpClientUsesGlobalProxyWhenConfigured() throws Exception {
        setUrlEnvReader(name -> "https_proxy".equals(name) ? "http://proxy.example.test:3128" : null);

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.DASH_SCOPE)
                .apiKey("sk-test")
                .apiBase("https://dashscope.example.test")
                .build();

        HttpClient client = DashScopeModelClient.createDashScopeHttpClient(clientConfig);

        assertThat(client.proxy()).isPresent();
        ProxySelector proxySelector = client.proxy().orElseThrow();
        InetSocketAddress address = (InetSocketAddress) proxySelector
                .select(URI.create("https://dashscope.example.test"))
                .get(0)
                .address();
        assertThat(address.getHostString()).isEqualTo("proxy.example.test");
        assertThat(address.getPort()).isEqualTo(3128);
    }

    @Test
    void mediaHttpClientRejectsSslCertOutsideSafeCertDir() throws Exception {
        Path safeCertDir = Files.createTempDirectory("dashscope-safe-cert");
        Path outsideCert = Files.createTempFile("dashscope-outside-cert", ".pem");
        setSslEnvReader(name -> "SAFE_CERT_DIR".equals(name) ? safeCertDir.toString() : null);
        try {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(ProviderType.DASH_SCOPE)
                    .apiKey("sk-test")
                    .apiBase("https://dashscope.example.test")
                    .sslCert(outsideCert.toString())
                    .build();

            Throwable thrown = catchThrowable(
                    () -> DashScopeModelClient.createDashScopeHttpClient(clientConfig));

            BaseError error = assertInstanceOf(BaseError.class, thrown);
            assertThat(error.getStatus()).isEqualTo(StatusCode.COMMON_SSL_CONTEXT_INIT_FAILED);
            // The empty temp file would also fail on size, so pin the directory boundary itself.
            assertThat(error.getMessage()).contains("certificate path is outside the allowed directory");
        } finally {
            Files.deleteIfExists(outsideCert);
            Files.deleteIfExists(safeCertDir);
        }
    }

    @AfterEach
    void restoreEnvReaders() throws Exception {
        Method urlReset = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        urlReset.setAccessible(true);
        urlReset.invoke(null);
        Method sslReset = SslUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        sslReset.setAccessible(true);
        sslReset.invoke(null);
    }

    private static void setUrlEnvReader(Function<String, String> reader) throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    private static void setSslEnvReader(Function<String, String> reader) throws Exception {
        Method method = SslUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    private static DashScopeModelClient client(CapturingTransport transport) {
        return new DashScopeModelClient(
                ModelRequestConfig.builder().modelName("dashscope-default").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.DASH_SCOPE)
                        .apiKey("sk-test")
                        .apiBase("https://dashscope.example.test")
                        .verifySsl(false)
                        .build(),
                transport);
    }

    /**
     * Mirrors Python's patched DashScope SDK call object in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    private static final class CapturingTransport implements DashScopeModelClient.DashScopeTransport {
        private Map<String, Object> nextResponse;
        private DashScopeModelClient.DashScopeApi lastApi;
        private Map<String, Object> lastParams;

        private CapturingTransport(Map<String, Object> nextResponse) {
            this.nextResponse = nextResponse;
        }

        @Override
        public Map<String, Object> call(
                DashScopeModelClient.DashScopeApi api,
                Map<String, Object> apiParams,
                ModelClientConfig clientConfig) {
            this.lastApi = api;
            this.lastParams = new LinkedHashMap<>(apiParams);
            return nextResponse;
        }
    }
}
