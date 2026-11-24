/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.factory;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessFactoryTest {

    @TempDir
    private Path tempDir;

    @Test
    void createDeepAgentPassesRestrictToWorkDirIntoLocalSysOperation() {
        DeepAgent agent = HarnessFactory.createDeepAgent(
                uniqueCard("restrict-on"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .restrictToWorkDir(true)
                        .build(),
                null);

        LocalWorkConfig workConfig = localWorkConfig(agent);
        assertThat(workConfig.isRestrictToSandbox()).isTrue();
    }

    @Test
    void createDeepAgentCanDisableSandboxRestrict() {
        DeepAgent agent = HarnessFactory.createDeepAgent(
                uniqueCard("restrict-off"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .restrictToWorkDir(false)
                        .build(),
                null);

        LocalWorkConfig workConfig = localWorkConfig(agent);
        assertThat(workConfig.isRestrictToSandbox()).isFalse();
    }

    @Test
    void failedCreationReleasesDynamicallyRegisteredTool() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Tool original = tool(resourceId, "original");
        assertThatThrownBy(() -> HarnessFactory.createDeepAgent(uniqueCard("failed"),
                config(new RegisterToolRail(original), new FailingContextRail()), null))
                .isInstanceOf(IllegalStateException.class).hasMessage("rail context binding failed");
        assertThat(Runner.resourceMgr().getTool(resourceId)).isNull();

        Tool replacement = tool(resourceId, "replacement");
        try (DeepAgent next = HarnessFactory.createDeepAgent(uniqueCard("next"),
                config(new RegisterToolRail(replacement)), null)) {
            assertThat(next.getTools()).containsKey("rollback_probe");
            assertThat(Runner.resourceMgr().getTool(resourceId).invoke(Map.of())).isEqualTo("replacement");
        }
        assertThat(Runner.resourceMgr().getTool(resourceId)).isNull();
    }

    @Test
    void failedCreationKeepsOtherOwnersTool() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Tool shared = tool(resourceId, "shared");
        try (DeepAgent live = HarnessFactory.createDeepAgent(uniqueCard("live"),
                config(new RegisterToolRail(shared)), null)) {
            assertThatThrownBy(() -> HarnessFactory.createDeepAgent(uniqueCard("failed-shared"),
                    config(new RegisterToolRail(tool(resourceId, "shared")), new FailingContextRail()), null))
                    .isInstanceOf(IllegalStateException.class).hasMessage("rail context binding failed");
            assertThat(live.getTools()).containsKey("rollback_probe");
            assertThat(Runner.resourceMgr().getTool(resourceId)).isSameAs(shared);
            assertThat(shared.invoke(Map.of())).isEqualTo("shared");
        }
        assertThat(Runner.resourceMgr().getTool(resourceId)).isNull();
    }

    private DeepAgentConfig config(DeepAgentRail... rails) {
        return DeepAgentConfig.builder().workspacePath(tempDir.toString()).rails(List.of(rails)).build();
    }

    private static Tool tool(String id, String description) {
        return new LocalFunction(ToolCard.builder().id(id).name("rollback_probe").description(description).build(),
                input -> description);
    }

    public static final class RegisterToolRail extends DeepAgentRail {
        private final Tool tool;

        RegisterToolRail(Tool tool) {
            this.tool = tool;
        }

        @Override
        public void init(DeepAgent agent) {
            super.init(agent);
            agent.registerHarnessTool(tool);
        }
    }

    public static final class FailingContextRail extends DeepAgentRail {
        @Override
        public void setWorkspace(Object workspace) {
            throw new IllegalStateException("rail context binding failed");
        }
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder()
                .id(id)
                .name(prefix)
                .description("factory restrict test")
                .build();
    }

    private static LocalWorkConfig localWorkConfig(DeepAgent agent) {
        SysOperation sysOperation = agent.getConfig().getSysOperation();
        assertThat(sysOperation).isNotNull();
        assertThat(sysOperation.getRunConfig()).isInstanceOf(LocalWorkConfig.class);
        return (LocalWorkConfig) sysOperation.getRunConfig();
    }
}
