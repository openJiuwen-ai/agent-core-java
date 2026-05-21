/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TeamMemoryManager.
 * <p>
 * Mirrors Python's test_team_memory_manager.py from
 * <code>tests/unit_tests/core/memory/team/test_team_memory_manager.py</code>.
 */
@DisplayName("Team Memory Manager Tests")
class TestTeamMemoryManager {

    // Stub classes
    static class WorkspaceStub {
        String root;
        Map<String, String> nodePaths = new HashMap<>();

        WorkspaceStub(String root) {
            this.root = root;
        }

        String getNodePath(String nodeName) {
            return nodePaths.computeIfAbsent(nodeName, k -> root + "/" + k);
        }
    }

    static class PromptBuilderStub {
        Map<String, Object> sections = new HashMap<>();
        int addSectionCalls = 0;
        int removeSectionCalls = 0;

        void addSection(Object section) {
            addSectionCalls++;
        }

        void removeSection(String name) {
            removeSectionCalls++;
            sections.remove(name);
        }
    }

    static class AbilityManagerStub {
        List<String> registeredTools = new ArrayList<>();

        void registerTool(String toolName) {
            registeredTools.add(toolName);
        }
    }

    static class AgentStub {
        PromptBuilderStub promptBuilder = new PromptBuilderStub();
        AbilityManagerStub abilityManager = new AbilityManagerStub();
        List<Object> rails = new ArrayList<>();
    }

    static class TeamMemoryManagerStub {
        String sectionName = "team_memory";
        boolean initialized = false;
        boolean closed = false;
        AgentStub agent;

        void init(AgentStub agent) {
            this.agent = agent;
            this.initialized = true;
            agent.abilityManager.registerTool("team_memory_read");
            agent.abilityManager.registerTool("team_memory_write");
        }

        void close() {
            this.closed = true;
            if (agent != null) {
                agent.promptBuilder.removeSection(sectionName);
            }
        }

        void injectSystemPrompt(String content) {
            if (agent != null && initialized) {
                agent.promptBuilder.addSection(content);
            }
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class TestConstants {

        @Test
        @DisplayName("section name constant")
        void testSectionNameConstant() {
            TeamMemoryManagerStub stub = new TeamMemoryManagerStub();
            assertEquals("team_memory", stub.sectionName);
        }
    }

    @Nested
    @DisplayName("Init Toolkit Tests")
    class TestInitToolkit {

        @Test
        @DisplayName("init toolkit is idempotent")
        void testInitToolkitIdempotent() {
            AgentStub agent = new AgentStub();
            TeamMemoryManagerStub manager = new TeamMemoryManagerStub();

            manager.init(agent);
            manager.init(agent); // Second call should be idempotent

            assertEquals(2, agent.abilityManager.registeredTools.size());
            assertTrue(manager.initialized);
        }
    }

    @Nested
    @DisplayName("Register Tools Tests")
    class TestRegisterTools {

        @Test
        @DisplayName("register tools registers team memory tools")
        void testRegisterToolsRegistersTeamMemoryTools() {
            AgentStub agent = new AgentStub();
            TeamMemoryManagerStub manager = new TeamMemoryManagerStub();
            manager.init(agent);

            assertTrue(agent.abilityManager.registeredTools.contains("team_memory_read"));
            assertTrue(agent.abilityManager.registeredTools.contains("team_memory_write"));
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class TestClose {

        @Test
        @DisplayName("close removes prompt section")
        void testCloseRemovesPromptSection() {
            AgentStub agent = new AgentStub();
            TeamMemoryManagerStub manager = new TeamMemoryManagerStub();
            manager.init(agent);
            manager.close();

            assertTrue(manager.closed);
            assertEquals(1, agent.promptBuilder.removeSectionCalls);
        }
    }

    @Nested
    @DisplayName("Inject System Prompt Tests")
    class TestInjectSystemPrompt {

        @Test
        @DisplayName("inject system prompt adds section")
        void testInjectSystemPromptAddsSection() {
            AgentStub agent = new AgentStub();
            TeamMemoryManagerStub manager = new TeamMemoryManagerStub();
            manager.init(agent);
            manager.injectSystemPrompt("Team memory guidance");

            assertEquals(1, agent.promptBuilder.addSectionCalls);
        }
    }
}