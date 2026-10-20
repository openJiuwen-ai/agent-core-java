/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UserConfig;
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
    void failureHidesResponseBodyInLogsButRetainsCause() {
        StubClient client = new StubClient();
        client.status = 400;
        assertThatThrownBy(() -> client.invoke(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of())).isInstanceOf(BaseError.class).hasMessageContaining(PRIVATE_TEXT);
        assertThat(logs.text()).contains("async invoke error", "IOException").doesNotContain(PRIVATE_TEXT);
    }

    @Test
    void streamFailureHidesResponseBody() {
        StubClient client = new StubClient();
        client.status = 400;
        assertThatThrownBy(() -> client.stream(PRIVATE_TEXT, null, null, null, null, null,
                null, null, null, Map.of())).isInstanceOf(BaseError.class);
        assertThat(logs.text()).contains("async stream error").doesNotContain(PRIVATE_TEXT);
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
        private Map<String, Object> payload = Map.of();

        private StubClient() {
            super(ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder().clientProvider("SiliconFlow").apiKey("fake-api-key")
                            .apiBase("http://127.0.0.1:1/v1").build());
        }

        @Override
        protected HttpResult postJson(Map<String, Object> request, Float timeout) {
            payload = request;
            return new HttpResult(status, "{\"choices\":[{\"message\":{\"content\":\"" + PRIVATE_TEXT + "\"}}]}");
        }

        @Override
        protected HttpResponse<InputStream> postStream(Map<String, Object> request, double idleSeconds) {
            payload = request;
            return TieredTimeoutStubServer.fakeStreamingResponse(status, List.of(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"" + PRIVATE_TEXT + "\"}}]}", "data: [DONE]"));
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
