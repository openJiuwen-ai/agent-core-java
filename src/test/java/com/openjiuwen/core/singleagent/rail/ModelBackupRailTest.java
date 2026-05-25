/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ModelBackupRail with mock LLM.
 * <p>
 * Mirrors Python's {@code test_model_backup_rail} in
 * {@code tests/system_tests/rail/test_model_backup_rail.py}.
 */
@Tag("system-test")
class ModelBackupRailTest {

    private ReActAgent reactAgent;
    private ModelRequestConfig modelConfig;
    private ModelClientConfig clientConfig;
    private List<Map<String, String>> promptTemplate;
    private LocalFunction addTool;

    @BeforeEach
    void setUp() {
        if (!System.getenv().containsKey("LLM_SSL_VERIFY")) {
            System.setProperty("LLM_SSL_VERIFY", "false");
        }
        modelConfig = createModelConfig();
        clientConfig = createClientConfig();
        promptTemplate = createPromptTemplate();

        AgentCard agentCard = AgentCard.builder()
                .description("Math calculation assistant")
                .build();
        ReActAgentConfig reactAgentConfig = new ReActAgentConfig();
        reactAgentConfig.setModelConfigObj(modelConfig);
        reactAgentConfig.setModelClientConfig(clientConfig);
        reactAgentConfig.setPromptTemplate(promptTemplate);
        reactAgent = new ReActAgent(agentCard);
        reactAgent.configure(reactAgentConfig);

        Model model = new Model(clientConfig, modelConfig);
        ModelBackupRail modelBackupMiddleware = new ModelBackupRail(List.of(model));
        reactAgent.registerRail(modelBackupMiddleware);
        addTool = createAddTool();
        reactAgent.getAbilityManager().add(addTool.getCard());
    }

    @AfterEach
    void tearDown() {
        reactAgent = null;
    }

    private static ModelRequestConfig createModelConfig() {
        ModelRequestConfig config = new ModelRequestConfig();
        config.setModel("gpt-3.5-turbo");
        config.setTemperature(0.8);
        config.setTopP(0.9);
        return config;
    }

    private static ModelClientConfig createClientConfig() {
        ModelClientConfig config = new ModelClientConfig();
        config.setClientProvider("OpenAI");
        config.setApiKey("mock_key");
        config.setApiBase("mock_url");
        config.setTimeout(30);
        config.setVerifySsl(false);
        return config;
    }

    private static List<Map<String, String>> createPromptTemplate() {
        return List.of(
                Map.of("role", "system", "content", "You are a math calculation assistant.")
        );
    }

    private static LocalFunction createAddTool() {
        ToolCard toolCard = new ToolCard();
        toolCard.setId("add");
        toolCard.setName("add");
        toolCard.setDescription("Addition operation");
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> aParam = new HashMap<>();
        aParam.put("description", "First addend");
        aParam.put("type", "number");
        Map<String, Object> bParam = new HashMap<>();
        bParam.put("description", "Second addend");
        bParam.put("type", "number");
        properties.put("a", aParam);
        properties.put("b", bParam);
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("a", "b"));
        toolCard.setInputParams(inputParams);
        return new LocalFunction(toolCard, (args) -> {
            Number a = (Number) args.get("a");
            Number b = (Number) args.get("b");
            return a.doubleValue() + b.doubleValue();
        });
    }

    @Test
    void testMiddlewareExecutesWhenReactAgentInvoke() throws Exception {
        Map<String, Object> result = reactAgent.invoke(Map.of(
                "conversation_id", "test_session",
                "query", "Calculate 1+2"
        ));
        assertNotNull(result);
        assertNotNull(result.get("output"));
        String output = result.get("output").toString();
        assertTrue(output.contains("3") || output.contains("1+2") || output.contains("计算"),
                "Expected calculation result in output but got: " + output);
    }
}
