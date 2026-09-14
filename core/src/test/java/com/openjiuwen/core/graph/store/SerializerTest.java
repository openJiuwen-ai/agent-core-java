/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.graph.pregel.Message;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.external.ExternalToolCallRequest;
import com.openjiuwen.core.singleagent.external.ExternalToolPendingState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializerTest {

    @Test
    void jsonSerializerRoundTripsMessagesInsideMap() {
        Serializer serializer = Serializer.create("json");
        Map<String, Object> state = Map.of("messages", List.of(
                new UserMessage("remember maple-742"),
                new AssistantMessage("stored")
        ));

        Object restored = serializer.loadsTyped(serializer.dumpsTyped(state));

        Map<?, ?> restoredMap = assertInstanceOf(Map.class, restored);
        List<?> messages = assertInstanceOf(List.class, restoredMap.get("messages"));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("remember maple-742", userMessage.getContentAsString());
        assertEquals("stored", assistantMessage.getContentAsString());
    }

    @Test
    void jsonSerializerRoundTripsGraphStoreStateWithTypedNestedValues() {
        Serializer serializer = Serializer.create("json");
        GraphStoreState state = GraphStoreState.create(
                "workflow-1",
                3,
                Map.of("channel", List.of(new UserMessage("inside channel"))),
                List.of(new Message("node-a", "node-b", new Message("inner", "target", "payload"))),
                Map.of("node-b", new PendingNode("node-b", "interrupted",
                        List.of(new IllegalStateException("boom")))),
                Map.of("node-a", 1)
        );

        Object restored = serializer.loadsTyped(serializer.dumpsTyped(state));

        GraphStoreState restoredState = assertInstanceOf(GraphStoreState.class, restored);
        assertEquals("workflow-1", restoredState.getNs());
        assertEquals(3, restoredState.getStep());
        List<?> channel = assertInstanceOf(List.class, restoredState.getChannelValues().get("channel"));
        assertInstanceOf(UserMessage.class, channel.get(0));
        Message pendingMessage = restoredState.getPendingBuffer().get(0);
        assertEquals("node-a", pendingMessage.getSender());
        assertInstanceOf(Message.class, pendingMessage.getPayload());
        PendingNode pendingNode = restoredState.getPendingNode().get("node-b");
        assertEquals("interrupted", pendingNode.getStatus());
        assertEquals(1, pendingNode.getExceptions().size());
        RuntimeException exception = assertInstanceOf(RuntimeException.class, pendingNode.getExceptions().get(0));
        assertTrue(exception.getMessage().contains("java.lang.IllegalStateException"));
        assertTrue(exception.getMessage().contains("boom"));
    }

    @Test
    void jsonSerializerPreservesNullPendingNodeExceptions() {
        Serializer serializer = Serializer.create("json");
        GraphStoreState state = GraphStoreState.create(
                "workflow-1",
                3,
                Map.of(),
                List.of(),
                Map.of("node-b", new PendingNode("node-b", "interrupted", null)),
                Map.of()
        );

        Object restored = serializer.loadsTyped(serializer.dumpsTyped(state));

        GraphStoreState restoredState = assertInstanceOf(GraphStoreState.class, restored);
        PendingNode pendingNode = restoredState.getPendingNode().get("node-b");
        assertNull(pendingNode.getExceptions());
    }

    @Test
    void jsonSerializerRoundTripsExternalToolPendingStateInsideAgentState() {
        Serializer serializer = Serializer.create("json");
        ToolCall toolCall = ToolCall.builder()
                .id("call-browser-1")
                .type("function")
                .name("frontend_read_text_input")
                .arguments("{\"field_id\":\"demo_external_text\"}")
                .build();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        ExternalToolPendingState pendingState = new ExternalToolPendingState(
                assistantMessage,
                2,
                "read browser input",
                List.of(toolCall),
                List.of(new ExternalToolCallRequest(
                        "call-browser-1",
                        "frontend_read_text_input",
                        "{\"field_id\":\"demo_external_text\"}"
                ))
        );
        Map<String, Object> state = Map.of("__react_agent_external_tool_pending__", pendingState);

        Object restored = serializer.loadsTyped(serializer.dumpsTyped(state));

        Map<?, ?> restoredMap = assertInstanceOf(Map.class, restored);
        ExternalToolPendingState restoredPending = assertInstanceOf(
                ExternalToolPendingState.class,
                restoredMap.get("__react_agent_external_tool_pending__"));
        assertEquals(2, restoredPending.getIteration());
        assertEquals("read browser input", restoredPending.getOriginalQuery());
        assertEquals("", restoredPending.getAssistantMessage().getContentAsString());
        assertEquals("call-browser-1", restoredPending.getPendingToolCalls().get(0).getId());
        assertEquals("frontend_read_text_input", restoredPending.getPendingToolCalls().get(0).getName());
        assertEquals("call-browser-1", restoredPending.getExternalToolCalls().get(0).getToolCallId());
        assertEquals("frontend_read_text_input", restoredPending.getExternalToolCalls().get(0).getToolName());
    }

    @Test
    void jsonSerializerRejectsUnknownJavaObject() {
        Serializer serializer = Serializer.create("json");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> serializer.dumpsTyped(new Object()));

        assertTrue(error.getMessage().contains("Unsupported JSON value type"));
    }

    @Test
    void javaSerializerUsesJavaTypeNameAndRejectsPickleAlias() {
        Serializer serializer = Serializer.create("java");

        Serializer.TypedBytes typedBytes = serializer.dumpsTyped(Map.of("name", "checkpoint"));
        Object restored = serializer.loadsTyped(typedBytes);

        assertEquals("java", typedBytes.type());
        assertEquals(Map.of("name", "checkpoint"), restored);
        assertThrows(IllegalArgumentException.class, () -> Serializer.create("pickle"));
    }

    @Test
    void javaSerializerRoundTripsGraphStoreStateWithPendingNode() {
        Serializer serializer = Serializer.create("java");
        GraphStoreState state = GraphStoreState.create(
                "workflow-1",
                3,
                Map.of(),
                List.of(),
                Map.of("node-b", new PendingNode("node-b", "interrupted",
                        List.of(new IllegalStateException("boom")))),
                Map.of("node-a", 1)
        );

        Object restored = serializer.loadsTyped(serializer.dumpsTyped(state));

        GraphStoreState restoredState = assertInstanceOf(GraphStoreState.class, restored);
        PendingNode pendingNode = restoredState.getPendingNode().get("node-b");
        assertEquals("node-b", pendingNode.getNodeName());
        assertEquals("interrupted", pendingNode.getStatus());
        assertEquals(1, pendingNode.getExceptions().size());
        assertEquals("boom", pendingNode.getExceptions().get(0).getMessage());
    }

    @Test
    void javaSerializerRoundTripsGraphInterruptPendingNode() {
        Serializer serializer = Serializer.create("java");
        OutputSchema output = new OutputSchema("interaction", 0, new InteractionOutput("interactive", null));
        GraphInterrupt interrupt = new GraphInterrupt(List.of(new Interrupt(output)));
        GraphStoreState state = GraphStoreState.create(
                "workflow-1",
                1,
                Map.of(),
                List.of(),
                Map.of("interactive", new PendingNode("interactive", "interrupted", List.of(interrupt))),
                Map.of()
        );

        GraphStoreState restored = (GraphStoreState) serializer.loadsTyped(serializer.dumpsTyped(state));
        Exception restoredError = restored.getPendingNode().get("interactive").getExceptions().get(0);

        assertInstanceOf(GraphInterrupt.class, restoredError);
        GraphInterrupt restoredInterrupt = (GraphInterrupt) restoredError;
        List<?> restoredValues = assertInstanceOf(List.class, restoredInterrupt.getValue());
        assertFalse(restoredValues.isEmpty());
        Interrupt restoredPayload = assertInstanceOf(Interrupt.class, restoredValues.get(0));
        OutputSchema restoredOutput = assertInstanceOf(OutputSchema.class, restoredPayload.getValue());
        assertEquals("interaction", restoredOutput.getType());
        InteractionOutput restoredInteraction =
                assertInstanceOf(InteractionOutput.class, restoredOutput.getPayload());
        assertEquals("interactive", restoredInteraction.getId());
    }

    @Test
    void javaSerializerRoundTripsExternalToolPendingStateInsideAgentState() {
        Serializer serializer = Serializer.create("java");
        ToolCall toolCall = ToolCall.builder()
                .id("call-browser-1")
                .type("function")
                .name("frontend_read_text_input")
                .arguments("{\"field_id\":\"demo_external_text\"}")
                .build();
        ExternalToolPendingState pendingState = new ExternalToolPendingState(
                AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build(),
                2,
                "read browser input",
                List.of(toolCall),
                List.of(new ExternalToolCallRequest(
                        "call-browser-1",
                        "frontend_read_text_input",
                        "{\"field_id\":\"demo_external_text\"}"
                ))
        );

        Object restored = serializer.loadsTyped(serializer.dumpsTyped(
                Map.of("__react_agent_external_tool_pending__", pendingState)));

        Map<?, ?> restoredMap = assertInstanceOf(Map.class, restored);
        ExternalToolPendingState restoredPending = assertInstanceOf(
                ExternalToolPendingState.class,
                restoredMap.get("__react_agent_external_tool_pending__"));
        assertEquals("call-browser-1", restoredPending.getPendingToolCalls().get(0).getId());
        assertEquals("call-browser-1", restoredPending.getExternalToolCalls().get(0).getToolCallId());
    }

    @Test
    void jsonSerializerRejectsReservedTypeFieldInPlainMap() {
        Serializer serializer = Serializer.create("json");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> serializer.dumpsTyped(Map.of("__jiuwenType", "application.value")));

        assertTrue(error.getMessage().contains("__jiuwenType"));
    }

    @Test
    void jsonSerializerRejectsNullReservedTypeFieldOnRead() {
        Serializer serializer = Serializer.create("json");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":null,"value":"application"}
                        """)));

        assertTrue(error.getMessage().contains("__jiuwenType"));
    }

    @Test
    void jsonSerializerRejectsInvalidGraphStoreStateStep() {
        Serializer serializer = Serializer.create("json");

        IllegalArgumentException stringStep = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":"3","channelValues":{},
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":{}}
                        """)));
        IllegalArgumentException nullStep = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":null,"channelValues":{},
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":{}}
                        """)));

        assertTrue(stringStep.getMessage().contains("step"));
        assertTrue(nullStep.getMessage().contains("step"));
    }

    @Test
    void jsonSerializerRejectsInvalidGraphStoreStateNodeVersionValues() {
        Serializer serializer = Serializer.create("json");

        IllegalArgumentException stringVersion = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":{},
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":{"node-a":"1"}}
                        """)));
        IllegalArgumentException nullVersion = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":{},
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":{"node-a":null}}
                        """)));
        IllegalArgumentException fractionalVersion = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":{},
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":{"node-a":1.5}}
                        """)));

        assertTrue(stringVersion.getMessage().contains("nodeVersion"));
        assertTrue(nullVersion.getMessage().contains("nodeVersion"));
        assertTrue(fractionalVersion.getMessage().contains("nodeVersion"));
    }

    @Test
    void jsonSerializerRejectsNullGraphStoreStateCollections() {
        Serializer serializer = Serializer.create("json");

        IllegalArgumentException channelValues = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":null,
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":{}}
                        """)));
        IllegalArgumentException pendingBuffer = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":{},
                         "pendingBuffer":null,"pendingNode":{},"nodeVersion":{}}
                        """)));
        IllegalArgumentException pendingNode = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":{},
                         "pendingBuffer":[],"pendingNode":null,"nodeVersion":{}}
                        """)));
        IllegalArgumentException nodeVersion = assertThrows(IllegalArgumentException.class,
                () -> serializer.loadsTyped(jsonBytes("""
                        {"__jiuwenType":"graph.storeState","ns":"workflow","step":3,"channelValues":{},
                         "pendingBuffer":[],"pendingNode":{},"nodeVersion":null}
                        """)));

        assertTrue(channelValues.getMessage().contains("channelValues"));
        assertTrue(pendingBuffer.getMessage().contains("pendingBuffer"));
        assertTrue(pendingNode.getMessage().contains("pendingNode"));
        assertTrue(nodeVersion.getMessage().contains("nodeVersion"));
    }

    @Test
    void javaSerializerRejectsClassesOutsideAllowlist() throws Exception {
        Serializer serializer = Serializer.create("java");
        byte[] bytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(new java.net.URL("https://example.com"));
            bytes = bos.toByteArray();
        }
        Serializer.TypedBytes data = new Serializer.TypedBytes("java", bytes);
        assertThrows(IllegalStateException.class, () -> serializer.loadsTyped(data));
    }

    @Test
    void javaSerializerRoundTripsGraphStoreStateWithSerializablePayloads() {
        Serializer serializer = Serializer.create("java");
        Message pendingMessage = new Message("sender", "target", Map.of("event", "pending"));
        GraphStoreState state = GraphStoreState.create(
                "workflow",
                2,
                Map.of("channel", "value"),
                List.of(pendingMessage),
                Map.of(),
                Map.of("start", 1)
        );

        GraphStoreState restored = (GraphStoreState) serializer.loadsTyped(serializer.dumpsTyped(state));

        assertEquals("value", restored.getChannelValues().get("channel"));
        assertEquals(Map.of("event", "pending"), restored.getPendingBuffer().get(0).getPayload());
    }

    private static Serializer.TypedBytes jsonBytes(String json) {
        return new Serializer.TypedBytes("json", json.getBytes(StandardCharsets.UTF_8));
    }
}
