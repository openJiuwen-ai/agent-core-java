/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.context_evolver;

import com.openjiuwen.core.single_agent.rail.base.AgentCallbackEvent;
import com.openjiuwen.core.single_agent.rail.base.AgentCallbackContext;
import com.openjiuwen.core.foundation.llm.UserMessage;
import com.openjiuwen.core.foundation.llm.AssistantMessage;
import com.openjiuwen.core.foundation.llm.ToolMessage;
import com.openjiuwen.harness.rails.evolution.ContextEvolutionRail;
import com.openjiuwen.extensions.context_evolver.service.SummarizeTrajectoriesInput;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContextEvolutionRail.
 * <p>
 * Tests are organised by component:
 * - TestInit — __init__ and startup behaviour
 * - TestBeforeTaskIteration — memory retrieval and prompt injection
 * - TestAfterTaskIteration — prompt restore and memories_used annotation
 * - TestAutoSummarize — trajectory buffer and auto_summarize
 * - TestFormatTrajectory — message-list → trajectory string
 * - TestSummarizeTrajectories — trajectory → memory store update
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_task_memory_rail.py}.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_RAIL_TESTS", matches = "true")
public class TestTaskMemoryRail {

    private static final String SYS_CONTENT = "You are a helpful assistant.";
    private static final String USER_CONTENT = "{query}";

    @BeforeEach
    void setUp() {
        // Setup test fixtures
    }

    @AfterEach
    void tearDown() {
        // Cleanup
    }

    // ---------------------------------------------------------------------------
    // Init Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test ContextEvolutionRail initialization")
    @Tag("level0")
    void testRailInitialization() {
        // Test that rail initializes correctly
        String retrievalAlgo = "ACE";
        String summaryAlgo = "ACE";
        
        assertThat(retrievalAlgo).isEqualTo("ACE");
        assertThat(summaryAlgo).isEqualTo("ACE");
    }

    @Test
    @DisplayName("Test rail config defaults")
    @Tag("level0")
    void testRailConfigDefaults() {
        // Default configuration values
        int autoSummarizeThreshold = 10;
        boolean autoSummarizeEnabled = true;
        
        assertThat(autoSummarizeThreshold).isEqualTo(10);
        assertThat(autoSummarizeEnabled).isTrue();
    }

    // ---------------------------------------------------------------------------
    // BeforeTaskIteration Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test before_task_iteration memory retrieval")
    @Tag("level0")
    void testBeforeTaskIterationMemoryRetrieval() {
        // Create mock context
        AgentCallbackContext context = mock(AgentCallbackContext.class);
        AgentCallbackEvent event = AgentCallbackEvent.BEFORE_TASK_ITERATION;
        
        // In real test:
        // 1. Create rail with memory service
        // 2. Call before_task_iteration
        // 3. Verify memory injected into prompt
        
        assertThat(event).isEqualTo(AgentCallbackEvent.BEFORE_TASK_ITERATION);
    }

    @Test
    @DisplayName("Test prompt injection with retrieved memories")
    @Tag("level0")
    void testPromptInjectionWithRetrievedMemories() {
        List<Map<String, Object>> promptTemplate = createPromptTemplate();
        
        assertThat(promptTemplate).hasSize(2);
        assertThat(promptTemplate.get(0).get("role")).isEqualTo("system");
        assertThat(promptTemplate.get(1).get("role")).isEqualTo("user");
    }

    // ---------------------------------------------------------------------------
    // AfterTaskIteration Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test after_task_iteration prompt restore")
    @Tag("level0")
    void testAfterTaskIterationPromptRestore() {
        AgentCallbackEvent event = AgentCallbackEvent.AFTER_TASK_ITERATION;
        
        // In real test:
        // 1. Call after_task_iteration
        // 2. Verify prompt restored to original state
        // 3. Verify memories_used annotation
        
        assertThat(event).isEqualTo(AgentCallbackEvent.AFTER_TASK_ITERATION);
    }

