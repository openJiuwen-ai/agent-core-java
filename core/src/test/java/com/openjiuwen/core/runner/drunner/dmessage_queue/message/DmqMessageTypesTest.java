/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused parity checks for distributed message DTO defaults and payload routing.
 */
class DmqMessageTypesTest {

    @Test
    void testRequestDefaults() {
        DmqRequestMessage message = new DmqRequestMessage();
        assertEquals(DMessageType.INPUT, message.getType());
        assertEquals("", message.getReplyTopic());
        assertEquals("", message.getRequestId());
        assertEquals("", message.getSenderId());
        assertEquals("", message.getReceiverId());
        assertFalse(message.isEnableStream());
        assertEquals(null, message.getExpireAt());
    }

    @Test
    void testResponseDefaults() {
        DmqResponseMessage message = new DmqResponseMessage();
        assertEquals(DMessageType.OUTPUT, message.getType());
        assertEquals(ResultType.MESSAGE, message.getResultType());
        assertEquals("", message.getRequestId());
        assertEquals("", message.getSenderId());
        assertEquals("", message.getReceiverId());
        assertEquals(0, message.getSeq());
        assertFalse(message.isLastChunk());
        assertEquals(null, message.getExpireAt());
    }

    @Test
    void testPayloadSetterRoutesToBodyAndPayloadGetterReturnsEnvelope() {
        DmqResponseMessage message = new DmqResponseMessage();
        message.setPayload("hello");
        assertEquals("hello", message.getBody());
        assertSame(message, message.getPayload());
    }

    @Test
    void testEnumNamesMatchPythonValues() {
        assertEquals("INPUT", DMessageType.INPUT.name());
        assertEquals("STOP", DMessageType.STOP.name());
        assertEquals("OUTPUT", DMessageType.OUTPUT.name());
        assertEquals("MESSAGE", ResultType.MESSAGE.name());
        assertEquals("ERROR", ResultType.ERROR.name());
    }
}
