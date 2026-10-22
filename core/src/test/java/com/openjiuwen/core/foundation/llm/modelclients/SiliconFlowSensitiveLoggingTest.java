/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Verifies that sensitive logging hides payloads without changing model results.
 *
 * @since 2026-10-20
 */
@Isolated("Temporarily changes process-wide sensitive logging configuration")
class SiliconFlowSensitiveLoggingTest {
    private static final String PRIVATE_TEXT = "private-payload-marker";

    private boolean isInitiallySensitive;
    private LogCapture logs;

    @BeforeEach
    void captureLogs() {
        isInitiallySensitive = UserConfig.isSensitive();
        UserConfig.setIsSensitive(true);
        logs = new LogCapture();
    }

    @AfterEach
    void restoreLogging() {
        logs.close();
        UserConfig.setIsSensitive(isInitiallySensitive);
    }

    @Test
    void invokeHidesRequestResponseAndParserButPreservesResult() {
        StubClient client = new StubClient();
        AssistantMessage answer = client.invoke(PRIVATE_TEXT, null, null, null, null, null,
                null, new EchoParser(), null, Map.of());

        assertThat(answer.getContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(answer.getParserContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(client.payload.toString()).contains(PRIVATE_TEXT);
        assertThat(logs.text()).contains("LLM request params ready", "Parser parse success")
                .doesNotContain(PRIVATE_TEXT, "fake-api-key");
    }

    @Test
    void streamHidesRequestWithoutRemovingChunks() {
        StubClient client = new StubClient();
        Iterator<AssistantMessageChunk> chunks = client.stream(PRIVATE_TEXT, null, null, null, null, null,
                null, new EchoParser(), null, Map.of());
        assertThat(chunks.next().getContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(chunks.hasNext()).isFalse();
        assertThat(client.payload.toString()).contains(PRIVATE_TEXT);
        assertThat(logs.text()).contains("LLM request params ready").doesNotContain(PRIVATE_TEXT);
    }

    @Test
    void failureHidesResponseBodyInLogsButRetainsClassification() {
        StubClient client = new StubClient();
        client.status = 401;
        assertThatThrownBy(() -> client.invoke(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of())).isInstanceOf(BaseError.class).hasMessageContaining(PRIVATE_TEXT)
                .satisfies(error -> assertThat(ModelCallClassification.of(error)).hasValueSatisfying(
                        classification -> assertThat(classification.category())
                                .isEqualTo(ModelErrorCategory.AUTH_FAILED)));
        assertThat(client.calls).isEqualTo(1);
        assertThat(logs.text()).contains("LLM request params ready").doesNotContain(PRIVATE_TEXT);
    }

    @Test
    void streamFailureHidesResponseBody() {
        StubClient client = new StubClient();
        client.status = 401;
        assertThatThrownBy(() -> client.stream(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of())).isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(ModelCallClassification.of(error)).hasValueSatisfying(
                        classification -> assertThat(classification.category())
                                .isEqualTo(ModelErrorCategory.AUTH_FAILED)));
        assertThat(client.calls).isEqualTo(1);
        assertThat(logs.text()).contains("LLM request params ready").doesNotContain(PRIVATE_TEXT);
    }

    @Test
    void invokeTransportFailureHidesPayloadAndPreservesCause() {
        StubClient client = new StubClient();
        client.failure = new IOException(PRIVATE_TEXT);
        assertThatThrownBy(() -> client.invoke(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of())).isInstanceOf(BaseError.class).hasCause(client.failure);
        assertThat(logs.text()).contains("async invoke error", "IOException").doesNotContain(PRIVATE_TEXT);
    }

    @Test
    void streamTransportFailureHidesPayloadAndPreservesCause() {
        StubClient client = new StubClient();
        client.failure = new IOException(PRIVATE_TEXT);
        assertThatThrownBy(() -> client.stream(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of())).isInstanceOf(BaseError.class).hasCause(client.failure);
        assertThat(logs.text()).contains("async stream error", "IOException").doesNotContain(PRIVATE_TEXT);
    }

    @Test
    void retriedInvokeHidesPayloadButReturnsAnswer() {
        StubClient client = new StubClient(1);
        client.firstStatus = 429;
        AssistantMessage answer = client.invoke(PRIVATE_TEXT, null, null, null, null, null,
                null, new EchoParser(), null, Map.of());
        assertThat(answer.getContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(answer.getParserContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(client.calls).isEqualTo(2);
        assertThat(logs.text()).contains("Model call will retry", "RATE_LIMITED", "API response received")
                .doesNotContain(PRIVATE_TEXT, "fake-api-key");
    }

    @Test
    void retriedStreamHidesPayloadButPreservesChunks() {
        StubClient client = new StubClient(1);
        client.firstStatus = 429;
        Iterator<AssistantMessageChunk> chunks = client.stream(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of());
        assertThat(chunks.next().getContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(chunks.hasNext()).isFalse();
        assertThat(client.calls).isEqualTo(2);
        assertThat(logs.text()).contains("Model call will retry", "RATE_LIMITED")
                .doesNotContain(PRIVATE_TEXT, "fake-api-key");
    }

    @Test
    void explicitNonSensitiveModeKeepsDiagnosticBody() {
        UserConfig.setIsSensitive(false);
        StubClient client = new StubClient();
        client.invoke(PRIVATE_TEXT, null, null, null, null, null, null, new EchoParser(), null, Map.of());
        assertThat(logs.text()).contains(PRIVATE_TEXT).doesNotContain("fake-api-key");
    }

    @Test
    void hidesToolSchemaButSendsItToModel() {
        StubClient client = new StubClient();
        ToolInfo tool = ToolInfo.builder().name("test-tool").description("private-tool-description")
                .parameters(Map.of("type", "object", "properties", Map.of())).build();
        client.invoke("hello", List.of(tool), null, null, null, null, null, null, null, Map.of());
        assertThat(client.payload.get("tools").toString()).contains("private-tool-description");
        assertThat(logs.text()).doesNotContain("private-tool-description", PRIVATE_TEXT);
    }

    @Test
    void parserFailureHidesErrorPayloadAndKeepsRawAnswer() {
        StubClient client = new StubClient();
        AssistantMessage answer = client.invoke(PRIVATE_TEXT, null, null, null, null, null,
                null, new FailingParser(), null, Map.of());
        assertThat(answer.getContent()).isEqualTo(PRIVATE_TEXT);
        assertThat(answer.getParserContent()).isNull();
        assertThat(logs.text()).contains("Parser parse error", "CompletionException").doesNotContain(PRIVATE_TEXT);
    }

    private static final class FailingParser extends BaseOutputParser {
        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(PRIVATE_TEXT));
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> inputs) {
            return List.of().iterator();
        }
    }

    private static final class StubClient extends SiliconFlowModelClient {
        private int status = 200;
        private int firstStatus = 200;
        private int calls;
        private IOException failure;
        private Map<String, Object> payload = Map.of();

        private StubClient() {
            this(0);
        }

        private StubClient(int maxRetries) {
            super(ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder().clientProvider("SiliconFlow").apiKey("fake-api-key")
                            .apiBase("http://127.0.0.1:1/v1").maxRetries(maxRetries).build(),
                    new ModelCallRetrier(delay -> {
                        // Retry attempts are synchronous; elapsed backoff time is not under test.
                    }, () -> 0.0D));
        }

        @Override
        protected HttpResult postJson(Map<String, Object> request, Float timeout) throws IOException {
            payload = request;
            return new HttpResult(nextStatus(),
                    "{\"choices\":[{\"message\":{\"content\":\"" + PRIVATE_TEXT + "\"}}]}");
        }

        @Override
        protected HttpResponse<InputStream> postStream(Map<String, Object> request, double idleSeconds)
                throws IOException {
            payload = request;
            return TieredTimeoutStubServer.fakeStreamingResponse(nextStatus(), List.of(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"" + PRIVATE_TEXT + "\"}}]}", "data: [DONE]"));
        }

        private int nextStatus() throws IOException {
            calls++;
            if (failure != null) {
                throw failure;
            }
            if (calls == 1 && firstStatus != 200) {
                return firstStatus;
            }
            return status;
        }
    }

    private static final class EchoParser extends BaseOutputParser {
        @Override
        public CompletableFuture<Object> parse(Object inputs) {
            return CompletableFuture.completedFuture(inputs);
        }

        @Override
        public Iterator<Object> streamParse(Iterator<?> inputs) {
            return List.of().iterator();
        }
    }

    private static final class LogCapture extends Handler implements AutoCloseable {
        private final List<String> messages = new ArrayList<>();

        private LogCapture() {
            Loggers.LLM.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        private String text() {
            return String.join(System.lineSeparator(), messages);
        }

        @Override
        public void flush() {
            // All records are retained in memory; there is no output stream to flush.
        }

        @Override
        public void close() {
            Loggers.LLM.removeHandler(this);
        }
    }
}
