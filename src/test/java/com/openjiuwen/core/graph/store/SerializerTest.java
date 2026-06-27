/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.graph.pregel.Message;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Serializer.TypedBytes jsonBytes(String json) {
        return new Serializer.TypedBytes("json", json.getBytes(StandardCharsets.UTF_8));
    }
}
