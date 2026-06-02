/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/system_tests/harness/test_coding_memory_rail_e2e.py}.
 */
@Tag("system-test")
class TestCodingMemoryRailE2e {

    @TempDir
    Path tempDir;

    private Path codingMemoryDir;

    @BeforeEach
    void setUp() throws IOException {
        Runner.start();
        codingMemoryDir = tempDir.resolve("coding_memory");
        Files.createDirectories(codingMemoryDir);
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("full invoke flow initializes tools, recalls memories, and injects prompt")
    void testFullInvokeFlow() throws IOException {
        writeMemory("python_pref.md", "Python Preference", "User prefers Python programming.");
        FakeAgent agent = new FakeAgent();
        CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir.toString(), new Object(), "cn");

        rail.init(agent);
        rail.setMemoryManager(new FakeMemoryManager(List.of(Map.of("path", "python_pref.md", "score", 0.9))));
        rail.prefetch("Python");
        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).build());

        assertTrue(rail.isManagerInitialized());
        assertTrue(agent.abilityManager.addedToolNames.containsAll(List.of(
                "coding_memory_read", "coding_memory_write", "coding_memory_edit")));
        assertNotNull(rail.getRecalledContent());
        assertTrue(rail.getRecalledContent().contains("Python Preference"));
        assertNotNull(agent.promptBuilder.lastSection);
        assertTrue(agent.promptBuilder.lastSection.render("cn").contains("Python Preference"));
    }

    @Test
    @DisplayName("auto recall returns formatted content for matching memory files")
    void testAutoRecallWithResults() throws IOException {
        writeMemory("python_pref.md", "Python Preference", "User prefers Python programming.");
        CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir.toString(), new Object(), "cn");
        rail.setMemoryManager(new FakeMemoryManager(List.of(Map.of("path", "python_pref.md", "score", 0.9))));

        CodingMemoryRail.RecallResult result = rail.autoRecall("Python");

        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("Python Preference"));
        assertTrue(result.getContent().contains("User prefers Python"));
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("auto recall returns empty result when manager finds nothing")
    void testAutoRecallNoResults() {
        CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir.toString(), new Object(), "cn");
        rail.setMemoryManager(new FakeMemoryManager(List.of()));

        CodingMemoryRail.RecallResult result = rail.autoRecall("UnknownQuery");

        assertNull(result.getContent());
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("beforeModelCall injects recalled memory content")
    void testBeforeModelCallWithRecallResults() {
        FakeAgent agent = new FakeAgent();
        CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir.toString(), new Object(), "cn");
        rail.init(agent);
        rail.setRecalledContent("### Test Memory\n\nTest content");

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).build());

        assertNotNull(agent.promptBuilder.lastSection);
        String rendered = agent.promptBuilder.lastSection.render("cn");
        assertTrue(rendered.contains("Test Memory"));
        assertTrue(rendered.contains("Test content"));
        assertFalse(rendered.isBlank());
    }

    @Test
    @DisplayName("memory scenario selection matches Python helper semantics")
    void testScenarioSwitching() {
        assertEquals("personal", getMemoryScenario(Map.of("memory", Map.of("scenario", "personal"))));
        assertEquals("coding", getMemoryScenario(Map.of("memory", Map.of("scenario", "coding"))));
        assertEquals("personal", getMemoryScenario(Map.of("memory", Map.of())));
        assertEquals("coding", getMemoryScenario(Map.of("memory", Map.of("scenario", "CODING"))));
    }

    private void writeMemory(String filename, String name, String body) throws IOException {
        String content = """
                ---
                name: %s
                description: test memory
                type: user
                ---

                %s
                """.formatted(name, body);
        Files.writeString(codingMemoryDir.resolve(filename), content);
    }

    @SuppressWarnings("unchecked")
    private static String getMemoryScenario(Map<String, Object> config) {
        Map<String, Object> memoryCfg = (Map<String, Object>) config.getOrDefault("memory", Map.of());
        String scenario = String.valueOf(memoryCfg.getOrDefault("scenario", "personal")).trim().toLowerCase();
        return "coding".equals(scenario) ? "coding" : "personal";
    }

    public static final class FakeAgent {
        private final FakeAbilityManager abilityManager = new FakeAbilityManager();
        private final FakePromptBuilder promptBuilder = new FakePromptBuilder();

        public FakeAbilityManager getAbilityManager() {
            return abilityManager;
        }

        public FakePromptBuilder getSystemPromptBuilder() {
            return promptBuilder;
        }
    }

    public static final class FakeAbilityManager {
        private final List<String> addedToolNames = new ArrayList<>();
        private final List<String> removedToolNames = new ArrayList<>();

        public Object add(Object card) {
            if (card instanceof ToolCard toolCard) {
                addedToolNames.add(toolCard.getName());
            }
            return new Object();
        }

        public void remove(String name) {
            removedToolNames.add(name);
        }
    }

    public static final class FakePromptBuilder {
        private PromptSection lastSection;
        private final List<String> removedSections = new ArrayList<>();

        public void addSection(PromptSection section) {
            this.lastSection = section;
        }

        public void removeSection(String name) {
            removedSections.add(name);
        }
    }

    public static final class FakeMemoryManager {
        private final List<Map<String, Object>> results;

        FakeMemoryManager(List<Map<String, Object>> results) {
            this.results = new ArrayList<>();
            for (Map<String, Object> result : results) {
                this.results.add(new LinkedHashMap<>(result));
            }
        }

        public List<Map<String, Object>> search(String query, Map<String, Object> options) {
            return results;
        }
    }
}
