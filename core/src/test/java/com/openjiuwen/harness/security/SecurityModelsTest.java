/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's model behavior in
 * {@code openjiuwen/harness/security/models.py}.
 */
class SecurityModelsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testPermissionLevelRoundTrip() throws Exception {
        assertEquals(PermissionLevel.ALLOW, PermissionLevel.fromValue("allow"));
        assertEquals(PermissionLevel.ASK, PermissionLevel.fromValue("unexpected"));
        assertEquals("\"deny\"", mapper.writeValueAsString(PermissionLevel.DENY));
        assertEquals(PermissionLevel.ASK, mapper.readValue("\"ask\"", PermissionLevel.class));
    }

    @Test
    void testPermissionResultFlags() {
        PermissionResult result = new PermissionResult(
                PermissionLevel.DENY,
                "rules.block",
                "Denied by rule",
                List.of("/tmp/outside")
        );
        assertFalse(result.isAllowed());
        assertTrue(result.isDenied());
        assertFalse(result.needsApproval());
        assertEquals(List.of("/tmp/outside"), result.getExternalPaths());
    }

    @Test
    void testPermissionConfirmResponseDefaults() {
        PermissionConfirmResponse response = new PermissionConfirmResponse(true);
        assertTrue(response.isApproved());
        assertEquals("", response.getFeedback());
        assertFalse(response.isAutoConfirm());
    }

    @Test
    void testApprovalOverrideEntryUsesSnakeCaseJson() throws Exception {
        ApprovalOverrideEntry entry = mapper.readValue(
                """
                {
                  "id": "allow-git-status",
                  "tools": ["bash"],
                  "match_type": "command",
                  "pattern": "re:^git\\\\s+status$",
                  "action": "allow"
                }
                """,
                ApprovalOverrideEntry.class
        );
        assertEquals("allow-git-status", entry.getId());
        assertEquals(List.of("bash"), entry.getTools());
        assertEquals("command", entry.getMatchType());
        assertEquals("allow", entry.getAction());
    }

    @Test
    void testPermissionsSectionKeepsOptionalFieldsAndExtensions() throws Exception {
        PermissionsSection section = mapper.readValue(
                """
                {
                  "enabled": true,
                  "schema": "tiered_policy",
                  "defaults": {"*": "allow"},
                  "tools": {"read_file": "ask"},
                  "rules": [{"id": "deny_env", "tools": ["read_file"]}],
                  "approval_overrides": [{"id": "persist", "tools": ["bash"], "match_type": "command", "pattern": "re:^git", "action": "allow"}],
                  "external_directory": {"*": "ask"},
                  "permission_mode": "normal"
                }
                """,
                PermissionsSection.class
        );
        assertTrue(section.getEnabled());
        assertEquals("tiered_policy", section.getSchema());
        assertEquals("allow", section.getDefaults().get("*"));
        assertEquals("ask", section.getTools().get("read_file"));
        assertEquals(1, section.getRules().size());
        assertEquals(1, section.getApprovalOverrides().size());
        assertEquals("ask", section.getExternalDirectory().get("*"));
        assertEquals("normal", section.getExtensions().get("permission_mode"));
        assertNull(new PermissionResult(PermissionLevel.ALLOW).getExternalPaths());
        assertEquals(
                Map.of("permission_mode", "normal"),
                section.getExtensions()
        );
    }
}
