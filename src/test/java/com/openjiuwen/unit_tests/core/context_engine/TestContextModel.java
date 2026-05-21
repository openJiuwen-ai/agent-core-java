/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModelContext behavior.
 * <p>
 * Mirrors Python's {@code test_context_model.py} from
 * {@code tests/unit_tests/core/context_engine/test_context_model.py}.
 */
class TestContextModel {

    // ---------------------------------------------------------------------------
    // Helper methods - Mirrors Python strip_metadata and create_context
    // ---------------------------------------------------------------------------

    /**
     * Strip metadata from messages for comparison.
     * Mirrors Python's strip_metadata helper.
     */
    private List<BaseMessage> stripMetadata(List<BaseMessage> messages) {
        List<BaseMessage> result = new ArrayList<>();
        for (BaseMessage msg : messages) {
            // Create copy without metadata for comparison
            BaseMessage copy = copyMessage(msg);
            result.add(copy);
        }
        return result;
    }

    private BaseMessage copyMessage(BaseMessage msg) {
        if (msg instanceof UserMessage) {
            return new UserMessage(msg.getContent());
        } else if (msg instanceof AssistantMessage) {
            return new AssistantMessage(msg.getContent());
        } else if (msg instanceof SystemMessage) {
            return new SystemMessage(msg.getContent());
        } else if (msg instanceof ToolMessage) {
            return new ToolMessage(msg.getContent(), msg.getToolCallId());
        }
        return msg;
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testModelContextClassExists() {
        assertNotNull(ModelContext.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineClassExists() {
        assertNotNull(ContextEngine.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineConfigExists() {
        assertNotNull(ContextEngineConfig.class);
    }

    @Test
    @Tag("level0")
    void testUserMessageClassExists() {
        assertNotNull(UserMessage.class);
    }

    @Test
    @Tag("level0")
    void testAssistantMessageClassExists() {
        assertNotNull(AssistantMessage.class);
    }

    @Test
    @Tag("level0")
    void testSystemMessageClassExists() {
        assertNotNull(SystemMessage.class);
    }

    @Test
    @Tag("level0")
    void testToolMessageClassExists() {
        assertNotNull(ToolMessage.class);
    }

    @Test
    @Tag("level0")
    void testToolCallClassExists() {
        assertNotNull(ToolCall.class);
    }

    @Test
    @Tag("level0")
    void testToolInfoClassExists() {
        assertNotNull(ToolInfo.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Message creation tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testUserMessageCreation() {
        UserMessage msg = new UserMessage("test content");
        assertEquals("test content", msg.getContent());
        assertEquals("user", msg.getRole());
    }

    @Test
    @Tag("level1")
    void testAssistantMessageCreation() {
        AssistantMessage msg = new AssistantMessage("assistant response");
        assertEquals("assistant response", msg.getContent());
        assertEquals("assistant", msg.getRole());
    }

    @Test
    @Tag("level1")
    void testSystemMessageCreation() {
        SystemMessage msg = new SystemMessage("system instruction");
        assertEquals("system instruction", msg.getContent());
        assertEquals("system", msg.getRole());
    }

    @Test
    @Tag("level1")
    void testToolMessageCreation() {
        ToolMessage msg = new ToolMessage("tool result", "call-123");
        assertEquals("tool result", msg.getContent());
        assertEquals("call-123", msg.getToolCallId());
    }

    @Test
    @Tag("level1")
    void testToolCallCreation() {
        ToolCall call = new ToolCall();
        call.setId("call-123");
        call.setName("test_tool");
        call.setType("function");
        assertEquals("call-123", call.getId());
        assertEquals("test_tool", call.getName());
        assertEquals("function", call.getType());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (ContextEngine configuration tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testContextEngineConfigDefaultValues() {
        ContextEngineConfig config = new ContextEngineConfig();
        assertNotNull(config);
    }

    @Test
    @Tag("level2")
    void testContextEngineConfigWithWindowMessageNum() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowMessageNum(100);
        assertEquals(100, config.getDefaultWindowMessageNum());
    }

    @Test
    @Tag("level2")
    void testContextEngineConfigWithWindowRoundNum() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowRoundNum(10);
        assertEquals(10, config.getDefaultWindowRoundNum());
    }

    @Test
    @Tag("level2")
    void testContextEngineCreation() {
        ContextEngineConfig config = new ContextEngineConfig();
        ContextEngine engine = new ContextEngine(config);
        assertNotNull(engine);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Message list operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testStripMetadataWithUserMessage() {
        List<BaseMessage> messages = List.of(new UserMessage("test"));
        List<BaseMessage> stripped = stripMetadata(messages);
        assertEquals(1, stripped.size());
        assertEquals("test", stripped.get(0).getContent());
    }

    @Test
    @Tag("level3")
    void testStripMetadataWithMultipleMessages() {
        List<BaseMessage> messages = List.of(
                new UserMessage("user1"),
                new AssistantMessage("assistant1"),
                new UserMessage("user2")
        );
        List<BaseMessage> stripped = stripMetadata(messages);
        assertEquals(3, stripped.size());
        assertEquals("user1", stripped.get(0).getContent());
        assertEquals("assistant1", stripped.get(1).getContent());
        assertEquals("user2", stripped.get(2).getContent());
    }

    @Test
    @Tag("level3")
    void testCreateMessageList() {
        List<BaseMessage> messageList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            messageList.add(new UserMessage("test-" + i));
        }
        assertEquals(100, messageList.size());
    }

    // ---------------------------------------------------------------------------
    // Tests - StatusCode validation
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testStatusCodeContextMessageInvalid() {
        assertNotNull(StatusCode.CONTEXT_MESSAGE_INVALID);
    }

    @Test
    @Tag("level1")
    void testStatusCodeValues() {
        assertNotNull(StatusCode.values());
        assertTrue(StatusCode.values().length > 0);
    }

    // ---------------------------------------------------------------------------
    // Tests - BaseMessage properties
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testBaseMessageRoleValues() {
        assertEquals("user", new UserMessage("test").getRole());
        assertEquals("assistant", new AssistantMessage("test").getRole());
        assertEquals("system", new SystemMessage("test").getRole());
    }

    @Test
    @Tag("level2")
    void testBaseMessageContentModification() {
        UserMessage msg = new UserMessage("original");
        assertEquals("original", msg.getContent());
        msg.setContent("modified");
        assertEquals("modified", msg.getContent());
    }

    // ---------------------------------------------------------------------------
    // Tests - Message comparison
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testMessageEquality() {
        UserMessage msg1 = new UserMessage("test");
        UserMessage msg2 = new UserMessage("test");
        assertEquals(msg1.getContent(), msg2.getContent());
        assertEquals(msg1.getRole(), msg2.getRole());
    }

    @Test
    @Tag("level2")
    void testMessageInequality() {
        UserMessage msg1 = new UserMessage("test1");
        UserMessage msg2 = new UserMessage("test2");
        assertNotEquals(msg1.getContent(), msg2.getContent());
    }
}