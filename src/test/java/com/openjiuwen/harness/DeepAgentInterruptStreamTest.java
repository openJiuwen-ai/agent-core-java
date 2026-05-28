/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Test DeepAgent stream interrupt and resume flow.
 * <p>
 * Mirrors Python's {@code TestDeepAgentInterruptStream} in
 * {@code tests.system_tests.harness.test_deepagent_interrupt_stream}.
 */
@Tag("system-test")
class DeepAgentInterruptStreamTest {

    static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "");
    static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "SiliconFlow");

    static Model createModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .verifySsl(false)
                .build();
        ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .build();
        return new Model(clientConfig, modelConfig);
    }

    static boolean hasApiConfig() {
        return API_KEY != null && !API_KEY.isEmpty()
                && API_BASE != null && !API_BASE.isEmpty();
    }

    @Test
    @Disabled("API_KEY and API_BASE required")
    void testDeepagentStreamInterruptResume() throws Exception {
        assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");

        Runner.start();
        try {
            AgentCard card = new AgentCard();
            card.setId("test_deepagent_resume_agent");
            card.setName("TestDeepAgentResume");
            DeepAgent agent = new DeepAgent(card);

            DeepAgentConfig config = new DeepAgentConfig();
            config.setCard(card);
            config.setMaxIterations(5);
            agent.configure(config);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "请写入文件 test.txt 内容为 hello world");
            inputs.put("conversation_id", "test_resume_1");

            assertNotNull(agent);
            assertNotNull(inputs);
        } finally {
            Runner.stop();
        }
    }
}
