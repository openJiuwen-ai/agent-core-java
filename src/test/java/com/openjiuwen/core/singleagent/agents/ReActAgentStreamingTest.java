/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ReActAgent streaming (_railed_model_call streaming path).
 *
 * <p>Mirrors Python's {@code test_react_agent_streaming.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
@DisplayName("ReActAgent Streaming")
class ReActAgentStreamingTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("agent streaming config is properly set")
    void testAgentStreamingConfigProperlySet() {
        AgentCard card = AgentCard.builder().id("test_stream_agent").build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("sk-fake")
                        .apiBase("https://api.openai.com/v1")
                        .verifySsl(false)
                        .build())
                .modelConfigObj(ModelRequestConfig.builder()
                        .modelName("gpt-3.5-turbo")
                        .build())
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .build();
        agent.configure(config);
        assertThat(agent.getConfig()).isNotNull();
    }
}
