/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.tracer.TracerHandlerRegistry;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.schema.config.ModelConfigEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End-to-end verification for BUG-0009: an LLM span must report the model that was
 * actually invoked, not the static instance configuration.
 *
 * <p>Uses real components only: {@code model-configs} entries are registered into the
 * framework model pool through {@link HarnessFactory}/{@link DeepAgent} (the same
 * reconciliation path production uses), the pool models issue real HTTP requests to a
 * local OpenAI-compatible server, and spans are exported by a real OTel SDK
 * {@code InMemorySpanExporter} through {@link OtelRail} + {@link OtelAgentHandler}.</p>
 *
 * @since 0.1.16
 */
@DisplayName("OTel llm span reports the actually invoked model")
class OtelLlmSpanModelIntegrationTest {
    private static final String HANDLER_NAME = "otel_agent_model_it";
    private static final String PREFIX = "it-bug0009-" + UUID.randomUUID() + "-";
    private static final Pattern MODEL_FIELD = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"");
    private static final String DEFAULT_MODEL_NAME = "model-a";
    private static final String DYNAMIC_MODEL_NAME = "model-b";

    private final List<String> wireModelNames = Collections.synchronizedList(new ArrayList<>());
    private final List<String> registeredModelIds = new ArrayList<>();
    private String poolA;
    private String poolB;
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        wireModelNames.clear();
        ConftestOtel.clearExporter();
        TracerHandlerRegistry.registerHandler(HANDLER_NAME,
                new OtelAgentHandler(ConftestOtel.OTEL_TRACER,
                        OtelTracerConfig.builder().isRedactionEnabled(false).build()));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handleChatCompletion);
        server.start();
        poolA = PREFIX + "pool-a";
        poolB = PREFIX + "pool-b";
        registeredModelIds.add(poolA);
        registeredModelIds.add(poolB);
        registerModelConfigs();
    }

    @AfterEach
    void tearDown() {
        TracerHandlerRegistry.unregisterHandler(HANDLER_NAME);
        if (server != null) {
            server.stop(0);
        }
        for (String modelId : registeredModelIds) {
            if (Runner.resourceMgr().listModelIds().contains(modelId)) {
                Runner.resourceMgr().removeModelForce(modelId, true);
            }
        }
        registeredModelIds.clear();
        Runner.resourceMgr().setDefaultModelId(null);
    }

    @Test
    @DisplayName("default model is reported when the request carries no model_id")
    void defaultModelIsReported() throws Exception {
        ReActAgent agent = newAgent();
        agent.invoke(Map.of("query", "hi"), new AgentSession("it-bug0009-default", null, agent.getCard()));

        assertReported(DEFAULT_MODEL_NAME, null);
        assertThat(wireModelNames).containsExactly(DEFAULT_MODEL_NAME);
    }

    @Test
    @DisplayName("request-scoped dynamic model is reported with its pool key")
    void requestScopedDynamicModelIsReported() throws Exception {
        ReActAgent agent = newAgent();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, callbackCtx -> {
            callbackCtx.setDynamicModelId(poolB);
            return CompletableFuture.completedFuture(null);
        }, 100).toCompletableFuture().join();

        agent.invoke(Map.of("query", "hi"), new AgentSession("it-bug0009-request-scoped", null, agent.getCard()));

        assertReported(DYNAMIC_MODEL_NAME, poolB);
        assertThat(wireModelNames).containsExactly(DYNAMIC_MODEL_NAME);
    }

    @Test
    @DisplayName("dynamic model selected during beforeModelCall is reported with its pool key")
    void lateDynamicModelSelectionIsReported() throws Exception {
        ReActAgent agent = newAgent();
        // Mirrors harness TaskPlanningRail, which switches the model in beforeModelCall:
        // priority 10 runs before OtelRail (priority 0, executes last).
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, callbackCtx -> {
            callbackCtx.setDynamicModelId(poolB);
            return CompletableFuture.completedFuture(null);
        }, 10).toCompletableFuture().join();

        agent.invoke(Map.of("query", "hi"), new AgentSession("it-bug0009-late-selection", null, agent.getCard()));

        assertReported(DYNAMIC_MODEL_NAME, poolB);
        assertThat(wireModelNames).containsExactly(DYNAMIC_MODEL_NAME);
    }

    @Test
    @DisplayName("streaming path reports the dynamic model and pool key")
    void streamingDynamicModelIsReported() throws Exception {
        ReActAgent agent = newAgent();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, callbackCtx -> {
            callbackCtx.setDynamicModelId(poolB);
            return CompletableFuture.completedFuture(null);
        }, 100).toCompletableFuture().join();
        AgentSession session = new AgentSession("it-bug0009-stream", null, agent.getCard());

        agent.stream(Map.of("query", "hi"), session, List.of(StreamMode.OUTPUT)).forEachRemaining(ignored -> {
        });

        assertReported(DYNAMIC_MODEL_NAME, poolB);
        assertThat(wireModelNames).containsExactly(DYNAMIC_MODEL_NAME);
    }

    /**
     * Register two {@code model-configs} entries into the framework pool the same way
     * {@code DeepAgent.reconcileModelConfigs()} does in production.
     */
    private void registerModelConfigs() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath("./target/it-bug0009-workspace")
                .modelConfigs(List.of(
                        newEntry(poolA, true, DEFAULT_MODEL_NAME),
                        newEntry(poolB, false, DYNAMIC_MODEL_NAME)))
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        agent.ensureInitialized();
    }

    private ModelConfigEntry newEntry(String modelId, boolean isDefault, String modelName) {
        return ModelConfigEntry.builder()
                .modelId(modelId)
                .isDefault(isDefault)
                .modelConfig(ModelRequestConfig.builder().modelName(modelName).build())
                .modelClient(ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("sk-test")
                        .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                        .timeout(5)
                        .build())
                .build();
    }

    private ReActAgent newAgent() {
        AgentCard card = AgentCard.builder()
                .id("it-bug0009-agent-" + UUID.randomUUID())
                .name("it-bug0009-agent")
                .description("OTel llm span model reporting integration test agent")
                .build();
        ReActAgent agent = new ReActAgent(card);
        agent.configure(ReActAgentConfig.builder()
                .modelName(DEFAULT_MODEL_NAME)
                .modelClientConfig(ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("sk-test")
                        .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                        .timeout(5)
                        .build())
                .modelConfigObj(ModelRequestConfig.builder().modelName(DEFAULT_MODEL_NAME).build())
                .build());
        agent.registerRail(new OtelRail());
        return agent;
    }

    private void assertReported(String expectedModelName, String expectedModelId) {
        List<SpanData> llmSpans = ConftestOtel.EXPORTER.getFinishedSpanItems().stream()
                .filter(span -> span.getName().startsWith("llm."))
                .toList();
        assertThat(llmSpans).as("exactly one llm span").hasSize(1);
        SpanData span = llmSpans.get(0);
        assertThat(span.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey(SemConv.GEN_AI_REQUEST_MODEL)))
                .isEqualTo(expectedModelName);
        assertThat(span.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey(SemConv.GEN_AI_RESPONSE_MODEL)))
                .isEqualTo(expectedModelName);
        assertThat(span.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey(SemConv.OJ_MODEL_ID)))
                .isEqualTo(expectedModelId);
    }

    /**
     * OpenAI-compatible endpoint that echoes the requested model name back in both the
     * response body and the SSE chunks, recording what the wire saw.
     */
    private void handleChatCompletion(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = MODEL_FIELD.matcher(requestBody);
        String model = matcher.find() ? matcher.group(1) : "";
        wireModelNames.add(model);
        if (requestBody.contains("\"stream\":true")) {
            writeSse(exchange, String.join("",
                    "data: {\"id\":\"it\",\"model\":\"" + model + "\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{\"content\":\"ok\"},\"finish_reason\":null}]}\n\n",
                    "data: {\"id\":\"it\",\"model\":\"" + model + "\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{},\"finish_reason\":\"stop\"}],"
                            + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":1,\"total_tokens\":4}}\n\n",
                    "data: [DONE]\n\n"));
            return;
        }
        writeJson(exchange, "{\"id\":\"it\",\"model\":\"" + model + "\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":1,\"total_tokens\":4}}");
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        writeResponse(exchange, body, "application/json");
    }

    private static void writeSse(HttpExchange exchange, String body) throws IOException {
        writeResponse(exchange, body, "text/event-stream");
    }

    private static void writeResponse(HttpExchange exchange, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
