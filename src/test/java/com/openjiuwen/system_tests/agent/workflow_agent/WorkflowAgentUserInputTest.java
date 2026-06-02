/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.workflow_agent;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/system_tests/agent/workflow_agent/test_workflow_agent_user_input.py}.
 */
class WorkflowAgentUserInputTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("dict interrupt should be returned again until structured input is supplied")
    void testDictInterruptShouldReturnAgain() {
        WorkflowAgent agent = createAgent("test_dict_interrupt_agent", buildUserInputWorkflow());
        String conversationId = "test-dict-interrupt-001";

        List<Object> first = stream(agent, "weather please", conversationId);
        List<OutputSchema> firstInteractions = chunksOfType(first, "__interaction__");
        assertEquals(1, firstInteractions.size());
        InteractionOutput firstInterrupt = interaction(firstInteractions.get(0));
        assertInstanceOf(Map.class, firstInterrupt.getValue());
        assertStructuredRequest(firstInterrupt.getValue());

        List<Object> second = stream(agent, "still weather please", conversationId);
        List<OutputSchema> secondInteractions = chunksOfType(second, "__interaction__");
        assertEquals(1, secondInteractions.size());
        InteractionOutput secondInterrupt = interaction(secondInteractions.get(0));
        assertInstanceOf(Map.class, secondInterrupt.getValue());
        assertStructuredRequest(secondInterrupt.getValue());

        InteractiveInput input = new InteractiveInput();
        input.update(secondInterrupt.getId(), Map.of("location", "Beijing", "date", "tomorrow"));
        List<Object> third = collect(agent.stream(Map.of(
                "query", input,
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));

        assertFinalResponseContains(third, "Beijing");
        assertFinalResponseContains(third, "tomorrow");
    }

    @Test
    @DisplayName("string interrupt should continue when raw user text is supplied")
    void testStrInterruptShouldContinueWithQuestioner() {
        WorkflowAgent agent = createAgent("test_str_interrupt_agent", buildStringInputWorkflow());
        String conversationId = "test-str-interrupt-001";

        List<Object> first = stream(agent, "question please", conversationId);
        List<OutputSchema> interactions = chunksOfType(first, "__interaction__");
        assertEquals(1, interactions.size());
        InteractionOutput interrupt = interaction(interactions.get(0));
        assertInstanceOf(String.class, interrupt.getValue());
        assertTrue(String.valueOf(interrupt.getValue()).contains("location"));

        List<Object> second = collect(agent.stream(Map.of(
                "query", new InteractiveInput("Beijing"),
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));

        assertFinalResponseContains(second, "Beijing");
    }

    @Test
    @DisplayName("UserInputComponent builds dict payload with required metadata")
    void testUserInputComponentReturnsDict() {
        List<UserInputElem> fields = List.of(
                new UserInputElem("name", "Your name", true, null),
                new UserInputElem("email", "Your email", false, "")
        );

        Map<String, Object> request = UserInputComponent.requestDict(fields);

        assertTrue(request.containsKey("name"));
        assertTrue(request.containsKey("email"));
        assertInstanceOf(Map.class, request.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nameField = (Map<String, Object>) request.get("name");
        assertEquals("Your name", nameField.get("description"));
        assertEquals(true, nameField.get("required"));
        assertTrue(nameField.containsKey("default"));
    }

    private static WorkflowAgent createAgent(String agentId, Workflow workflow) {
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id(agentId)
                .version("1.0")
                .description("workflow user input test")
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.addWorkflows(List.of(workflow));
        return agent;
    }

    private static Workflow buildUserInputWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("user_input_flow")
                .name("user input workflow")
                .version("1.0")
                .description("dict interrupt workflow")
                .inputParams(Map.of("type", "object", "properties", Map.of(
                        "query", Map.of("type", "string", "description", "user input")
                )))
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("user_input", new UserInputComponent(List.of(
                new UserInputElem("location", "Please provide location", true, null),
                new UserInputElem("date", "Please provide date", false, "today")
        )), Map.of());
        flow.setEndComp("end", new End(Map.of(
                        "responseTemplate", "received location {{location}}, date {{date}}")),
                Map.of("location", "${user_input.location}", "date", "${user_input.date}"));
        flow.addConnection("start", "user_input");
        flow.addConnection("user_input", "end");
        return flow;
    }

    private static Workflow buildStringInputWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("questioner_flow")
                .name("questioner workflow")
                .version("1.0")
                .description("string interrupt workflow")
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        flow.addWorkflowComp("questioner", new StringInputComponent(), Map.of());
        flow.setEndComp("end", new End(Map.of("responseTemplate", "received answer {{location}}")),
                Map.of("location", "${questioner.location}"));
        flow.addConnection("start", "questioner");
        flow.addConnection("questioner", "end");
        return flow;
    }

    private static List<Object> stream(WorkflowAgent agent, String query, String conversationId) {
        return collect(agent.stream(Map.of(
                "query", query,
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));
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

    private static InteractionOutput interaction(OutputSchema schema) {
        assertEquals("__interaction__", schema.getType());
        assertInstanceOf(InteractionOutput.class, schema.getPayload());
        return (InteractionOutput) schema.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static void assertStructuredRequest(Object request) {
        Map<String, Object> fields = (Map<String, Object>) request;
        assertTrue(fields.containsKey("location"));
        assertTrue(fields.containsKey("date"));
        assertInstanceOf(Map.class, fields.get("location"));
        assertEquals(true, ((Map<String, Object>) fields.get("location")).get("required"));
    }

    @SuppressWarnings("unchecked")
    private static void assertFinalResponseContains(List<?> chunks, String expected) {
        List<OutputSchema> finals = chunksOfType(chunks, "workflow_final");
        assertFalse(finals.isEmpty());
        Object payload = finals.get(finals.size() - 1).getPayload();
        assertInstanceOf(Map.class, payload);
        Object response = ((Map<String, Object>) payload).get("response");
        assertNotNull(response);
        assertTrue(String.valueOf(response).contains(expected));
    }

    private record UserInputElem(String inputName, String inputDescription, boolean required, Object defaultValue) {
    }

    private static final class UserInputComponent extends WorkflowComponent {
        private final List<UserInputElem> inputConfList;

        private UserInputComponent(List<UserInputElem> inputConfList) {
            this.inputConfList = inputConfList;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> result = session.interact(requestDict(inputConfList));
            for (UserInputElem inputElem : inputConfList) {
                if (inputElem.required() && result.get(inputElem.inputName()) == null) {
                    throw new IllegalArgumentException("required user input is missing: " + inputElem.inputName());
                }
            }
            return result;
        }

        private static Map<String, Object> requestDict(List<UserInputElem> inputConfList) {
            Map<String, Object> request = new LinkedHashMap<>();
            for (UserInputElem elem : inputConfList) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("description", elem.inputDescription());
                field.put("required", elem.required());
                field.put("default", elem.defaultValue());
                request.put(elem.inputName(), field);
            }
            return request;
        }
    }

    private static final class StringInputComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object value = session.interact("Please provide location");
            return Map.of("location", value);
        }
    }
}
