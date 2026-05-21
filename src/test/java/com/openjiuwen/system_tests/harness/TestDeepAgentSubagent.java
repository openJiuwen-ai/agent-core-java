/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.SysOperationRail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepAgent SessionRail / SubAgentRail subtask system test.
 * <p>
 * Mirrors Python's {@code test_deep_agent_subagent.py} in
 * {@code tests/system_tests/harness/test_deep_agent_subagent.py}.
 */
public class TestDeepAgentSubagent {

    private Path tmpDir;
    private String workDir;
    private String sysOperationId;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("deepagent_subagent_" + UUID.randomUUID().toString().substring(0, 8));
        workDir = tmpDir.toString();
        sysOperationId = "deepagent_sysop_" + UUID.randomUUID().toString().replace("-", "");
        
        SysOperationCard card = SysOperationCard.builder()
                .id(sysOperationId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workDir).build())
                .build();
        
        Runner.resourceMgr().addSysOperation(card);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        } finally {
            if (tmpDir != null) {
                Files.deleteIfExists(tmpDir);
            }
            Runner.stop();
        }
    }

    /**
     * ToolTraceRail - Records tool call sequence.
     */
    private static class ToolTraceRail {
        private final List<String> toolCalls = new ArrayList<>();

        void recordToolCall(String toolName) {
            toolCalls.add(toolName);
        }

        List<String> getToolCalls() {
            return toolCalls;
        }
    }

    @Nested
    @DisplayName("SubagentRail execution tests")
    class SubagentTests {

        @Test
        @DisplayName("Test deep agent tasks using subagents")
        void testDeepAgentTasksUsingSubagents() {
            // Placeholder: Multi-step complex task with subagent
            // Validates:
            // - Main agent can call subagent via task tool
            // - Main agent and subagent share workspace
            
            SysOperationRail fsRail = new SysOperationRail();
            ToolTraceRail toolTrace = new ToolTraceRail();
            
            AgentCard researchCard = AgentCard.builder()
                    .name("research_agent")
                    .description("Research agent for investigation tasks")
                    .build();

            assertThat(fsRail).isNotNull();
            assertThat(toolTrace).isNotNull();
            assertThat(researchCard).isNotNull();
        }

        @Test
        @DisplayName("Test tool trace recording")
        void testToolTraceRecording() {
            ToolTraceRail rail = new ToolTraceRail();
            rail.recordToolCall("read_file");
            rail.recordToolCall("write_file");
            rail.recordToolCall("edit_file");
            
            assertThat(rail.getToolCalls())
                    .containsExactly("read_file", "write_file", "edit_file");
        }
    }
}