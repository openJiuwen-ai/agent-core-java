/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.code_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.subagents.CodeAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CodeAgent end-to-end system tests.
 *
 * <p>Mirrors Python's {@code TestCodeAgentE2E} in
 * {@code tests/system_tests/code_agent/test_code_agent_e2e.py}.</p>
 */
class TestCodeAgentE2e {

    private String sysOperationId;

    @BeforeEach
    void setUp() {
        Runner.start();
        sysOperationId = "codeagent_sysop_" + UUID.randomUUID().toString().replace("-", "");
        SysOperationCard card = SysOperationCard.builder()
                .id(sysOperationId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir("").build())
                .build();
        Result<SysOperationCard> addResult = Runner.resourceMgr().addSysOperation(card, null);
        assertTrue(addResult.isOk(), () -> "add_sys_operation failed: " + addResult.getError());
    }

    @AfterEach
    void tearDown() {
        if (sysOperationId != null) {
            Runner.resourceMgr().removeSysOperation(sysOperationId, null, TagMatchStrategy.ALL, true);
        }
        Runner.stop();
    }

    @Test
    @DisplayName("CodeAgent normal E2E with TaskPlanningRail")
    void testCodeAgentNormalE2e() {
        SysOperation sysOperation = (SysOperation) Runner.resourceMgr()
                .getSysOperation(sysOperationId, null, TagMatchStrategy.ALL);
        TaskPlanningRail taskPlanningRail = new TaskPlanningRail();
        QueueModel model = new QueueModel(
                toolResponse(
                        "mock_call_todo_create",
                        "todo_create",
                        "{\"tasks\":\"design module;implement core;write unit tests;integrate\"}"
                ),
                toolResponse("mock_call_todo_list", "todo_list", "{}"),
                toolResponse(
                        "mock_call_todo_modify",
                        "todo_modify",
                        "{\"action\":\"update\",\"todos\":[{\"id\":\"mock_task_id_1\",\"status\":\"completed\"}]}"
                ),
                textResponse("The module structure and core implementation plan are complete.")
        );

        Object agent = CodeAgent.createCodeAgent(
                model,
                null,
                null,
                null,
                List.of(taskPlanningRail),
                null,
                false,
                20,
                null,
                sysOperation,
                null
        );

        Object result = Runner.runAgent(agent, Map.of("query", "Plan a simple module development task."), null, null);

        Map<?, ?> resultMap = assertInstanceOf(Map.class, result);
        assertEquals("answer", resultMap.get("result_type"));
        assertEquals(4, model.invokeCount());
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder().content(content).build();
    }

    private static AssistantMessage toolResponse(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .type("function")
                        .name(name)
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static final class QueueModel extends Model {
        private final Queue<AssistantMessage> responses = new ArrayDeque<>();
        private int invokeCount;

        private QueueModel(AssistantMessage... responses) {
            super(modelClientConfig(), ModelRequestConfig.builder().modelName("mock-model").build());
            this.responses.addAll(List.of(responses));
        }

        int invokeCount() {
            return invokeCount;
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            invokeCount++;
            return responses.isEmpty() ? textResponse("Default mock response") : responses.remove();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        private static ModelClientConfig modelClientConfig() {
            return ModelClientConfig.builder()
                    .clientProvider("OpenAI")
                    .apiKey("sk-test")
                    .apiBase("https://mock.openai.local/v1")
                    .verifySsl(false)
                    .build();
        }
    }
}
