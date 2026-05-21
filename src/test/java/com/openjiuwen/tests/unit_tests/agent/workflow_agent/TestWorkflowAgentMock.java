/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.workflow_agent;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.WorkflowUtils;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes;
import com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow Agent mock tests using Mock LLM.
 * <p>
 * Mirrors Python's {@code test_workflow_agent_mock.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 */
@DisplayName("WorkflowAgent Mock Tests")
class TestWorkflowAgentMock {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    /**
     * Build a simple workflow: start -> node -> end.
     */
    private static Workflow buildSimpleWorkflow(String workflowId, String workflowName, String version) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .version(version)
                .name(workflowName)
                .description("Simple workflow: " + workflowName)
                .build();
        Workflow flow = new Workflow(card);

        flow.setStartComp("start", new MockNodes.MockStartNode("start"),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("node_a", new MockNodes.Node1("node_a"),
                Map.of("output", "${start.query}"), null);
        flow.setEndComp("end", new MockNodes.MockEndNode("end"),
                Map.of("result", "${node_a.output}"), null);

        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");

        return flow;
    }

    /**
     * Build a workflow with Questioner component for interrupt testing.
     */
    private static Workflow buildQuestionerWorkflow(
            String workflowId, String workflowName, String fieldName, String fieldDesc, String version) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .version(version)
                .name(workflowName)
                .description("Questioner workflow: " + workflowName)
                .build();
        Workflow flow = new Workflow(card);

        // Start component
        Start start = new Start();

        // Questioner component (will trigger interrupt)
        List<FieldInfo> keyFields = List.of(
                FieldInfo.builder()
                        .fieldName(fieldName)
                        .description(fieldDesc)
                        .required(true)
                        .build()
        );
        QuestionerConfig questionerConfig = QuestionerConfig.builder()
                .questionContent("")
                .extractFieldsFromResponse(true)
                .fieldNames(keyFields)
                .withChatHistory(false)
                .build();
        QuestionerComponent questioner = new QuestionerComponent(questionerConfig);

        // End component
        End end = new End(Map.of("responseTemplate", workflowName + "完成: {{questioner." + fieldName + "}}"));

        // Register components
        flow.setStartComp("start", start, Map.of("query", "${query}"), null);
        flow.addWorkflowComp("questioner", questioner, Map.of("query", "${start.query}"), null);
        flow.setEndComp("end", end, Map.of(fieldName, "${questioner." + fieldName + "}"), null);

        // Connect topology
        flow.addConnection("start", "questioner");
        flow.addConnection("questioner", "end");

