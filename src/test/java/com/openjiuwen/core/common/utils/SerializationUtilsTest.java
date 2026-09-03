/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.graph.pregel.Message;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.singleagent.interrupt.ToolCallInterruptRequest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

class SerializationUtilsTest {
    @Test
    void dynamicPayloadsSurviveJavaSerialization() throws IOException, ClassNotFoundException {
        BaseMessage message = new BaseMessage("user", List.of(Map.of("text", "hello")), "caller",
                Map.of("trace", "trace-1"));
        Message graphMessage = new Message("sender", "target", Map.of("event", "pending"));
        InteractionOutput output = new InteractionOutput("interaction", Map.of("answer", "yes"));
        ToolCallInterruptRequest request = new ToolCallInterruptRequest();
        request.setQuestions(List.of(Map.of("question", "Continue?")));
        BaseError error = new BaseError(StatusCode.ERROR, "failed", Map.of("detail", "value"), null,
                Map.of("parameter", "value"));

        BaseMessage restoredMessage = roundTrip(message, BaseMessage.class);
        Message restoredGraphMessage = roundTrip(graphMessage, Message.class);
        InteractionOutput restoredOutput = roundTrip(output, InteractionOutput.class);
        ToolCallInterruptRequest restoredRequest = roundTrip(request, ToolCallInterruptRequest.class);
        BaseError restoredError = roundTrip(error, BaseError.class);

        assertEquals(message, restoredMessage);
        assertEquals(Map.of("event", "pending"), restoredGraphMessage.getPayload());
        assertEquals(output, restoredOutput);
        assertEquals(request.getQuestions(), restoredRequest.getQuestions());
        assertEquals(error.getParams(), restoredError.getParams());
        assertEquals(error.getDetails(), restoredError.getDetails());
    }

    @Test
    void dynamicPayloadBoundariesRejectNonSerializableValues() {
        Object nonSerializable = new Object();

        assertThrows(IllegalArgumentException.class,
                () -> new BaseMessage("user", nonSerializable, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Message("sender", "target", nonSerializable));
        assertThrows(IllegalArgumentException.class, () -> new InteractionOutput("interaction", nonSerializable));
    }

    @Test
    void nullablePayloadsPreserveExistingContracts() {
        BaseError error = new BaseError(StatusCode.ERROR, "failed", null, null, Map.of());
        BaseMessage baseMessage = new BaseMessage("user", null, null, null);
        Message graphMessage = new Message("sender", "target");
        InteractionOutput output = new InteractionOutput("interaction", null);
        InteractiveInput input = new InteractiveInput();

        assertDoesNotThrow(() -> input.setRawInputs(null));
        assertNull(error.getDetails());
        assertNull(baseMessage.getContent());
        assertNull(graphMessage.getPayload());
        assertNull(output.getValue());
        assertNull(input.getRawInputs());
    }

    private static <T> T roundTrip(T value, Class<T> valueType) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
            objectOutput.flush();
            serialized = output.toByteArray();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return valueType.cast(objectInput.readObject());
        }
    }
}
