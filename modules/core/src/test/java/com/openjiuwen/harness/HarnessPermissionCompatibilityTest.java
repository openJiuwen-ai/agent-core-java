
package com.openjiuwen.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import com.openjiuwen.harness.security.PermissionCheckResult;
import com.openjiuwen.harness.security.PermissionEngine;
import com.openjiuwen.harness.security.PermissionFactory;
import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.ToolPermissionHost;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

class HarnessPermissionCompatibilityTest {
    private static Map<String, Object> permissions() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("schema", "tiered_policy");
        config.put("permission_mode", "normal");
        config.put("tools", Map.of("read_file", "ask", "write_file", "deny"));
        config.put("defaults", Map.of("*", "allow"));
        config.put("rules", java.util.List.of());
        config.put("approval_overrides", java.util.List.of());
        return config;
    }

    @Test
    void permissionEngineShouldEvaluateAllowAskDeny() {
        PermissionEngine engine = new PermissionEngine(permissions(), Path.of(".").toAbsolutePath());

        Map.Entry<PermissionLevel, String> ask =
            engine.evaluateGlobalPolicyDirectly("read_file", Map.of("path", "a.txt"));
        Map.Entry<PermissionLevel, String> deny =
            engine.evaluateGlobalPolicyDirectly("write_file", Map.of("path", "a.txt"));
        Map.Entry<PermissionLevel, String> allow = engine.evaluateGlobalPolicyDirectly("list_files", Map.of());
        PermissionCheckResult result = engine.checkPermission("read_file", Map.of("path", "a.txt"));

        assertThat(ask.getKey()).isEqualTo(PermissionLevel.ASK);
        assertThat(deny.getKey()).isEqualTo(PermissionLevel.DENY);
        assertThat(allow.getKey()).isEqualTo(PermissionLevel.ALLOW);
        assertThat(result.isNeedsApproval()).isTrue();
        assertThat(result.getMatchedRule()).isEqualTo("tools.read_file");
    }

    @Test
    void toolPermissionHostShouldExposeWorkspaceAndPersistAllowRule() {
        ToolPermissionHost host = ToolPermissionHost.builder().resolveWorkspaceDir(() -> Path.of("/tmp/workspace"))
                .permissionYamlPath(Path.of("/tmp/permissions.yaml"))
                .getPermissionsSnapshot(HarnessPermissionCompatibilityTest::permissions).build();

        Map<String, Object> persisted = host.persistAllowRule("bash", Map.of("command", "ls"));

        assertThat(host.resolveWorkspaceDir()).isEqualTo(Path.of("/tmp/workspace"));
        assertThat(host.permissionYamlPath()).isEqualTo(Path.of("/tmp/permissions.yaml"));
        assertThat(((Map<?, ?>) persisted.get("tools")).get("bash")).isEqualTo("allow");
    }

    @Test
    void permissionFactoryAndRailShouldCreateInterruptRail() {
        ToolPermissionHost host = ToolPermissionHost.builder().resolveWorkspaceDir(() -> Path.of(".")).build();
        PermissionInterruptRail rail =
            PermissionFactory.buildPermissionInterruptRail(permissions(), host, Path.of(".").toAbsolutePath());

        assertThat(rail.getEngine()).isNotNull();
        assertThat(rail.getHost()).isSameAs(host);
    }

    @Test
    void permissionRailShouldAllowDenyAndInterruptByPolicy() {
        PermissionInterruptRail rail = PermissionFactory.buildPermissionInterruptRail(permissions(),
                ToolPermissionHost.builder().build(), Path.of(".").toAbsolutePath());

        ToolCallInputs readInputs =
            ToolCallInputs.builder().toolCall(ToolCall.builder().id("tc1").name("read_file").arguments("{}").build())
                    .toolName("read_file").toolArgs(Map.of("path", "a.txt")).build();
        AgentCallbackContext readCtx =
            AgentCallbackContext.builder().inputs(readInputs).extra(new LinkedHashMap<>()).build();

        try {
            rail.beforeToolCall(readCtx);
        } catch (Exception ex) {
            assertThat(ex.getClass().getSimpleName()).contains("ToolInterruptException");
        }

        ToolCallInputs denyInputs =
            ToolCallInputs.builder().toolCall(ToolCall.builder().id("tc2").name("write_file").arguments("{}").build())
                    .toolName("write_file").toolArgs(Map.of("path", "a.txt")).build();
        AgentCallbackContext denyCtx =
            AgentCallbackContext.builder().inputs(denyInputs).extra(new LinkedHashMap<>()).build();
        rail.beforeToolCall(denyCtx);
        assertThat(denyCtx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);

        ToolCallInputs allowInputs =
            ToolCallInputs.builder().toolCall(ToolCall.builder().id("tc3").name("list_files").arguments("{}").build())
                    .toolName("list_files").toolArgs(Map.of()).build();
        AgentCallbackContext allowCtx =
            AgentCallbackContext.builder().inputs(allowInputs).extra(new LinkedHashMap<>()).build();
        rail.beforeToolCall(allowCtx);
        assertThat(allowCtx.getExtra()).doesNotContainKey("_skip_tool");
    }
}
