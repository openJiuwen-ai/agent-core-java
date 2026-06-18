/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.openjiuwen.core.single_agent.legacy.config.LlmCallConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.tune.chat_agent} module in
 * {@code openjiuwen/dev_tools/tune/chat_agent/__init__.py}.
 */
class TuneChatAgentPackageTest {

    @Test
    void exposesPythonModuleAndAllExports() {
        assertEquals("openjiuwen/dev_tools/tune/chat_agent/__init__.py", TuneChatAgentPackage.PYTHON_MODULE);
        assertEquals(List.of("ChatAgent", "ChatAgentConfig", "create_chat_agent", "create_chat_agent_config"),
                TuneChatAgentPackage.ALL);
        assertSame(ChatAgent.class, TuneChatAgentPackage.chatAgentClass());
        assertSame(ChatAgentConfig.class, TuneChatAgentPackage.chatAgentConfigClass());
    }

    @Test
    void createChatAgentConfigDelegatesToTranslatedFactory() {
        LlmCallConfig model = new LlmCallConfig();

        ChatAgentConfig config = TuneChatAgentPackage.createChatAgentConfig(
                "chat-agent",
                "1.0.0",
                "description",
                model
        );

        assertEquals("chat-agent", config.getId());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("description", config.getDescription());
        assertSame(model, config.getLlmCallConfig());
    }
}
