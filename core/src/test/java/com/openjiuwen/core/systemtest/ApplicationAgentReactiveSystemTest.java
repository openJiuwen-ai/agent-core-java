/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for reactive application-layer agents.
 */
@Tag("system-test")
class ApplicationAgentReactiveSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("LlmAgent.invokeAsync invokes remote model")
    void testLlmAgentInvokeAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        String sessionId = trackSessionId("reactive-llm-agent-invoke-session");
        LlmAgent agent = newRemoteLlmAgent(
                uniqueId("reactive-llm-invoke-agent"),
                "Reply in English. If the user asks for an exact token, return that token only."
        );

        StepVerifier.create(reactor.core.publisher.Mono.fromCompletionStage(
                        agent.invoke(
                        Map.of("query", "Reply with the exact token LLM_REACTIVE_INVOKE_OK.",
                                "conversation_id", sessionId),
                        null)))
                .assertNext(result -> assertTrue(
                        containsIgnoreCase(flattenText(result), "LLM_REACTIVE_INVOKE_OK"),
                        () -> "Expected LLM_REACTIVE_INVOKE_OK in output but got: " + flattenText(result)))
                .expectComplete()
                .verify(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("LlmAgent.streamAsync streams remote model output")
    void testLlmAgentStreamAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        String sessionId = trackSessionId("reactive-llm-agent-session");
        LlmAgent agent = newRemoteLlmAgent(
                uniqueId("reactive-llm-agent"),
                "Reply in English. If the user asks for an exact token, return that token only."
        );

        StepVerifier.create(reactor.core.publisher.Flux.fromIterable(
                        () -> agent.stream(
                                Map.of("query", "Reply with the exact token LLM_REACTIVE_STREAM_OK.",
                                        "conversation_id", sessionId),
                                null,
                                List.of(StreamMode.OUTPUT))))
                .assertNext(items -> {
                    assertTrue(containsIgnoreCase(flattenText(items), "LLM_REACTIVE_STREAM_OK"),
                            () -> "Expected LLM_REACTIVE_STREAM_OK in stream but got: " + flattenText(items));
                })
                .expectComplete()
                .verify(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("WorkflowAgent.invokeAsync executes registered workflow")
    void testWorkflowAgentInvokeAsyncExecutesRegisteredWorkflow() {
        WorkflowAgent agent = newWorkflowAgent();
        String sessionId = trackSessionId("reactive-workflow-invoke-session");

        StepVerifier.create(reactor.core.publisher.Mono.fromCompletionStage(
                        agent.invoke(
                        Map.of("query", "invoke coverage", "conversation_id", sessionId),
                        null)))
                .assertNext(result -> {
                    String text = flattenText(result);
                    assertTrue(containsIgnoreCase(text, "workflow reactive handled"),
                            () -> "Expected workflow response in output but got: " + text);
                    assertTrue(containsIgnoreCase(text, "invoke coverage"),
                            () -> "Expected original query in output but got: " + text);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("WorkflowAgent.streamAsync executes registered workflow")
    void testWorkflowAgentStreamAsyncExecutesRegisteredWorkflow() {
        WorkflowAgent agent = newWorkflowAgent();
        String sessionId = trackSessionId("reactive-workflow-stream-session");

        StepVerifier.create(reactor.core.publisher.Flux.fromIterable(
                        () -> agent.stream(
                                Map.of("query", "stream coverage", "conversation_id", sessionId),
                                null,
                                List.of(StreamMode.OUTPUT))))
                .assertNext(items -> {
                    String text = flattenText(items);
                    assertTrue(containsIgnoreCase(text, "workflow reactive handled"),
                            () -> "Expected workflow response in stream but got: " + text);
                    assertTrue(containsIgnoreCase(text, "stream coverage"),
                            () -> "Expected original query in stream but got: " + text);
                })
                .verifyComplete();
    }

    private WorkflowAgent newWorkflowAgent() {
        String workflowBaseId = uniqueId("reactive-app-workflow");
        String workflowVersion = "1";
        String workflowResourceId = workflowBaseId + "_" + workflowVersion;
        String workflowName = uniqueId("ReactiveWorkflow");

        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(workflowResourceId)
                .name(workflowName)
                .description("Reactive workflow used by application system tests")
                .version(workflowVersion)
                .inputParams(inputSchema())
                .build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", "workflow reactive handled {{query}}")),
                Map.of("query", "${start.query}"),
                null
        );
        workflow.addConnection("start", "end");
        registerWorkflow(workflow);

        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(workflowBaseId)
                .name(workflowName)
                .version(workflowVersion)
                .description("Reactive workflow schema")
                .inputParams(inputSchema())
                .build();

        return new WorkflowAgent(WorkflowAgentConfig.builder()
                .id(uniqueId("reactive-workflow-agent"))
                .description("reactive workflow application agent")
                .workflows(List.of(workflowSchema))
                .build());
    }

    private static Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"));
    }
}
