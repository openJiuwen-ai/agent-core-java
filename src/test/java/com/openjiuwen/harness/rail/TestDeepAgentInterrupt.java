/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision.InterruptResult;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for DeepAgent interrupt rail.
 * <p>
 * Mirrors Python's {@code test_deep_agent_interrupt.py} in
 * {@code tests.system_tests.harness.rail}.
 */
class TestDeepAgentInterrupt {

    @Test
    void testDeepagentStreamInterruptResume() {
        ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("write"));
        ToolCall writeCall = ToolCall.builder()
                .id("write-call-1")
                .type("function")
                .name("write")
                .arguments("{\"filepath\":\"/tmp/test.txt\",\"content\":\"hello world\"}")
                .build();
        AtomicInteger writeInvokeCount = new AtomicInteger(0);
        MockRuntimeModel mockRuntimeModel = new MockRuntimeModel(List.of(writeCall, "file written"));

        InterruptDecision firstDecision = rail.resolveInterrupt(null, writeCall, null, null);
        assertTrue(firstDecision.isInterrupted(), "Should detect interrupt on write tool");
        Map<?, ?> request = assertInstanceOf(Map.class,
                assertInstanceOf(InterruptResult.class, firstDecision).getRequest());
        assertEquals("write", request.get("auto_confirm_key"));
        assertNotNull(request.get("payload_schema"));
        assertEquals(0, writeInvokeCount.get(), "Write tool should not be invoked before confirmation");

        InterruptDecision resumeDecision = rail.resolveInterrupt(null, writeCall, Map.of(
                "approved", true,
                "feedback", "Confirm",
                "auto_confirm", false), null);
        assertTrue(resumeDecision.isApproved(), "Approved resume should continue tool execution");
        if (resumeDecision.isApproved()) {
            writeInvokeCount.incrementAndGet();
        }

        assertFalse(resumeDecision.isInterrupted(), "Should not interrupt after confirm");
        assertEquals(1, writeInvokeCount.get());
        assertEquals(writeCall, mockRuntimeModel.invoke());
        assertEquals("file written", mockRuntimeModel.stream().next());
    }

    static class MockRuntimeModel {
        private final Iterator<Object> responses;

        MockRuntimeModel(List<Object> responses) {
            this.responses = responses.iterator();
        }

        Object invoke() {
            return responses.hasNext() ? responses.next() : null;
        }

        Iterator<Object> stream() {
            return responses;
        }
    }
}
