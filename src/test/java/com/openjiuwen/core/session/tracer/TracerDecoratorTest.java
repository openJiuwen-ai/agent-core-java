/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracerDecoratorTest {

    interface TestTool {
        String invoke(String input);
    }

    static class TestToolImpl implements TestTool {
        @Override
        public String invoke(String input) {
            return "ok:" + input;
        }
    }

    @Test
    @DisplayName("decorateToolWithTrace supports AgentSessionApi wrappers")
    void decorateToolWithTraceSupportsWrappedSession() {
        TestTool decorated = TracerDecorator.decorateToolWithTrace(new TestToolImpl(), new AgentSessionApi());

        assertTrue(Proxy.isProxyClass(decorated.getClass()));
        assertEquals("ok:ping", decorated.invoke("ping"));
    }

    @Test
    @DisplayName("decorateToolWithTrace supports direct AgentSession instances")
    void decorateToolWithTraceSupportsDirectInnerSession() {
        AgentSession session = new AgentSession("session-1", new Config());
        TestTool decorated = TracerDecorator.decorateToolWithTrace(new TestToolImpl(), session);

        assertTrue(Proxy.isProxyClass(decorated.getClass()));
        assertEquals("ok:ping", decorated.invoke("ping"));
    }
}