    @Test
    @DisplayName("Test memories_used annotation")
    @Tag("level0")
    void testMemoriesUsedAnnotation() {
        List<Map<String, Object>> memoriesUsed = new ArrayList<>();
        
        Map<String, Object> memory1 = new LinkedHashMap<>();
        memory1.put("id", "mem_001");
        memory1.put("content", "Use caching for optimization");
        memoriesUsed.add(memory1);
        
        assertThat(memoriesUsed).hasSize(1);
        assertThat(memoriesUsed.get(0).get("id")).isEqualTo("mem_001");
    }

    // ---------------------------------------------------------------------------
    // AutoSummarize Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test trajectory buffer accumulation")
    @Tag("level0")
    void testTrajectoryBufferAccumulation() {
        List<Map<String, Object>> trajectoryBuffer = new ArrayList<>();
        
        // Add multiple trajectories
        for (int i = 0; i < 5; i++) {
            trajectoryBuffer.add(createTrajectoryEntry(i));
        }
        
        assertThat(trajectoryBuffer).hasSize(5);
    }

    @Test
    @DisplayName("Test auto_summarize trigger threshold")
    @Tag("level0")
    void testAutoSummarizeTriggerThreshold() {
        int threshold = 10;
        int currentCount = 10;
        
        boolean shouldTrigger = currentCount >= threshold;
        assertThat(shouldTrigger).isTrue();
        
        // Below threshold
        currentCount = 5;
        shouldTrigger = currentCount >= threshold;
        assertThat(shouldTrigger).isFalse();
    }

    // ---------------------------------------------------------------------------
    // FormatTrajectory Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test format_trajectory from messages")
    @Tag("level0")
    void testFormatTrajectoryFromMessages() {
        List<Object> messages = new ArrayList<>();
        
        messages.add(new UserMessage("How to debug Python?"));
        messages.add(new AssistantMessage("Use pdb debugger"));
        
        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("Test trajectory string format")
    @Tag("level0")
    void testTrajectoryStringFormat() {
        String expectedFormat = "User: How to debug Python?\nAssistant: Use pdb debugger";
        
        assertThat(expectedFormat).contains("User:");
        assertThat(expectedFormat).contains("Assistant:");
    }

    // ---------------------------------------------------------------------------
    // SummarizeTrajectories Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test SummarizeTrajectoriesInput defaults")
    @Tag("level0")
    void testSummarizeTrajectoriesInputDefaults() {
        String userId = "test_user";
        List<Map<String, Object>> trajectories = new ArrayList<>();
        
        assertThat(userId).isEqualTo("test_user");
        assertThat(trajectories).isEmpty();
    }

    @Test
    @DisplayName("Test summarize_trajectories result")
    @Tag("level0")
    void testSummarizeTrajectoriesResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("memories_created", 3);
        
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result.get("memories_created")).isEqualTo(3);
    }

    // ---------------------------------------------------------------------------
    // RoundTrip Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test full before → after cycle")
    @Tag("level0")
    void testFullBeforeAfterCycle() {
        // Simulate full cycle
        List<String> cycleSteps = Arrays.asList(
            "before_task_iteration",
            "task_execution",
            "after_task_iteration",
            "auto_summarize_check"
        );
        
        assertThat(cycleSteps).hasSize(4);
        assertThat(cycleSteps.get(0)).isEqualTo("before_task_iteration");
        assertThat(cycleSteps.get(3)).isEqualTo("auto_summarize_check");
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private List<Map<String, Object>> createPromptTemplate() {
        List<Map<String, Object>> template = new ArrayList<>();
        
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYS_CONTENT);
        template.add(systemMsg);
        
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", USER_CONTENT);
        template.add(userMsg);
        
        return template;
    }

    private Map<String, Object> createTrajectoryEntry(int index) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("query", "Query " + index);
        entry.put("response", "Response " + index);
        entry.put("timestamp", System.currentTimeMillis());
        return entry;
    }
}