        return flow;
    }

    @Nested
    @DisplayName("Basic Execution Tests")
    class BasicExecutionTests {

        @Test
        @DisplayName("workflow agent basic execution completes successfully")
        void testWorkflowAgentBasicExecution() {
            // Build workflow
            Workflow workflow = buildSimpleWorkflow("simple_workflow", "简单工作流", "1.0");

            // Use new API: addWorkflows() to automatically extract schema from workflow.card
            WorkflowAgentConfig workflowConfig = WorkflowAgentConfig.builder()
                    .id("simple_workflow_agent")
                    .version("1.0")
                    .description("简单工作流测试")
                    .build();
            WorkflowAgent agent = new WorkflowAgent(workflowConfig);
            agent.addWorkflows(List.of(workflow));

            // Execute
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) agent.invoke(Map.of("query", "hello"));

            // Verify
            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Interrupt Tests")
    class InterruptTests {

        @Test
        @DisplayName("workflow agent with interrupt returns interaction request")
        void testWorkflowAgentWithInterrupt() {
            MockLLMModel mockLlm = new MockLLMModel();
            // Questioner extracts field, location is null, triggers interrupt
            mockLlm.setResponses(List.of(
                    MockLLMModel.createJsonResponse(Map.of("location", null))
            ));

            // Build workflow
            Workflow workflow = buildQuestionerWorkflow(
                    "location_workflow", "地点查询", "location", "地点名称", "1.0");

            WorkflowAgentConfig workflowConfig = WorkflowAgentConfig.builder()
                    .id("location_workflow_agent")
                    .version("1.0")
                    .description("地点查询工作流")
                    .build();
            WorkflowAgent agent = new WorkflowAgent(workflowConfig);
            agent.addWorkflows(List.of(workflow));

            // Execute - should trigger interrupt
            String conversationId = "test_interrupt";
            Object result = agent.invoke(Map.of(
                    "conversation_id", conversationId,
                    "query", "查询天气"
            ));

            // Verify interrupt
            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> interactionList = (List<Map<String, Object>>) result;
            assertThat(interactionList.get(0).get("type")).isEqualTo("__interaction__");
        }

        @Test
        @DisplayName("workflow agent interrupt resume completes workflow")
        void testWorkflowAgentInterruptResume() {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(
                    // First: extract field, location is null, triggers interrupt
                    MockLLMModel.createJsonResponse(Map.of("location", null)),
                    // Second: after resume, location has value
                    MockLLMModel.createJsonResponse(Map.of("location", "上海"))
            ));

            // Build workflow
            Workflow workflow = buildQuestionerWorkflow(
                    "location_workflow_resume", "地点查询", "location", "地点名称", "1.0");

            WorkflowAgentConfig workflowConfig = WorkflowAgentConfig.builder()
                    .id("location_workflow_resume_agent")
                    .version("1.0")
                    .description("地点查询工作流")
                    .build();
            WorkflowAgent agent = new WorkflowAgent(workflowConfig);
            agent.addWorkflows(List.of(workflow));

            // First call - trigger interrupt
            String conversationId = "test_resume";
            Object result1 = agent.invoke(Map.of(
                    "conversation_id", conversationId,
                    "query", "查询天气"
            ));

            // Verify first result is interaction list
            assertThat(result1).isInstanceOf(List.class);

            // Second call - use InteractiveInput to resume
            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("questioner", "上海");

            @SuppressWarnings("unchecked")
            Map<String, Object> result2 = (Map<String, Object>) agent.invoke(Map.of(
                    "conversation_id", conversationId,
                    "query", interactiveInput
            ));

            // Verify completion
            assertThat(result2).isInstanceOf(Map.class);
            assertThat(result2.get("result_type")).isEqualTo("answer");
        }
    }

    /**
     * Test workflow tag isolation for WorkflowAgent.
     * Covers commit: fix(controller): use agent_id as tag to get tool.
     * <p>
     * Core logic:
     * - WorkflowAgent (BaseAgent) calls Runner.resource_mgr.add_workflow(..., tag=self.agent_config.id)
     * - WorkflowController retrieves workflows via Runner.resource_mgr.get_workflow(..., tag=self.agent_config.id)
     */
    @Nested
    @DisplayName("Tag Isolation Tests")
    class TagIsolationTests {

        @Test
        @DisplayName("workflow is tagged with agent_config.id in resource_mgr")
        void testWorkflowAgentWorkflowTaggedWithAgentId() {
            Workflow workflow = buildSimpleWorkflow("tag_test_wf", "Tag Test WF", "1.0");

            WorkflowAgentConfig agentConfig = WorkflowAgentConfig.builder()
                    .id("wf_agent_tag_test")
                    .version("1.0")
                    .description("Tag Test Agent")
                    .build();
            WorkflowAgent agent = new WorkflowAgent(agentConfig);
            agent.addWorkflows(List.of(workflow));

            // Verify: workflow is tagged with agent_config.id, not GLOBAL
            String wfKey = WorkflowUtils.generateWorkflowKey("tag_test_wf", "1.0");
            assertThat(Runner.resourceMgr().resourceHasTag(wfKey, "wf_agent_tag_test")).isTrue();
            assertThat(Runner.resourceMgr().resourceHasTag(wfKey, Tag.GLOBAL)).isFalse();
        }

        @Test
        @DisplayName("workflows registered by two WorkflowAgents are isolated via tag")
        void testTwoWorkflowAgentsIsolated() {
            // Agent A
            Workflow wfA = buildSimpleWorkflow("wf_iso_a", "WF A", "1.0");
            WorkflowAgentConfig configA = WorkflowAgentConfig.builder()
                    .id("iso_agent_A")
                    .version("1.0")
                    .description("A")
                    .build();
            WorkflowAgent agentA = new WorkflowAgent(configA);
            agentA.addWorkflows(List.of(wfA));

            // Agent B
            Workflow wfB = buildSimpleWorkflow("wf_iso_b", "WF B", "1.0");
            WorkflowAgentConfig configB = WorkflowAgentConfig.builder()
                    .id("iso_agent_B")
                    .version("1.0")
                    .description("B")
                    .build();
            WorkflowAgent agentB = new WorkflowAgent(configB);
            agentB.addWorkflows(List.of(wfB));

            String wfKeyA = WorkflowUtils.generateWorkflowKey("wf_iso_a", "1.0");
            String wfKeyB = WorkflowUtils.generateWorkflowKey("wf_iso_b", "1.0");

            // Agent A's workflow belongs to iso_agent_A only, not iso_agent_B
            assertThat(Runner.resourceMgr().resourceHasTag(wfKeyA, "iso_agent_A")).isTrue();
            assertThat(Runner.resourceMgr().resourceHasTag(wfKeyA, "iso_agent_B")).isFalse();

            // Agent B's workflow belongs to iso_agent_B only, not iso_agent_A
            assertThat(Runner.resourceMgr().resourceHasTag(wfKeyB, "iso_agent_B")).isTrue();
            assertThat(Runner.resourceMgr().resourceHasTag(wfKeyB, "iso_agent_A")).isFalse();
        }
    }
}