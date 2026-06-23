/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.LlmCallConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code ChatAgentConfig} in
 * {@code openjiuwen/dev_tools/tune/chat_agent/chat_config.py}.
 */
class ChatAgentConfigTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void chatAgentConfigExtendsLegacyAgentConfig() {
        LlmCallConfig llmCallConfig = new LlmCallConfig();
        ChatAgentConfig config = new ChatAgentConfig(llmCallConfig);

        assertInstanceOf(AgentConfig.class, config);
        assertSame(llmCallConfig, config.getLlmCallConfig());
        assertTrue(config.getTools().isEmpty());
    }

    @Test
    void jsonUsesPythonModelFieldForLlmCallConfig() throws Exception {
        ChatAgentConfig config = new ChatAgentConfig(new LlmCallConfig());
        config.setDescription("chat");

        Map<String, Object> payload = mapper.readValue(
                mapper.writeValueAsBytes(config),
                new TypeReference<>() {
                });

        assertEquals("chat", payload.get("description"));
        assertTrue(payload.containsKey("model"));
        assertFalse(payload.containsKey("llmCallConfig"));
    }

    @Test
    void jsonDeserializesModelAsLlmCallConfig() throws Exception {
        ChatAgentConfig config = mapper.readValue(
                "{\"description\":\"chat\",\"model\":{\"freeze_user_prompt\":false}}",
                ChatAgentConfig.class);

        assertEquals("chat", config.getDescription());
        assertNotNull(config.getLlmCallConfig());
        assertFalse(config.getLlmCallConfig().isFreezeUserPrompt());
    }
}
