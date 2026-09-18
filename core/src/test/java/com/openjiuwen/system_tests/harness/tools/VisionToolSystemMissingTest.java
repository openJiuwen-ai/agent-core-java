/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.multimodal.VisionTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.system_tests.harness.tools.test_vision_tool} in
 * {@code tests/system_tests/harness/tools/test_vision_tool.py}.
 */
class VisionToolSystemMissingTest {

    @Test
    void createVisionToolsRegisterAndInvoke() throws Exception {
        RecordingVisionInvoker invoker = new RecordingVisionInvoker();
        DeepAgentConfig.VisionModelConfig visionModelConfig = visionModelConfig();
        List<Tool> tools = VisionTools.createVisionTools("cn", visionModelConfig, invoker);

        ToolOutput result;
        try {
            Runner.start().toCompletableFuture().join();
            Runner.resourceMgr().addTools(tools, null, true);
            Tool registeredTool = Runner.resourceMgr().getTool(toolIdByName(tools, "VisualQuestionAnsweringTool"));
            assertNotNull(registeredTool);

            result = (ToolOutput) registeredTool.invoke(Map.of(
                    "image_path_or_url", "https://example.com/image.png",
                    "question", "What text is shown?"));
        } finally {
            for (Tool tool : tools) {
                Runner.resourceMgr().removeTool(tool.getCard().getId());
            }
            Runner.stop().toCompletableFuture().join();
        }

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("HELLO WORLD", data.get("ocr_text"));
        assertEquals("The image text says HELLO WORLD.", data.get("answer"));
        assertEquals(2, invoker.prompts.size());
        assertSame(visionModelConfig, invoker.lastConfig);
    }

    @Test
    void runnerStopClearsRegisteredVisionTools() {
        List<Tool> tools = VisionTools.createVisionTools("cn", visionModelConfig(), new RecordingVisionInvoker());

        Tool leakedTool;
        Runner.start().toCompletableFuture().join();
        try {
            assertTrue(Runner.resourceMgr().addTools(tools, null, true).stream().allMatch(result -> result.isOk()));
            Runner.stop().toCompletableFuture().join();
            leakedTool = Runner.resourceMgr().getTool(toolIdByName(tools, "VisualQuestionAnsweringTool"));
        } finally {
            for (Tool tool : tools) {
                Runner.resourceMgr().removeTool(tool.getCard().getId());
            }
            Runner.stop().toCompletableFuture().join();
        }

        assertNull(leakedTool);
    }

    @Disabled("Skipped in Python source: Set RUN_VISION_SYSTEM_TESTS=1 to run live vision system tests.")
    @Test
    void createVisionToolsWithRealApiFromEnv() {
        /*
         * Python skips this live API test unless RUN_VISION_SYSTEM_TESTS is enabled and a vision API key is present.
         * The Java parity test inherits that skip to avoid requiring external credentials in the deterministic gate.
         */
    }

    private static DeepAgentConfig.VisionModelConfig visionModelConfig() {
        DeepAgentConfig.VisionModelConfig config = new DeepAgentConfig.VisionModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setModel("mock-model");
        return config;
    }

    private static String toolIdByName(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow()
                .getCard()
                .getId();
    }

    /**
     * Mirrors Python's monkeypatched {@code fake_call_vision_model} in
     * {@code tests/system_tests/harness/tools/test_vision_tool.py}.
     */
    private static final class RecordingVisionInvoker implements VisionTools.VisionInvoker {
        private final List<String> prompts = new ArrayList<>();
        private DeepAgentConfig.VisionModelConfig lastConfig;

        @Override
        public Map<String, Object> ocr(String imagePath, Map<String, Object> inputs) {
            String ignored = imagePath;
            prompts.add(String.valueOf(inputs.get("prompt")));
            lastConfig = (DeepAgentConfig.VisionModelConfig) inputs.get("vision_model_config");
            return Map.of("text", "HELLO WORLD", "model", "mock-model");
        }

        @Override
        public Map<String, Object> answer(String imagePath, String question, Map<String, Object> inputs) {
            String ignored = imagePath;
            prompts.add(question);
            lastConfig = (DeepAgentConfig.VisionModelConfig) inputs.get("vision_model_config");
            return Map.of("answer", "The image text says HELLO WORLD.", "model", "mock-model");
        }
    }
}
