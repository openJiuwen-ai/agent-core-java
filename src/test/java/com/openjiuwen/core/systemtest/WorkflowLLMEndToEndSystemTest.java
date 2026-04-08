/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end integration test combining Workflow + LLM invocation.
 * A custom WorkflowComponent calls the real LLM API.
 * Corresponds to Python's build_workflow_agent example.
 */
@Tag("system-test")
class WorkflowLLMEndToEndSystemTest {

    private static Model model;

    @BeforeAll
    static void setUp() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ApiConfigLoader.getModelProvider())
                .apiKey(ApiConfigLoader.getApiKey())
                .apiBase(ApiConfigLoader.getApiBase())
                .timeout(120.0)
                .maxRetries(2)
                .verifySsl(ApiConfigLoader.getSslVerify())
                .sslCert(ApiConfigLoader.getSslCert())
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(ApiConfigLoader.getModelName())
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(512)
                .build();

        model = new Model(clientConfig, requestConfig);
    }

    /**
     * A workflow component that calls the LLM API.
     */
    static class LLMCallerComponent extends WorkflowComponent {
        private final Model llmModel;

        LLMCallerComponent(Model llmModel) {
            this.llmModel = llmModel;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            try {
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                String query = (String) inputMap.get("query");

                List<UserMessage> messages = List.of(new UserMessage(query));
                AssistantMessage response = llmModel.invoke(
                        messages, null, 0.7f, 0.9f, null, 256, null, null, null, null);

                String content = response != null ? response.getContentAsString() : "无回复";
                return Map.of("answer", content != null ? content : "无内容");
            } catch (Exception e) {
                return Map.of("answer", "LLM调用失败: " + e.getMessage());
            }
        }
    }

    /**
     * A component that transforms text to uppercase.
     */
    static class UpperCaseComponent extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            String text = String.valueOf(inputMap.get("text"));
            return Map.of("result", text.toUpperCase());
        }
    }

    private static WorkflowSessionApi newSession() {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of());
    }

    @Test
    @DisplayName("End-to-end: Workflow with real LLM call")
    void testWorkflowWithLLMCall() {
        WorkflowCard card = WorkflowCard.builder()
                .id("llm-workflow")
                .name("LLM Query Workflow")
                .description("Workflow that queries an LLM")
                .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("llm", new LLMCallerComponent(model),
                Map.of("query", "${start.query}"), null);
        flow.setEndComp("end", new End(Map.of(
                        "responseTemplate", "问题：{{query}}，回答：{{answer}}")),
                Map.of("query", "${start.query}", "answer", "${llm.answer}"), null);
        flow.addConnection("start", "llm");
        flow.addConnection("llm", "end");

        WorkflowOutput output = flow.invoke(
                Map.of("query", "1加1等于几？"), newSession(), null);

        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertNotNull(output.getResult());
        String result = output.getResult().toString();
        assertFalse(result.isEmpty(), "Result should not be empty");
        System.out.println("[E2E LLM Workflow] Result: " + output.getResult());
    }

    @Test
    @DisplayName("End-to-end: Multi-step workflow with LLM and transformation")
    void testMultiStepWorkflowWithLLM() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("llm", new LLMCallerComponent(model),
                Map.of("query", "${start.query}"), null);
        flow.addWorkflowComp("transform", new UpperCaseComponent(),
                Map.of("text", "${llm.answer}"), null);
        flow.setEndComp("end", new End(),
                Map.of("answer", "${llm.answer}", "transformed", "${transform.result}"), null);

        flow.addConnection("start", "llm");
        flow.addConnection("llm", "transform");
        flow.addConnection("transform", "end");

        WorkflowOutput output = flow.invoke(
                Map.of("query", "用英文说hello"), newSession(), null);

        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertNotNull(output.getResult());
        System.out.println("[E2E MultiStep] Result: " + output.getResult());
    }
}
