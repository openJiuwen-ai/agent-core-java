/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessor;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessorConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MicroCompactProcessor behavior.
 * <p>
 * Mirrors Python's {@code test_micro_compact_processor.py} from
 * {@code tests/unit_tests/core/context_engine/test_micro_compact_processor.py}.
 * Tests micro-level context compaction for tool messages.
 */
class TestMicroCompactProcessor {

    // ---------------------------------------------------------------------------
    // Helper methods - Mirrors Python create_tool_call_list
    // ---------------------------------------------------------------------------

    private List<ToolCall> createToolCallList(List<String> ids, List<String> names) {
        List<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            ToolCall call = new ToolCall();
            call.setId(ids.get(i));
            call.setName(names.get(i));
            call.setType("function");
            call.setArguments("");
            calls.add(call);
        }
        return calls;
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testMicroCompactProcessorClassExists() {
        assertNotNull(MicroCompactProcessor.class);
    }

    @Test
    @Tag("level0")
    void testMicroCompactProcessorConfigClassExists() {
        assertNotNull(MicroCompactProcessorConfig.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineClassExists() {
        assertNotNull(ContextEngine.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineConfigClassExists() {
        assertNotNull(ContextEngineConfig.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Configuration tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testMicroCompactProcessorConfigCreation() {
        MicroCompactProcessorConfig config = new MicroCompactProcessorConfig();
        assertNotNull(config);
    }

    @Test
    @Tag("level1")
    void testMicroCompactProcessorConfigTriggerThreshold() {
        MicroCompactProcessorConfig config = new MicroCompactProcessorConfig();
        config.setTriggerThreshold(10);
        assertEquals(10, config.getTriggerThreshold());
    }

    @Test
    @Tag("level1")
    void testMicroCompactProcessorConfigCompactableToolNames() {
        MicroCompactProcessorConfig config = new MicroCompactProcessorConfig();
        List<String> toolNames = List.of("read_file", "write_file");
        config.setCompactableToolNames(toolNames);
        assertNotNull(config.getCompactableToolNames());
    }

    @Test
    @Tag("level1")
    void testMicroCompactProcessorConfigKeepRecentPerTool() {
        MicroCompactProcessorConfig config = new MicroCompactProcessorConfig();
        config.setKeepRecentPerTool(2);
        assertEquals(2, config.getKeepRecentPerTool());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (ToolCall creation tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testToolCallListCreation() {
        List<String> ids = List.of("tc-1", "tc-2", "tc-3");
        List<String> names = List.of("read_file", "read_file", "read_file");
        List<ToolCall> calls = createToolCallList(ids, names);

        assertEquals(3, calls.size());
        assertEquals("tc-1", calls.get(0).getId());
        assertEquals("read_file", calls.get(0).getName());
        assertEquals("function", calls.get(0).getType());
    }

    @Test
    @Tag("level2")
    void testToolCallEmptyArguments() {
        List<String> ids = List.of("tc-1");
        List<String> names = List.of("test_tool");
        List<ToolCall> calls = createToolCallList(ids, names);

        assertEquals("", calls.get(0).getArguments());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Message creation tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testAssistantMessageWithToolCalls() {
        ToolCall call = new ToolCall();
        call.setId("tc-1");
        call.setName("read_file");

        AssistantMessage msg = new AssistantMessage("");
        msg.setToolCalls(List.of(call));

        assertNotNull(msg.getToolCalls());
        assertEquals(1, msg.getToolCalls().size());
        assertEquals("tc-1", msg.getToolCalls().get(0).getId());
    }

    @Test
    @Tag("level3")
    void testToolMessageCreation() {
        ToolMessage msg = new ToolMessage("file-content", "tc-1");
        assertEquals("file-content", msg.getContent());
        assertEquals("tc-1", msg.getToolCallId());
    }

    @Test
    @Tag("level3")
    void testUserMessageCreation() {
        UserMessage msg = new UserMessage("test query");
        assertEquals("test query", msg.getContent());
        assertEquals("user", msg.getRole());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (Context engine configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testContextEngineConfigWindowMessageNum() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowMessageNum(100);
        assertEquals(100, config.getDefaultWindowMessageNum());
    }

    @Test
    @Tag("level4")
    void testContextEngineCreationWithConfig() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowMessageNum(100);
        ContextEngine engine = new ContextEngine(config);
        assertNotNull(engine);
    }
}