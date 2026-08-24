/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.factory;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
