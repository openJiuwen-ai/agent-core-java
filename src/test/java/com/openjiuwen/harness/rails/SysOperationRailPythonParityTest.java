/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.FilesystemTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/test_filesystem_rail.py}.
 */
class SysOperationRailPythonParityTest {

    @TempDir
    private Path workspace;

    @Test
    void sysOperationRailRegistersBaseTools() {
        withRail("test_sys_operation_rail_base_tools", new SysOperationRail(), true, agent -> {
            Set<String> toolNames = toolNames(agent.getAbilityManager());

            assertThat(toolNames).contains("read_file", "write_file", "edit_file", "glob", "list_files", "grep", "bash");
            if (isWindows()) {
                assertThat(toolNames).contains("powershell");
            } else {
                assertThat(toolNames).doesNotContain("powershell");
            }
            assertThat(toolNames)
                    .doesNotContain("code", "audio_transcription", "audio_question_answering", "audio_metadata",
                            "image_ocr", "visual_question_answering");
        });
    }

    @Test
    void sysOperationRailReadOnly() {
        withRail("test_sys_operation_rail_read_only", new SysOperationRail(false, true), true, agent -> {
            Set<String> toolNames = toolNames(agent.getAbilityManager());

            assertThat(toolNames).contains("read_file", "glob", "list_files", "grep", "bash");
            assertThat(toolNames).doesNotContain("write_file", "edit_file", "code");
        });
    }

    @Test
    void sysOperationRailWithCodeTool() {
        withRail("test_sys_operation_rail_with_code_tool", new SysOperationRail(true, false), true, agent ->
                assertThat(toolNames(agent.getAbilityManager())).contains("code"));
    }

    @Test
    void sysOperationRailAppliesReadImageMultimodalConfig() {
        SysOperationRail rail = new SysOperationRail();
        withRail("test_sys_operation_rail_read_image_multimodal_config", rail, false, agent -> {
            Tool readTool = rail.getTools().stream()
                    .filter(FilesystemTools.ReadFileTool.class::isInstance)
                    .findFirst()
                    .orElseThrow();

            assertThat(((FilesystemTools.ReadFileTool) readTool).isEnableImageMultimodal()).isFalse();
        });
    }

    private void withRail(String cardId, SysOperationRail rail, boolean enableReadImageMultimodal, RailAssertion assertion) {
        Runner.start().toCompletableFuture().join();
        SysOperationCard card = new SysOperationCard(cardId, OperationMode.LOCAL, new LocalWorkConfig());
        Runner.resourceMgr().addSysOperation(card);
        SysOperation sysOperation = Runner.resourceMgr().getSysOperation(card.getId());
        rail.setSysOperation(sysOperation);
        DeepAgent agent = new DeepAgent(new AgentCard(cardId + "_agent", cardId + "_agent", "test agent"));
        DeepAgentConfig config = new DeepAgentConfig();
        config.setLanguage("en");
        config.setWorkspace(workspace.toString());
        config.setEnableReadImageMultimodal(enableReadImageMultimodal);
        agent.configure(config);

        try {
            rail.init(agent);
            assertion.verify(agent);
        } finally {
            rail.uninit(agent);
            Runner.resourceMgr().removeSysOperation(card.getId());
            Runner.stop().toCompletableFuture().join();
        }
    }

    private static Set<String> toolNames(AbilityManager abilityManager) {
        return abilityManager.getTools().values().stream()
                .map(card -> card.getName())
                .collect(Collectors.toSet());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @FunctionalInterface
    private interface RailAssertion {
        void verify(DeepAgent agent);
    }
}
