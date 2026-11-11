/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.tracer.TracerHandlerRegistry;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for streaming latency collection in a production-shaped pipeline.
 *
 * <p>A {@code Model} only backfills {@code time_to_first_token_ms}, {@code tpot_ms}
 * and {@code total_latency_ms} when it has a callback framework. Production code
 * never calls {@code Model.setCallbackFramework}, so the component must fall back
 * to the process-global framework owned by {@code Runner}; otherwise every latency
 * attribute is silently dropped even though the span itself looks healthy.</p>
 *
 * <p>Everything here is real production wiring — {@link ReActAgent} with a
 * {@link Model} built from {@code ReActAgentConfig}, a real {@code OpenAIModelClient}
 * against a local SSE stub, and the real {@link OtelRail} + {@link OtelAgentHandler}
 * span chain. Nothing installs a framework on the model, which is precisely the
 * configuration that used to lose all latency data.</p>
 *
 * <p>The stub scripts a role-only delta immediately, then stalls before the first
 * content token, so TTFT must land near the stall while a "first frame of any kind"
 * measurement would land near zero.</p>
 *
 * @since 0.1.18
 */
@Isolated
class ModelStreamLatencyProductionWiringTest {

    /** Prefill stall between the role-only delta and the first content token. */
    private static final long FIRST_TOKEN_DELAY_MS = 150L;

    private static final AttributeKey<Double> TTFT_KEY =
            AttributeKey.doubleKey("openjiuwen.gen_ai.response.time_to_first_token_ms");
    private static final AttributeKey<Double> TPOT_KEY =
            AttributeKey.doubleKey("openjiuwen.gen_ai.response.tpot_ms");
    private static final AttributeKey<Double> TOTAL_LATENCY_KEY =
            AttributeKey.doubleKey("openjiuwen.gen_ai.response.total_latency_ms");
    private static final AttributeKey<Long> INPUT_TOKENS_KEY =
            AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<String> OPERATION_NAME_KEY =
            AttributeKey.stringKey("gen_ai.operation.name");

    private HttpServer stub;
    private String apiBase;

    @BeforeAll
    static void disableCorporateProxy() throws Exception {
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

    @BeforeEach
    void setUp() throws IOException {
        ConftestOtel.clearExporter();
        TracerHandlerRegistry.clear();
        // A framework left behind by another test would mask the fallback being
        // tested, so start from the same state production starts from.
        Model.clearCallbackFramework();

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/v1/chat/completions", this::handleStream);
        stub.start();
        apiBase = "http://127.0.0.1:" + stub.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.stop(0);
        }
        TracerHandlerRegistry.clear();
        Model.clearCallbackFramework();
    }

    @Test
    @DisplayName("production wiring backfills TTFT, TPOT and total latency without an explicit framework")
    void productionWiringBackfillsStreamingLatency() throws Exception {
        SpanData llmSpan = runAgentStream();

        assertThat(llmSpan.getAttributes().get(INPUT_TOKENS_KEY))
                .as("usage must still arrive through the rail path")
                .isNotNull();

        Double ttftMs = llmSpan.getAttributes().get(TTFT_KEY);
        assertThat(ttftMs)
                .as("TTFT must be recorded when only the global framework exists")
                .isNotNull();
        assertThat(ttftMs)
                .as("TTFT must include the prefill stall, so a role-only frame cannot satisfy it")
                .isGreaterThan(FIRST_TOKEN_DELAY_MS * 0.8d);

        assertThat(llmSpan.getAttributes().get(TPOT_KEY))
                .as("TPOT must be recorded when only the global framework exists")
                .isNotNull();
        assertThat(llmSpan.getAttributes().get(TOTAL_LATENCY_KEY))
                .as("total latency must be recorded when only the global framework exists")
                .isNotNull();
    }

    // ==================== pipeline ====================

    private SpanData runAgentStream() {
        OtelAgentHandler handler = new OtelAgentHandler(ConftestOtel.OTEL_TRACER,
                OtelTracerConfig.builder().isRedactionEnabled(false).build());
        TracerHandlerRegistry.registerHandler("otel_model_latency_wiring", handler);

        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("model-latency-wiring")
                .name("model-latency-wiring")
                .description("streaming latency wiring regression")
                .build());
        agent.configure(ReActAgentConfig.builder()
                .modelClientConfig(ModelClientConfig.builder()
                        .clientId("model-latency-wiring-client")
                        .clientProvider(ProviderType.OPEN_AI.getValue())
                        .apiKey("sk-test")
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .maxRetries(0)
                        .build())
                .modelConfigObj(ModelRequestConfig.builder().modelName("gpt-latency-wiring").build())
                .maxIterations(1)
                .streamRetryDelayMs(0)
                .build());
        agent.registerRail(new OtelRail()).toCompletableFuture().join();

        AgentSession session = new AgentSession("model-latency-wiring-session", null, agent.getCard());
        Iterator<Object> it = agent.stream(Map.of("query", "hi"), session, List.of(StreamMode.OUTPUT));
        List<Object> frames = new ArrayList<>();
        while (it.hasNext()) {
            frames.add(it.next());
        }
        assertThat(frames).as("the stream must produce frames").isNotEmpty();

        return llmSpanOf(ConftestOtel.EXPORTER.getFinishedSpanItems());
    }

    private static SpanData llmSpanOf(List<SpanData> spans) {
        SpanData llmSpan = null;
        for (SpanData span : spans) {
            if ("chat".equals(span.getAttributes().get(OPERATION_NAME_KEY))) {
                llmSpan = span;
                break;
            }
        }
        assertThat(llmSpan).as("an LLM span must be produced by the rail chain").isNotNull();
        return llmSpan;
    }

    // ==================== SSE stub ====================

    private void handleStream(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream body = exchange.getResponseBody();

            // 1. Role-only delta: a frame of "some kind", but it carries no output.
            write(body, "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n");

            // 2. The prefill stall a real provider hides behind that delta.
            try {
                Thread.sleep(FIRST_TOKEN_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 3. First token carrying actual output.
            write(body, "data: {\"choices\":[{\"delta\":{\"content\":\"he\"}}]}\n\n");
            write(body, "data: {\"choices\":[{\"delta\":{\"content\":\"llo\"},"
                    + "\"finish_reason\":\"stop\"}]}\n\n");

            // 4. Trailing usage-only frame, as OpenAI reports it with stream_options.
            write(body, "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":11,"
                    + "\"completion_tokens\":2,\"total_tokens\":13}}\n\n");

            // 5. Terminator.
            write(body, "data: [DONE]\n\n");
        }
    }

    private static void write(OutputStream body, String payload) throws IOException {
        body.write(payload.getBytes(StandardCharsets.UTF_8));
        body.flush();
    }
}
