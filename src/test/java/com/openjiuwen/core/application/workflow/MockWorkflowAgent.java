/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
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
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Test adapter for WorkflowAgent unit tests.
 *
 * <p>In Python, mock_workflow_agent.py is a test double that wraps
 * ControllerAgent for testing WorkflowAgent behavior without real LLM calls.
 *
 * <p>Mirrors Python's {@code mock_workflow_agent.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
final class MockWorkflowAgent {

    static final String MOCK_PROVIDER = "WorkflowAgentMockProvider";

    private static final ArrayDeque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
            MOCK_PROVIDER,
            BaseModelInfo.builder()
                    .modelName("gpt-4o-mock")
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
            .modelName("gpt-4o-mock")
            .temperature(0.0)
            .topP(0.1)
            .build();

    static {
        registerMockModelFactory();
    }

    private final WorkflowAgent delegate;

    private MockWorkflowAgent(WorkflowAgentConfig config) {
        registerMockModelFactory();
        this.delegate = new WorkflowAgent(config);
    }

    static MockWorkflowAgent of(WorkflowAgentConfig config) {
        return new MockWorkflowAgent(config);
    }

    static WorkflowAgent createAgent(String agentId, Workflow... workflows) {
        MockWorkflowAgent mockAgent = of(config(agentId));
        mockAgent.addWorkflows(Arrays.asList(workflows));
        return mockAgent.unwrap();
    }

    static WorkflowAgent createAgentWithModel(String agentId, Workflow... workflows) {
        MockWorkflowAgent mockAgent = of(configWithModel(agentId));
        mockAgent.addWorkflows(Arrays.asList(workflows));
        return mockAgent.unwrap();
    }

    static WorkflowAgentConfig config(String agentId) {
        return WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("workflow agent test")
                .build();
    }

    static WorkflowAgentConfig configWithModel(String agentId) {
        return WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("workflow agent model test")
                .model(MODEL_CONFIG)
                .build();
    }

    static WorkflowAgentConfig configWithDefaultResponse(String agentId, String text) {
        return WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("workflow agent default response test")
                .model(MODEL_CONFIG)
                .defaultResponse(DefaultResponse.builder().type("text").text(text).build())
                .build();
    }

    void addWorkflows(List<Workflow> workflows) {
        delegate.addWorkflows(workflows);
    }

    ControllerOutput invoke(Object inputs, Session session) {
        return delegate.invoke(inputs, session);
    }

    Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return delegate.stream(inputs, session, streamModes);
    }

    WorkflowAgent unwrap() {
        return delegate;
    }

    WorkflowAgentConfig getAgentConfig() {
        return delegate.getAgentConfig();
    }

    static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                .finishReason("stop")
                .build();
    }

    static ModelConfig modelConfig() {
        return MODEL_CONFIG;
    }

    static QuestionerComponent questioner(String question) {
        QuestionerConfig config = new QuestionerConfig(
                MODEL_REQUEST_CONFIG,
                MODEL_CLIENT_CONFIG,
                question,
                false,
                List.of(new FieldInfo("user_response", "User response", true)),
                false
        );
        return new QuestionerComponent(config);
    }

    static Workflow simpleWorkflow(String workflowId, String name) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(name)
                .version("1.0")
                .description("Simple workflow for test")
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.setEndComp("end", new End(Map.of("responseTemplate", "{{output}}")),
                Map.of("output", "${start.query}"));
        flow.addConnection("start", "end");
        return flow;
    }

    static Workflow prefixedWorkflow(String workflowId, String name, String description, String prefix) {
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

    static Workflow questionerWorkflow(String workflowId, String name, String description, String question,
                                       String responsePrefix) {
        return questionerWorkflow(workflowId, name, description, "questioner", question, responsePrefix);
    }

    static Workflow questionerWorkflow(String workflowId, String name, String description, String questionerId,
                                       String question, String responsePrefix) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(name)
                .version("1.0")
                .description(description)
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp(questionerId, questioner(question), Map.of("query", "${start.query}"));
        flow.setEndComp("end", new End(Map.of("responseTemplate", responsePrefix + "{{user_response}}")),
                Map.of("user_response", "${" + questionerId + ".user_response}"));
        flow.addConnection("start", questionerId);
        flow.addConnection(questionerId, "end");
        return flow;
    }

    static List<Object> collect(Iterator<?> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    static List<OutputSchema> chunksOfType(List<?> chunks, String type) {
        List<OutputSchema> matches = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema os && type.equals(os.getType())) {
                matches.add(os);
            }
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    static List<OutputSchema> interactionData(ControllerOutput output) {
        if (output == null || !(output.getData() instanceof List<?> list)) {
            return List.of();
        }
        List<OutputSchema> interactions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                interactions.add(os);
            }
        }
        return interactions;
    }

    static Map<String, Object> dataMap(ControllerOutput output) {
        return output != null ? output.getDataAsMap() : null;
    }

    static WorkflowOutput workflowOutput(ControllerOutput output) {
        Map<String, Object> data = dataMap(output);
        Object workflowOutput = data != null ? data.get("output") : null;
        return workflowOutput instanceof WorkflowOutput wo ? wo : null;
    }

    static String responseFrom(WorkflowOutput output) {
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

    private static void registerMockModelFactory() {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return MOCK_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new MockModelClient(modelConfig, clientConfig);
            }
        });
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
