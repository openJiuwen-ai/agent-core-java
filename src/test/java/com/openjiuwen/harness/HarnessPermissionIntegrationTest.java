package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessPermissionIntegrationTest {

    private static Map<String, Object> permissions() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("tools", Map.of("read_file", "ask", "write_file", "deny"));
        config.put("defaults", Map.of("*", "allow"));
        return config;
    }

    private static ToolPermissionHost buildHost() {
        ToolPermissionHost host = new ToolPermissionHost();
        host.setWorkspaceDirResolver(() -> Path.of(".").toAbsolutePath());
        return host;
    }

    @Test
    void deepAgentShouldMountSecurityAndPermissionRailsWhenConfigured() {
        ToolPermissionHost host = buildHost();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("permission_demo").description("permission demo").build(),
                DeepAgentConfig.builder()
                        .workspacePath("./workspace")
                        .language("cn")
                        .build(),
                new Workspace("./workspace", "cn"),
                permissions(),
                host
        );

        agent.ensureInitialized();

        assertThat(agent.getRegisteredRails().stream().map(item -> item.getClass().getSimpleName()).toList())
                .contains("SecurityRail", "PermissionInterruptRail");
    }

    @Test
    void deepAgentInvokeShouldInitializePermissionRailsLazily() {
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("permission_demo").description("permission demo").build(),
                DeepAgentConfig.builder()
                        .workspacePath("./workspace")
                        .permissions(permissions())
                        .build(),
                new Workspace("./workspace", "cn")
        );

        agent.invoke(Map.of("query", "read file"));

        assertThat(agent.isInitialized()).isTrue();
        assertThat(agent.getRegisteredRails().stream().map(item -> item.getClass().getSimpleName()).toList())
                .contains("SecurityRail", "PermissionInterruptRail");
    }
}
