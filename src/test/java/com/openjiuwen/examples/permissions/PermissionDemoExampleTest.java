/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.permissions;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionResult;
import com.openjiuwen.harness.security.ToolPermissionHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionDemoExampleTest {

    @TempDir
    Path workspace;

    @Test
    void examplePermissionsDictMatchesPythonPolicy() {
        Map<String, Object> permissions = PermissionDemoExample.examplePermissionsDict();

        assertEquals(true, permissions.get("enabled"));
        assertEquals("tiered_policy", permissions.get("schema"));
        assertEquals("normal", permissions.get("permission_mode"));
        assertEquals("ask", ((Map<?, ?>) permissions.get("tools")).get("read_file"));
        assertEquals("deny", ((Map<?, ?>) permissions.get("tools")).get("write_file"));
        assertEquals("allow", ((Map<?, ?>) permissions.get("defaults")).get("*"));
        assertTrue(((java.util.List<?>) permissions.get("rules")).isEmpty());
        assertTrue(((java.util.List<?>) permissions.get("approval_overrides")).isEmpty());
    }

    @Test
    void examplePermissionHostResolvesWorkspaceAndConfigPath() {
        Path yaml = workspace.resolve("openjiuwen_permission_demo_config.yaml");

        ToolPermissionHost host = PermissionDemoExample.examplePermissionHost(workspace, yaml);

        assertNotNull(host.getResolveWorkspaceDir());
        assertEquals(workspace.toAbsolutePath().normalize(), host.getResolveWorkspaceDir().get());
        assertEquals(yaml, host.getPermissionYamlPath());
    }

    @Test
    void syncAndAsyncPermissionChecksAskForReadFile() throws ExecutionException, InterruptedException {
        PermissionResult sync = PermissionDemoExample.demoSyncPermissionEngine(workspace);
        PermissionResult async = PermissionDemoExample.demoAsyncCheckPermission(workspace).get();

        assertEquals(PermissionLevel.ASK, sync.getPermission());
        assertEquals("tools.read_file", sync.getMatchedRule());
        assertTrue(sync.needsApproval());
        assertEquals(PermissionLevel.ASK, async.getPermission());
        assertEquals(sync.getMatchedRule(), async.getMatchedRule());
    }

    @Test
    void standaloneRailFactoryBuildsPermissionInterruptRail() {
        PermissionInterruptRail rail = PermissionDemoExample.demoStandaloneRailFactory(workspace);

        assertNotNull(rail);
        assertInstanceOf(PermissionInterruptRail.class, rail);
    }

    @Test
    void deepAgentMountsSecurityAndPermissionRails() {
        DeepAgent agent = PermissionDemoExample.demoDeepAgentMountsRails(workspace);

        assertInstanceOf(DeepAgentConfig.class, agent.getConfig());
        DeepAgentConfig config = (DeepAgentConfig) agent.getConfig();
        assertEquals("permission_demo", config.getCard().getName());
        assertEquals(3, config.getMaxIterations());
        assertEquals(workspace.toAbsolutePath().normalize().toString(), config.getWorkspace().getRootPath());
        assertTrue(PermissionDemoExample.mountedRailNames(agent).contains("SecurityRail"));
        assertTrue(PermissionDemoExample.mountedRailNames(agent).contains("PermissionInterruptRail"));
    }

    @Test
    void naturalLanguageDemoGateFollowsApiKeyPresence() {
        assertFalse(PermissionDemoExample.shouldRunNaturalLanguageDemo(Map.of()));
        assertFalse(PermissionDemoExample.shouldRunNaturalLanguageDemo(Map.of("API_KEY", " ")));
        assertTrue(PermissionDemoExample.shouldRunNaturalLanguageDemo(Map.of("API_KEY", "sk-demo")));
    }

    @Test
    void summarizeDictResultMatchesPythonDisplayShape() {
        List<String> lines = PermissionDemoExample.summarizeDictResult("[NL] first", Map.of(
                "result_type", "interrupt",
                "output", "ok",
                "state", List.of("s1", "s2")
        ));

        assertEquals("[NL] first result_type=interrupt keys=[output, result_type, state]", lines.get(0));
        assertEquals("[NL] first   output: ok", lines.get(1));
        assertEquals("[NL] first   state: len=2", lines.get(2));
    }

    @Test
    void runDemoExecutesDeterministicPermissionFlow() throws Exception {
        PermissionDemoExample.DemoRunSummary summary = PermissionDemoExample.runDemo(workspace, Map.of());

        assertEquals(workspace.toAbsolutePath().normalize(), summary.workspace());
        assertEquals(PermissionLevel.ASK, summary.syncResult().getPermission());
        assertEquals(PermissionLevel.ASK, summary.asyncResult().getPermission());
        assertEquals("PermissionInterruptRail", summary.permissionRailName());
        assertTrue(summary.mountedRailNames().contains("SecurityRail"));
        assertEquals("skipped", summary.naturalLanguageResult().status());
        assertTrue(java.nio.file.Files.exists(workspace.resolve("notes.txt")));
    }
}
