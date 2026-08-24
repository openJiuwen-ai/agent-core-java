/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.code_agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.Result;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.subagents.CodeAgentFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Missing system-test coverage for the CodeAgent normal E2E path.
 *
 * <p>Mirrors Python's {@code TestCodeAgentE2E.test_code_agent_normal_e2e} in
 * {@code tests/system_tests/code_agent/test_code_agent_e2e.py}.</p>
 */
class CodeAgentE2eMissingTest {

    private String sysOperationId;

    @BeforeEach
    void setUp() {
        Runner.start().toCompletableFuture().join();
        sysOperationId = "codeagent_sysop_" + UUID.randomUUID().toString().replace("-", "");
        SysOperationCard card = new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().build()
        );
        Result<?, ?> addResult = Runner.resourceMgr().addSysOperation(card);
        assertThat(addResult.isErr()).as(String.valueOf(addResult.msg())).isFalse();
    }

    @AfterEach
    void tearDown() {
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        } finally {
            Runner.stop().toCompletableFuture().join();
        }
    }

    @Test
    void codeAgentNormalE2eUsesTaskPlanningRail() {
        SysOperation sysOperation = Runner.resourceMgr().getSysOperation(sysOperationId);
        TaskPlanningRail taskPlanningRail = new TaskPlanningRail();
        DeepAgent agent = CodeAgentFactory.createCodeAgent(
                "mock-model",
                null,
                null,
                null,
                null,
                null,
                List.of(taskPlanningRail),
                false,
                20,
                null,
                null,
                null,
                sysOperation,
                "cn",
                null,
                null
        );
        String query = "Plan a small module development task.";

        Object resultObject = agent.invoke(Map.of("query", query));

        assertThat(resultObject).isInstanceOf(Map.class);
        Map<?, ?> result = (Map<?, ?>) resultObject;
        assertThat(result.get("type")).isEqualTo("deep_agent_result");
        assertThat(result.get("input")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result.get("input")).get("query")).isEqualTo(query);
        assertThat(agent.findRailsByType(TaskPlanningRail.class)).hasSize(1);
        assertThat(toolIds(agent)).contains("todo_create", "todo_list", "todo_modify");
    }

    private static List<String> toolIds(DeepAgent agent) {
        return agent.getTools().values().stream()
                .map(tool -> tool.getCard().getId())
                .toList();
    }
}
