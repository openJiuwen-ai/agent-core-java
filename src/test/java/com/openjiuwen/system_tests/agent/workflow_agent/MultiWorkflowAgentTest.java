/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.workflow_agent;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/system_tests/agent/workflow_agent/test_multi_workflow_agent.py}.
 */
class MultiWorkflowAgentTest {

    private static final String MOCK_PROVIDER = "SystemMultiWorkflowMockProvider";
    private static final String DEFAULT_TEXT = "Sorry, I cannot understand your question";

    private static final ArrayDeque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
            MOCK_PROVIDER,
            BaseModelInfo.builder()
                    .modelName("workflow-agent-mock")
                    .apiBase("https://mock.openai.com/v1")
                    .apiKey("sk-fake")
                    .temperature(0.0)
                    .topP(0.1)
                    .verifySsl(false)
                    .build()
    );

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
            .clientProvider(MOCK_PROVIDER)
            .apiKey("sk-fake")
            .apiBase("https://mock.openai.com/v1")
            .verifySsl(false)
            .build();

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
            .modelName("workflow-agent-mock")
            .temperature(0.0)
            .topP(0.1)
            .build();

    @BeforeAll
    static void registerMockModel() {
        Model.registerFactory(new MockModelClientFactory());
    }

    @BeforeEach
    void setUp() {
        Runner.start();
        MOCK_RESPONSES.clear();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
        MOCK_RESPONSES.clear();
    }

    @Test
    @DisplayName("batch End output emits only workflow_final")
    void testEndBatchOutputShouldHaveWorkflowFinal() {
        Workflow workflow = buildStartFixedEndWorkflow("batch_output_flow", "Batch output test", null);
        WorkflowAgent agent = createAgent("test_batch_output_agent", workflow);

        List<Object> chunks = collect(agent.stream(Map.of(
                "query", "hello",
                "conversation_id", "test-batch-output-001"
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> workflowFinalChunks = chunksOfType(chunks, "workflow_final");
        List<OutputSchema> endNodeStreamChunks = chunksOfType(chunks, "end node stream");

        assertEquals(1, workflowFinalChunks.size());
        assertEquals(0, endNodeStreamChunks.size());
        assertFinalResponseContains(workflowFinalChunks, "mock LLM answer");
    }

    @Test
    @DisplayName("streaming End output emits end node stream and no workflow_final")
    void testEndStreamOutputShouldHaveEndNodeStream() {
        Workflow workflow = buildStartFixedEndWorkflow("stream_output_flow", "Stream output test", "streaming");
        WorkflowAgent agent = createAgent("test_stream_output_agent", workflow);

        List<Object> chunks = collect(agent.stream(Map.of(
                "query", "hello",
                "conversation_id", "test-stream-output-001"
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> workflowFinalChunks = chunksOfType(chunks, "workflow_final");
        List<OutputSchema> endNodeStreamChunks = chunksOfType(chunks, "end node stream");

        assertFalse(endNodeStreamChunks.isEmpty());
        assertEquals(0, workflowFinalChunks.size());
        assertFinalResponseContains(endNodeStreamChunks, "mock LLM answer");
    }

    @Test
    @DisplayName("configured default_response is returned when no workflow is detected")
    void testDefaultResponseWhenNoTaskDetected() {
        setMockResponses(textResponse("{\"result\": 0}"));
        WorkflowAgent agent = createDefaultResponseAgent("test_default_response_agent");

        ControllerOutput output = agent.invoke(Map.of(
                "query", "blahblah random xyz",
                "conversation_id", "test-default-response-001"
        ), null);

        Map<String, Object> result = output.getDataAsMap();
        assertNotNull(result);
        assertEquals("default_response", result.get("status"));
        assertEquals("answer", result.get("result_type"));
        assertInstanceOf(Map.class, result.get("output"));
        @SuppressWarnings("unchecked")
        Map<String, Object> outputMap = (Map<String, Object>) result.get("output");
        assertEquals(DEFAULT_TEXT, outputMap.get("answer"));
    }

    @Test
    @DisplayName("first workflow is used when no default_response is configured")
    void testFallbackToFirstWorkflowWhenNoDefaultResponse() {
        setMockResponses(textResponse("{\"result\": 0}"));
        WorkflowAgent agent = createFallbackAgent("test_no_default_response_agent");

        ControllerOutput output = agent.invoke(Map.of(
                "query", "blahblah random xyz",
                "conversation_id", "test-no-default-response-001"
        ), null);

        Map<String, Object> result = output.getDataAsMap();
        assertNotNull(result);
        assertEquals("answer", result.get("result_type"));
        assertInstanceOf(WorkflowOutput.class, result.get("output"));
        assertTrue(responseFrom((WorkflowOutput) result.get("output")).contains("weather:"));
    }

    @Test
    @DisplayName("streaming default_response returns workflow_final payload")
    void testDefaultResponseStreamReturnsWorkflowFinal() {
        setMockResponses(textResponse("{\"result\": 0}"));
        WorkflowAgent agent = createDefaultResponseAgent("test_default_response_stream_agent");

        List<Object> chunks = collect(agent.stream(Map.of(
                "query", "blahblah random xyz",
                "conversation_id", "test-default-response-stream-001"
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> workflowFinalChunks = chunksOfType(chunks, "workflow_final");
        assertEquals(1, workflowFinalChunks.size());
        Object payload = workflowFinalChunks.get(0).getPayload();
        assertInstanceOf(Map.class, payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) payload;
        assertEquals(DEFAULT_TEXT, payloadMap.get("response"));
    }

    private static WorkflowAgent createDefaultResponseAgent(String agentId) {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("multi workflow default response test")
                .model(MODEL_CONFIG)
                .defaultResponse(DefaultResponse.builder().type("text").text(DEFAULT_TEXT).build())
                .build();
        return createAgent(config, buildTwoWorkflows());
    }

    private static WorkflowAgent createFallbackAgent(String agentId) {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("multi workflow fallback test")
                .model(MODEL_CONFIG)
                .build();
        return createAgent(config, buildTwoWorkflows());
    }

    private static WorkflowAgent createAgent(String agentId, Workflow workflow) {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("workflow output mode test")
                .build();
        return createAgent(config, List.of(workflow));
    }

    private static WorkflowAgent createAgent(WorkflowAgentConfig config, List<Workflow> workflows) {
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(workflows);
        return agent;
    }

    private static List<Workflow> buildTwoWorkflows() {
        return List.of(
                prefixedWorkflow("weather_flow", "weather_query", "Query weather, temperature, forecast", "weather:"),
                prefixedWorkflow("stock_flow", "stock_query", "Query stock price, market trends", "stock:")
        );
    }

    private static Workflow buildStartFixedEndWorkflow(String workflowId, String workflowName, String responseMode) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowName)
                .version("1.0")
                .description(workflowName)
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        if ("streaming".equals(responseMode)) {
            flow.addWorkflowComp("llm", new FixedAnswerComponent(), true, Map.of("query", "${start.query}"),
                    null, null, null, List.of(ComponentAbility.STREAM));
            flow.setEndComp("end", new End(Map.of("responseTemplate", "Result: {{output}}")),
                    null, null, Map.of("output", "${llm.output}"), null, "streaming");
            flow.addConnection("start", "llm");
            flow.addStreamConnection("llm", "end");
        } else {
            flow.addWorkflowComp("llm", new FixedAnswerComponent(), Map.of("query", "${start.query}"));
            flow.setEndComp("end", new End(Map.of("responseTemplate", "Result: {{output}}")),
                    Map.of("output", "${llm.output}"), responseMode);
            flow.addConnection("start", "llm");
            flow.addConnection("llm", "end");
        }
        return flow;
    }

    private static Workflow prefixedWorkflow(String workflowId, String name, String description, String prefix) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(name)
                .version("1.0")
                .description(description)
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.setEndComp("end", new End(Map.of("responseTemplate", prefix + "{{output}}")),
                Map.of("output", "${start.query}"));
        flow.addConnection("start", "end");
        return flow;
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                .finishReason("stop")
                .build();
    }

    private static List<Object> collect(Iterator<?> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static List<OutputSchema> chunksOfType(List<?> chunks, String type) {
        List<OutputSchema> matches = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema && type.equals(schema.getType())) {
                matches.add(schema);
            }
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    private static void assertFinalResponseContains(List<OutputSchema> chunks, String expected) {
        assertFalse(chunks.isEmpty());
        Object payload = chunks.get(chunks.size() - 1).getPayload();
        assertInstanceOf(Map.class, payload);
        Object response = ((Map<String, Object>) payload).get("response");
        assertTrue(String.valueOf(response).contains(expected));
    }

    private static String responseFrom(WorkflowOutput output) {
        if (output == null || output.getState() != WorkflowExecutionState.COMPLETED) {
            return "";
        }
        Object result = output.getResult();
        if (result instanceof Map<?, ?> map) {
            Object response = map.get("response");
            return response != null ? response.toString() : "";
        }
        return result != null ? result.toString() : "";
    }

    private static final class FixedAnswerComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return new LinkedHashMap<>(Map.of("output", "mock LLM answer"));
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            return List.<Object>of(Map.of("output", "mock LLM answer")).iterator();
        }
    }

    private static final class MockModelClientFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return MOCK_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new MockModelClient(modelConfig, clientConfig);
        }
    }

    private static final class MockModelClient extends BaseModelClient {
        private MockModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            AssistantMessage response = MOCK_RESPONSES.pollFirst();
            return response != null ? response : textResponse("{\"result\": 1}");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            AssistantMessage response = invoke(messages, tools, temperature, topP, model, maxTokens,
                    stop, outputParser, timeout, kwargs);
            return List.of(AssistantMessageChunk.builder()
                    .content(response.getContent())
                    .toolCalls(response.getToolCalls())
                    .usageMetadata(response.getUsageMetadata())
                    .finishReason(response.getFinishReason())
                    .build()).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in workflow tests");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in workflow tests");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in workflow tests");
        }
    }
}
