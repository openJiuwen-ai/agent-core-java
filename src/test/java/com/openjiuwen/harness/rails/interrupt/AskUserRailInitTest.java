/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.prompts.tools.HarnessPromptToolsPackage;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Python {@code AskUserRail.init} registers {@code harness.tools.AskUserTool}
 * so the model can call {@code ask_user}. Java previously only intercepted the name.
 */
class AskUserRailInitTest {

    @TempDir
    private Path tempDir;

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void initRegistersAskUserToolWithPromptMetadata() {
        agent = HarnessFactory.createDeepAgent(
                uniqueCard("ask-user-init"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .language("en")
                        .rails(List.of(new AskUserRail()))
                        .build(),
                null);

        assertThat(agent.getTools()).containsKey("ask_user");
        Tool tool = agent.getTools().get("ask_user");
        assertThat(tool).isInstanceOf(com.openjiuwen.harness.tools.AskUserTool.class);
        assertThat(tool.getCard().getDescription())
                .isEqualTo(HarnessPromptToolsPackage.getToolDescription("ask_user", "en"));
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(prefix).description("ask user rail init").build();
    }
}
