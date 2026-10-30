/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Stream termination handling of the model clients against a chunked stub
 * endpoint. A terminal finish_reason makes a missing [DONE] frame a normal
 * end, and a stream that stops without any termination marker is reported as
 * a retryable truncation instead of a silent success or an unclassified
 * failure.
 *
 * @since 2026-10-30
 */
@Isolated
class ModelClientStreamTerminationTest {

    @BeforeAll
    static void disableCorporateProxy() throws Exception {
        // Local transport fixtures must not be redirected through a corporate proxy.
        setEnvReader(name -> null);
    }

    @AfterAll
    static void restoreEnvReader() throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void setEnvReader(Function<String, String> reader) throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    @Test
    void openAiStreamTerminatedByFinishReasonWithoutDoneFrameEndsNormally() {
        // Given a chunked stream whose last data frame carries a finish_reason and no [DONE]
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript().events(List.of(terminalContentEvent("ok"))))) {
            OpenAIModelClient client = openAiClient(stub.baseUrl());

            // When the stream is consumed to the end
            List<AssistantMessageChunk> chunks = toList(client.stream(
                    List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build()));

            // Then the completed body is a normal end, not a stream failure
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("ok");
            assertThat(chunks.get(0).getFinishReason()).isEqualTo("stop");
        }
    }

    @Test
    void openAiStreamWithoutTerminationFrameIsRetryableTruncation() {
        // Given a chunked stream that simply stops after a content frame
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .events(List.of(TieredTimeoutStubServer.sseContent("partial"))))) {
            OpenAIModelClient client = openAiClient(stub.baseUrl());

            // When the body ends without any termination marker
            Throwable error = catchThrowable(() -> toList(client.stream(
                    List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build())));

            // Then the truncation is a retryable temporary failure
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
            assertRetryableTruncation(typed);
        }
    }

    @Test
    void openAiStreamCutOffBeforeFirstSegmentIsRetryableTruncation() {
        // Given a peer that answers headers and then cuts the chunked body off
        // before any data frame
        try (AbruptCloseSseServer stub = AbruptCloseSseServer.startWithoutEvents()) {
            OpenAIModelClient client = openAiClient(stub.baseUrl());

            // When the first read hits the abrupt cut
            Throwable error = catchThrowable(() -> toList(client.stream(
                    List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build())));

            // Then the transport cut is a retryable temporary failure instead of an
            // unclassified non-retryable error
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
            assertRetryableTruncation(typed);
        }
    }

    @Test
    void siliconFlowStreamTerminatedByFinishReasonWithoutDoneFrameEndsNormally() {
        // Given a chunked stream whose last data frame carries a finish_reason and no [DONE]
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript().events(List.of(terminalContentEvent("ok"))))) {
            SiliconFlowModelClient client = siliconFlowClient(stub.baseUrl());

            // When the stream is consumed to the end
            List<AssistantMessageChunk> chunks = toList(client.stream(
                    List.of(new UserMessage("hello")), null, null, null, null, null, null,
                    new EchoParser(), 8.0F, Map.of()));

            // Then the completed body is a normal end, not a stream failure
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("ok");
            assertThat(chunks.get(0).getFinishReason()).isEqualTo("stop");
        }
    }

    @Test
    void siliconFlowStreamTerminatedByStandaloneFinishReasonFrameEndsNormally() {
        // Given a content frame followed by a finish_reason-only frame, with no [DONE]
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript().events(List.of(
                        TieredTimeoutStubServer.sseContent("ok"),
                        standaloneFinishReasonEvent())))) {
            SiliconFlowModelClient client = siliconFlowClient(stub.baseUrl());

            // When the stream is consumed to the end
            List<AssistantMessageChunk> chunks = toList(client.stream(
                    List.of(new UserMessage("hello")), null, null, null, null, null, null,
                    new EchoParser(), 8.0F, Map.of()));

            // Then the frame that carries no delta is still recognized as termination
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("ok");
        }
    }

    @Test
    void siliconFlowStreamWithoutTerminationFrameIsRetryableTruncation() {
        // Given a chunked stream that simply stops after a content frame
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(
                new TieredTimeoutStubServer.StubScript()
                        .events(List.of(TieredTimeoutStubServer.sseContent("partial"))))) {
            SiliconFlowModelClient client = siliconFlowClient(stub.baseUrl());

            // When the body ends without any termination marker
            Throwable error = catchThrowable(() -> toList(client.stream(
                    List.of(new UserMessage("hello")), null, null, null, null, null, null,
                    new EchoParser(), 8.0F, Map.of())));

            // Then the truncation is a retryable temporary failure
            BaseError typed = unwrap(error);
            assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
            assertRetryableTruncation(typed);
        }
    }

    @Test
    void openAiStreamTerminatedByFinishReasonThenAbruptCloseEndsNormally() {
        // Given a peer that sends a finish_reason frame and then cuts the body off
        // without the terminating 0-chunk (an abrupt mid-chunked-stream close)
        try (AbruptCloseSseServer stub = AbruptCloseSseServer.start(terminalContentEvent("ok"))) {
            OpenAIModelClient client = openAiClient(stub.baseUrl());

            // When the body is cut off after the terminal frame
            List<AssistantMessageChunk> chunks = toList(client.stream(
                    List.of(new UserMessage("hello")), ModelInvokeOptions.builder().build()));

            // Then the already-terminated stream ends normally instead of failing
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("ok");
            assertThat(chunks.get(0).getFinishReason()).isEqualTo("stop");
        }
    }

    @Test
    void siliconFlowStreamTerminatedByFinishReasonThenAbruptCloseEndsNormally() {
        // Given a peer that sends a finish_reason frame and then cuts the body off
        // without the terminating 0-chunk (an abrupt mid-chunked-stream close)
        try (AbruptCloseSseServer stub = AbruptCloseSseServer.start(terminalContentEvent("ok"))) {
            SiliconFlowModelClient client = siliconFlowClient(stub.baseUrl());

            // When the body is cut off after the terminal frame
            List<AssistantMessageChunk> chunks = toList(client.stream(
                    List.of(new UserMessage("hello")), null, null, null, null, null, null,
                    new EchoParser(), 8.0F, Map.of()));

            // Then the already-terminated stream ends normally instead of failing
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getContent()).isEqualTo("ok");
        }
    }

    @Test
    void siliconFlowStreamEndingWithoutTerminationMarkerIsRetryableTruncation() {
        // Given a length-delimited body (readLine returns null at the end) with no
        // terminal frame of any kind
        StubStreamClient client = new StubStreamClient(List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}"));

        // When the body ends without any termination marker
        Throwable error = catchThrowable(() -> toList(client.stream(
                "hello", null, null, null, null, null, null, new EchoParser(), null, Map.of())));

        // Then the truncation is surfaced as a failure instead of a silent success
        BaseError typed = unwrap(error);
        assertThat(typed.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
        assertThat(String.valueOf(typed.getParams().get("error_msg"))).contains("premature eof");
        assertRetryableTruncation(typed);
    }

    private static String terminalContentEvent(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"},"
                + "\"finish_reason\":\"stop\"}]}\n\n";
    }

    private static String standaloneFinishReasonEvent() {
        return "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
    }

    private static void assertRetryableTruncation(BaseError typed) {
        Map<?, ?> details = (Map<?, ?>) typed.getDetails();
        assertThat(details).isNotNull();
        assertThat(details.get(ModelCallClassification.DETAIL_CATEGORY))
                .isEqualTo(ModelErrorCategory.TEMPORARY);
        assertThat(details.get(ModelCallClassification.DETAIL_RETRYABLE)).isEqualTo(true);
        assertThat(details.get(ModelCallClassification.DETAIL_STREAM)).isEqualTo(true);
    }

    private static OpenAIModelClient openAiClient(String baseUrl) {
        return new OpenAIModelClient(
                ModelRequestConfig.builder().modelName("gpt-test").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.OPEN_AI)
                        .apiKey("sk-test")
                        .apiBase(baseUrl)
                        .verifySsl(false)
                        .maxRetries(0)
                        .build());
    }

    private static SiliconFlowModelClient siliconFlowClient(String baseUrl) {
        return new SiliconFlowModelClient(
                ModelRequestConfig.builder().modelName("test-model").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.SILICON_FLOW)
                        .apiKey("sk-test")
                        .apiBase(baseUrl + "/v1")
                        .verifySsl(false)
                        .maxRetries(0)
                        .build());
    }

    private static List<AssistantMessageChunk> toList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static BaseError unwrap(Throwable error) {
        assertThat(error).isNotNull();
        Throwable cursor = error;
        while (cursor != null && !(cursor instanceof BaseError)) {
            cursor = cursor.getCause();
        }
        assertThat(cursor).isInstanceOf(BaseError.class);
        return (BaseError) cursor;
    }

    /**
     * Raw-socket SSE endpoint that writes one valid chunked event and then closes
     * the connection without the terminating 0-chunk, reproducing an abrupt
     * mid-chunked-stream close. Chunk sizes are hexadecimal per RFC 9112.
     */
    private static final class AbruptCloseSseServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final byte[] eventBytes;

        private AbruptCloseSseServer(String event) throws IOException {
            this.serverSocket = new ServerSocket();
            this.serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            this.eventBytes = event == null ? null : event.getBytes(StandardCharsets.UTF_8);
            Thread acceptor = new Thread(null, this::serveOnce, "abrupt-close-sse-stub");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        static AbruptCloseSseServer start(String event) {
            try {
                return new AbruptCloseSseServer(event);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        static AbruptCloseSseServer startWithoutEvents() {
            try {
                return new AbruptCloseSseServer(null);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private void serveOnce() {
            try (Socket socket = serverSocket.accept()) {
                drainRequest(socket);
                OutputStream out = socket.getOutputStream();
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                out.write("Transfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                if (eventBytes != null) {
                    out.write((Integer.toHexString(eventBytes.length) + "\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(eventBytes);
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                out.flush();
                // Leaving the try block closes the socket with no terminating 0-chunk,
                // ending the chunked body mid-stream like an abrupt peer close.
            } catch (IOException exception) {
                // Client teardown can race the acceptor; the server side is not under test.
            }
        }

        private void drainRequest(Socket socket) throws IOException {
            BufferedReader request = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            int contentLength = 0;
            String line;
            while ((line = request.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).strip());
                }
            }
            if (contentLength > 0) {
                long remaining = contentLength;
                while (remaining > 0) {
                    long skipped = request.skip(remaining);
                    if (skipped <= 0) {
                        break;
                    }
                    remaining -= skipped;
                }
            }
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
            } catch (IOException exception) {
                // The acceptor already served its single connection or was never accepted.
            }
        }
    }

    /**
     * Test double pinning the stream transport to a length-delimited fake body.
     */
    private static final class StubStreamClient extends SiliconFlowModelClient {
        private final List<String> streamLines;

        private StubStreamClient(List<String> streamLines) {
            super(ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider(ProviderType.SILICON_FLOW)
                            .apiKey("sk-test")
                            .apiBase("http://127.0.0.1:1/v1")
                            .verifySsl(false)
                            .maxRetries(0)
                            .build(),
                    new ModelCallRetrier(delay -> {
                        // Retry attempts are synchronous; elapsed backoff time is not under test.
                    }, () -> 0.0D));
            this.streamLines = streamLines;
        }

        @Override
        protected HttpResponse<InputStream> postStream(Map<String, Object> payload, double idleSeconds) {
            return TieredTimeoutStubServer.fakeStreamingResponse(200, streamLines);
        }
    }

    /**
     * Deterministic output parser mirroring the test doubles of the client tests.
     */
    private static final class EchoParser extends BaseOutputParser {
        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.completedFuture(inputs);
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
            return List.of().iterator();
        }
    }
}
