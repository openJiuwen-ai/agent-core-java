/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import com.openjiuwen.harness.security.PermissionInterruptRailFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests.system_tests.harness.rail.test_deep_agent_tool_permission_interrupt} in
 * {@code tests/system_tests/harness/rail/test_deep_agent_tool_permission_interrupt.py}.
 */
class DeepAgentToolPermissionInterruptMissingTest {
    private static final String TOOL_NAME = "read_file";

    @TempDir
    Path workspaceRoot;

    @Test
    void hitlToolPermissionInterruptReadFileAsk() throws IOException {
        String fileName = "hello_permission_st.txt";
        Files.writeString(workspaceRoot.resolve(fileName), "permission-system-test\n", StandardCharsets.UTF_8);
        PermissionInterruptRail rail = permissionRail();
        String toolCallId = "tool_call_read_perm_001";

        CallbackContext interrupted = runRail(rail, toolCallId, fileName, null);
        Map<String, Object> request = assertInterruptedReadFile(interrupted, fileName);
        assertEquals(TOOL_NAME, request.get("auto_confirm_key"));

        CallbackContext resumed = runRail(
                rail,
                toolCallId,
                fileName,
                Map.of("approved", true, "feedback", "", "auto_confirm", false)
        );
        assertApprovedOnce(resumed);
    }

    @Test
    void hitlToolPermissionInterruptResumeWithConfirmPayloadObject() throws IOException {
        String fileName = "hello_permission_st_obj.txt";
        Files.writeString(workspaceRoot.resolve(fileName), "permission-object-resume\n", StandardCharsets.UTF_8);
        PermissionInterruptRail rail = permissionRail();
        String toolCallId = "tool_call_read_perm_002";

        assertInterruptedReadFile(runRail(rail, toolCallId, fileName, null), fileName);

        CallbackContext resumed = runRail(
                rail,
                toolCallId,
                fileName,
                new ConfirmInterruptRail.ConfirmPayload(true, "", false)
        );
        assertApprovedOnce(resumed);
    }

    private PermissionInterruptRail permissionRail() {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("enabled", true);
        permissions.put("defaults", Map.of("*", "allow"));
        permissions.put("tools", Map.of(TOOL_NAME, Map.of("*", "ask")));
        return PermissionInterruptRailFactory.buildPermissionInterruptRail(
                permissions,
                null,
                null,
                null,
                null,
                workspaceRoot
        );
    }

    private static CallbackContext runRail(
            PermissionInterruptRail rail,
            String toolCallId,
            String fileName,
            Object resumePayload
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", TOOL_NAME);
        values.put("tool_args", Map.of("file_path", fileName));
        values.put("tool_call_id", toolCallId);
        if (resumePayload != null) {
            values.put("resume_user_input", Map.of(toolCallId, resumePayload));
        }
        CallbackContext ctx = new CallbackContext(null, values);
        rail.beforeToolCall(ctx);
        return ctx;
    }

    private static Map<String, Object> assertInterruptedReadFile(CallbackContext ctx, String fileName) {
        assertTrue(ctx.isRejected());
        assertEquals("Security approval required.", ctx.getRejectionMessage());
        Map<String, Object> request = asMap(ctx.get("security_interrupt_request"));
        assertEquals(TOOL_NAME, request.get("tool_name"));
        assertEquals(fileName, asMap(request.get("tool_args")).get("file_path"));
        Map<String, Object> properties = asMap(asMap(request.get("payload_schema")).get("properties"));
        assertTrue(properties.containsKey("approved"));
        assertTrue(properties.containsKey("feedback"));
        assertTrue(properties.containsKey("auto_confirm"));
        return request;
    }

    private static void assertApprovedOnce(CallbackContext ctx) {
        assertFalse(ctx.isRejected());
        assertEquals(
                Map.of("approved", true, "auto_confirm", false, "persisted", false),
                ctx.get("permission_confirmed")
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertTrue(value instanceof Map<?, ?>);
        return (Map<String, Object>) value;
    }
}
