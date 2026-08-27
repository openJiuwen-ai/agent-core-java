/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAgentToolErrorConfigTest {

    @TempDir
    Path baseDir;

    private DeepAgent newAgent(DeepAgentConfig config) {
        AgentCard card = AgentCard.builder().name("tool_error_agent").description("test").build();
        Workspace workspace = Workspace.builder().rootPath(baseDir.toString()).language("cn").build();
        return new DeepAgent(card, config, workspace);
    }

    private static boolean runtimeShouldFailTaskOnToolError(DeepAgent agent) {
        return ((ReActAgentConfig) agent.getAgent().getConfig()).isShouldFailTaskOnToolError();
    }

    @Test
    void defaultsToTrueWhenUnset() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(baseDir.toString())
                .build();
        DeepAgent agent = newAgent(config);
        assertThat(runtimeShouldFailTaskOnToolError(agent)).isTrue();
    }

    @Test
    void passedThroughWhenExplicitlyFalse() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(baseDir.toString())
                .shouldFailTaskOnToolError(false)
                .build();
        DeepAgent agent = newAgent(config);
        assertThat(runtimeShouldFailTaskOnToolError(agent)).isFalse();
    }

    @Test
    void passedThroughWhenExplicitlyTrue() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(baseDir.toString())
                .shouldFailTaskOnToolError(true)
                .build();
        DeepAgent agent = newAgent(config);
        assertThat(runtimeShouldFailTaskOnToolError(agent)).isTrue();
    }
}
