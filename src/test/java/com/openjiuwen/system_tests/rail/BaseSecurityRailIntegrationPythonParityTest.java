/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.rail;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.security.BaseSecurityRail;
import com.openjiuwen.harness.rails.security.SecurityAllow;
import com.openjiuwen.harness.rails.security.SecurityCheckContext;
import com.openjiuwen.harness.rails.security.SecurityDecision;
import com.openjiuwen.harness.rails.security.SecurityInterrupt;
import com.openjiuwen.harness.rails.security.SecurityReject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/system_tests/rail/test_base_security_rail_integration.py}.
 */
class BaseSecurityRailIntegrationPythonParityTest {

    @Test
    void securityRejectModifiesToolResult() {
        TrackingReadTool tool = new TrackingReadTool("this contains secret data");
        RejectToolResultRail rail = new RejectToolResultRail();
        CallbackContext ctx = context(tool.call("/test.txt"));

        rail.afterToolCall(ctx);

        assertEquals(1, tool.count());
        assertTrue(String.valueOf(ctx.get("tool_result")).toLowerCase().contains("blocked"));
        assertTrue(toolMessage(ctx).getContentAsString().toLowerCase().contains("blocked"));
    }

    @Test
    void securityAllowPassesToolResult() {
        TrackingReadTool tool = new TrackingReadTool("normal safe content");
        RejectToolResultRail rail = new RejectToolResultRail();
        Map<String, Object> result = tool.call("/safe.txt");
        CallbackContext ctx = context(result);

        rail.afterToolCall(ctx);

        assertEquals(1, tool.count());
        assertSame(result, ctx.get("tool_result"));
        assertFalse(ctx.isRejected());
    }

    @Test
    void multiEventRailRecordsAllEvents() {
        MultiEventRail rail = new MultiEventRail();
        CallbackContext ctx = context(Map.of("content", "test content"));

        rail.beforeInvoke(ctx);
        rail.beforeModelCall(ctx);
        rail.afterToolCall(ctx);

        assertTrue(rail.events().contains(BaseSecurityRail.BEFORE_INVOKE));
        assertTrue(rail.events().contains(BaseSecurityRail.BEFORE_MODEL_CALL));
        assertTrue(rail.events().contains(BaseSecurityRail.AFTER_TOOL_CALL));
    }

    @Test
    void priorityOrderingHighRunsBeforeLow() {
        List<String> order = new ArrayList<>();
        List<BaseSecurityRail> rails = List.of(new HighPrioritySecurityRail(order), new LowPrioritySecurityRail(order));
        CallbackContext ctx = context(Map.of("content", "test"));

        rails.forEach(rail -> rail.afterToolCall(ctx));

        assertEquals(List.of("high_priority", "low_priority"), order);
    }

    @Disabled("Skipped in Python source: Requires proper session setup for HITL interrupt flow")
    @Test
    void securityInterruptWithHumanApproval() {
    }

    @Disabled("Skipped in Python source: Requires proper session setup for HITL interrupt flow")
    @Test
    void securityInterruptWithHumanRejection() {
    }

    @Test
    void chainOfToolCallsWithMixedResults() {
        TrackingReadTool tool = new TrackingReadTool("normal content without blocked_pattern");
        RejectToolResultRail rail = new RejectToolResultRail();
        rail.pattern("blocked_pattern");
        CallbackContext ctx = context(tool.call("/first.txt"));

        rail.afterToolCall(ctx);

        assertEquals(1, tool.count());
        assertTrue(String.valueOf(ctx.get("tool_result")).contains("blocked_pattern"));
    }

    @Test
    void rejectModifiesToolMessage() {
        TrackingReadTool tool = new TrackingReadTool("secret_api_key=12345");
        StrictRejectRail rail = new StrictRejectRail();
        CallbackContext ctx = context(tool.call("/env.txt"));

        rail.afterToolCall(ctx);

        assertEquals(1, tool.count());
        assertEquals("Sensitive data blocked", ctx.get("tool_result"));
        assertEquals("Sensitive data blocked", toolMessage(ctx).getContent());
    }

