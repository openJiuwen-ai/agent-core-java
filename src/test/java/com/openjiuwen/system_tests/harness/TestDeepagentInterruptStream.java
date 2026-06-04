/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test DeepAgent stream interrupt output.
 * <p>
 * Mirrors Python's {@code test_deepagent_interrupt_stream.py} in
 * {@code tests/system_tests/harness/test_deepagent_interrupt_stream.py}.
 */
public class TestDeepagentInterruptStream {

    private static final String API_BASE = System.getenv("API_BASE");
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String MODEL_NAME = System.getenv("MODEL_NAME");
    private static final String MODEL_PROVIDER = System.getenv("MODEL_PROVIDER");

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        Runner.stop();
    }

    private boolean hasApiConfig() {
        return API_KEY != null && !API_KEY.isBlank()
                && API_BASE != null && !API_BASE.isBlank();
    }

    private ModelClientConfig createModelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER != null ? MODEL_PROVIDER : "OpenAI")
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(60)
                .verifySsl(false)
                .build();
    }

    private ModelRequestConfig createModelRequestConfig() {
        return ModelRequestConfig.builder()
                .modelName(MODEL_NAME != null ? MODEL_NAME : "model")
                .build();
    }

    @Nested
    @DisplayName("Stream interrupt tests")
    class StreamInterruptTests {

        @Test
        @DisplayName("Test DeepAgent stream interrupt and resume flow")
        void testDeepagentStreamInterruptResume() {
            assumeTrue(hasApiConfig(), "API_KEY and API_BASE required");

            AgentCard card = AgentCard.builder()
                    .id("test_deepagent_resume_agent")
                    .name("TestDeepAgentResume")
                    .build();
            DeepAgent agent = new DeepAgent(card);

            ReadTool readTool = new ReadTool();
            WriteTool writeTool = new WriteTool();
            Runner.resourceMgr().addTool(readTool, null);
            Runner.resourceMgr().addTool(writeTool, null);

            DeepAgentConfig config = new DeepAgentConfig();
            config.setCard(card);
            config.setModelClientConfig(createModelClientConfig());
            config.setModelRequestConfig(createModelRequestConfig());
            config.setSystemPrompt("When the user asks to read or write files, call the matching tool.");
            config.setMaxIterations(5);
            config.setTools(List.of(readTool.getCard(), writeTool.getCard()));
            config.setRails(List.of(new ConfirmInterruptRail(List.of("write"))));
            agent.configure(config);

            List<Object> outputs1 = new ArrayList<>();
            boolean interruptDetected = false;
            String toolCallId = null;

            Iterator<Object> firstRun = Runner.runAgentStreaming(
                    agent,
                    Map.of(
                            "query", "Please write file test.txt with content hello world",
                            "conversation_id", "test_resume_1"
                    ),
                    null,
                    null,
                    null
            );
            while (firstRun.hasNext()) {
                Object output = firstRun.next();
                outputs1.add(output);
                String interactionId = extractInteractionId(output);
                if (interactionId != null) {
                    interruptDetected = true;
                    toolCallId = interactionId;
                }
            }

            assertThat(outputs1).isNotEmpty();
            assertThat(interruptDetected).isTrue();
            assertThat(toolCallId).isNotNull();
            assertThat(writeTool.getInvokeCount()).isZero();

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update(toolCallId, Map.of(
                    "approved", true,
                    "feedback", "Confirm",
                    "auto_confirm", false
            ));

            boolean secondInterruptDetected = false;
            Iterator<Object> secondRun = Runner.runAgentStreaming(
                    agent,
                    Map.of(
                            "query", interactiveInput,
                            "conversation_id", "test_resume_1"
                    ),
                    null,
                    null,
                    null
            );
            while (secondRun.hasNext()) {
                Object output = secondRun.next();
                if (extractInteractionId(output) != null) {
                    secondInterruptDetected = true;
                }
            }

            assertThat(secondInterruptDetected).isFalse();
            assertThat(writeTool.getInvokeCount()).isEqualTo(1);
        }
    }

    private static String extractInteractionId(Object output) {
        if (!(output instanceof OutputSchema schema)
                || !Constant.INTERACTION.equals(schema.getType())) {
            return null;
        }
        Object payload = schema.getPayload();
        if (payload instanceof InteractionOutput interactionOutput) {
            return interactionOutput.getId();
        }
        if (payload instanceof Map<?, ?> payloadMap) {
            Object id = payloadMap.get("id");
            return id != null ? String.valueOf(id) : null;
        }
        return null;
    }

    private static class ReadTool extends Tool {
        private int invokeCount;

        ReadTool() {
            super(ToolCard.builder()
                    .id("read")
                    .name("read")
                    .description("Read file content")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "filepath", Map.of("description", "File path", "type", "string")
                            ),
                            "required", List.of("filepath")
                    ))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokeCount++;
            String filepath = String.valueOf(inputs.getOrDefault("filepath", ""));
            return Map.of(
                    "success", true,
                    "content", "Content of file " + filepath,
                    "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }

    private static class WriteTool extends Tool {
        private int invokeCount;

        WriteTool() {
            super(ToolCard.builder()
                    .id("write")
                    .name("write")
                    .description("Write file content")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "filepath", Map.of("description", "File path", "type", "string"),
                                    "content", Map.of("description", "Content", "type", "string")
                            ),
                            "required", List.of("filepath", "content")
                    ))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokeCount++;
            String filepath = String.valueOf(inputs.getOrDefault("filepath", ""));
            return Map.of(
                    "success", true,
                    "message", "Written to " + filepath,
                    "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }

        int getInvokeCount() {
            return invokeCount;
        }
    }
}
