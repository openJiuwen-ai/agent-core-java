/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;

import java.util.List;

/**
 * Package facade for tune chat-agent exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.tune.chat_agent} module in
 * {@code openjiuwen/dev_tools/tune/chat_agent/__init__.py}.</p>
 */
public final class TuneChatAgentPackage {
    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/tune/chat_agent/__init__.py";
    public static final List<String> ALL = List.of(
            "ChatAgent",
            "ChatAgentConfig",
            "create_chat_agent",
            "create_chat_agent_config"
    );

    private TuneChatAgentPackage() {
    }

    public static Class<ChatAgent> chatAgentClass() {
        return ChatAgent.class;
    }

    public static Class<ChatAgentConfig> chatAgentConfigClass() {
        return ChatAgentConfig.class;
    }

    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig) {
        return ChatAgent.createChatAgent(agentConfig);
    }

    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig, List<? extends Tool> tools) {
        return ChatAgent.createChatAgent(agentConfig, tools);
    }

    public static ChatAgentConfig createChatAgentConfig(String agentId,
                                                       String agentVersion,
                                                       String description,
                                                       LLMCallConfig model) {
        return ChatAgent.createChatAgentConfig(agentId, agentVersion, description, model);
    }
}