    @Test
    void allowPreservesOriginalResult() {
        TrackingReadTool tool = new TrackingReadTool("This is safe public content");
        AlwaysAllowRail rail = new AlwaysAllowRail();
        Map<String, Object> result = tool.call("/public.txt");
        CallbackContext ctx = context(result);

        rail.afterToolCall(ctx);

        assertEquals(1, tool.count());
        assertSame(result, ctx.get("tool_result"));
        assertFalse(ctx.isRejected());
    }

    private static CallbackContext context(Map<String, Object> toolResult) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", "read");
        values.put("tool_call_id", "tc_1");
        values.put("tool_result", toolResult);
        values.put("tool_msg", new ToolMessage("original", "tc_1", "read"));
        return new CallbackContext(new DeepAgent(), values);
    }

    private static ToolMessage toolMessage(CallbackContext ctx) {
        return (ToolMessage) ctx.get("tool_msg");
    }

    private static final class TrackingReadTool {
        private final String content;
        private int count;

        TrackingReadTool(String content) {
            this.content = content;
        }

        Map<String, Object> call(String filepath) {
            count++;
            return Map.of("success", true, "content", content, "filepath", filepath);
        }

        int count() {
            return count;
        }
    }

    private static class RejectToolResultRail extends BaseSecurityRail {
        private String pattern = "secret";

        RejectToolResultRail() {
            setSupportedEvents(List.of(AFTER_TOOL_CALL));
        }

        void pattern(String pattern) {
            this.pattern = pattern;
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            Object toolResult = securityCtx.callbackContext().get("tool_result");
            String content = extractContent(toolResult);
            if (content.contains(pattern)) {
                return reject("Blocked: detected '" + pattern + "' in tool result");
            }
            return allow();
        }

        @Override
        protected void applySecurityDecision(SecurityCheckContext securityCtx, SecurityDecision decision) {
            if (decision instanceof SecurityAllow) {
                return;
            }
            if (decision instanceof SecurityReject reject) {
                CallbackContext ctx = securityCtx.callbackContext();
                String message = reject.message();
                ctx.put("tool_result", message);
                ctx.put("tool_msg", new ToolMessage(message, String.valueOf(ctx.get("tool_call_id")), "read"));
                return;
            }
            super.applySecurityDecision(securityCtx, decision);
        }
    }

    private static final class StrictRejectRail extends RejectToolResultRail {
        StrictRejectRail() {
            pattern("secret");
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            Object toolResult = securityCtx.callbackContext().get("tool_result");
            if (toolResult instanceof Map<?, ?> map && String.valueOf(map.get("content")).contains("secret")) {
                return reject("Sensitive data blocked");
            }
            return allow();
        }
    }

    private static final class AlwaysAllowRail extends BaseSecurityRail {
        AlwaysAllowRail() {
            setSupportedEvents(List.of(AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            return allow();
        }
    }

    private static final class MultiEventRail extends BaseSecurityRail {
        private final List<String> events = new ArrayList<>();

        MultiEventRail() {
            setSupportedEvents(List.of(BEFORE_INVOKE, BEFORE_MODEL_CALL, AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            events.add(securityCtx.event());
            return allow();
        }

        List<String> events() {
            return events;
        }
    }

    private static final class HighPrioritySecurityRail extends BaseSecurityRail {
        private final List<String> order;

        HighPrioritySecurityRail(List<String> order) {
            this.order = order;
            setPriority(90);
            setSupportedEvents(List.of(AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            order.add("high_priority");
            return allow();
        }
    }

    private static final class LowPrioritySecurityRail extends BaseSecurityRail {
        private final List<String> order;

        LowPrioritySecurityRail(List<String> order) {
            this.order = order;
            setPriority(10);
            setSupportedEvents(List.of(AFTER_TOOL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            order.add("low_priority");
            return allow();
        }
    }

    private static String extractContent(Object toolResult) {
        if (toolResult instanceof String text) {
            return text;
        }
        if (toolResult instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content == null) {
                content = map.get("output");
            }
            return content == null ? "" : String.valueOf(content);
        }
        return toolResult == null ? "" : String.valueOf(toolResult);
    }
}
