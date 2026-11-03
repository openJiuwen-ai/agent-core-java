/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventClassRegistryTest {

    @Test
    void createEventPopulatesSnakeCasePropertiesAndValidatesBuiltins() {
        BaseLogEvent event = EventClassRegistry.createEvent(
                LogEventType.AGENT_START,
                Map.of(
                        "trace_id", "trace-1",
                        "module_id", "agent",
                        "module_name", "agent",
                        "message", "hello"
                )
        );

        assertInstanceOf(AgentEvent.class, event);
        assertEquals("trace-1", event.getTraceId());
        assertEquals("agent", event.getModuleId());
        assertTrue(EventClassRegistry.validateEvent(event));
    }

    @Test
    void customEventRegistrySupportsRegisterAndUnregister() {
        EventClassRegistry.register("custom.test", BaseLogEvent::new);
        BaseLogEvent event = EventClassRegistry.createEvent("custom.test", Map.of("message", "payload"));

        assertEquals("custom.test", event.getEventTypeKey());
        assertEquals("payload", event.getMessage());
        assertTrue(EventClassRegistry.unregister("custom.test"));
        assertFalse(EventClassRegistry.unregister("custom.test"));
    }
}
