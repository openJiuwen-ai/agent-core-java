/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorPipelineModelsTest {

    @Test
    void execResultSuccessMatchesReturnCode() {
        ExecResult result = new ExecResult("ok", "", 0, false);
        assertTrue(result.isSuccess());
    }

    @Test
    void pipelineResultToDictUsesPythonKeys() {
        PipelineResult result = new PipelineResult();
        result.setTaskId("t1");
        result.setAgentName("agent");
        result.setBenchmarkName("bench");
        result.setTotalIterations(2);
        result.setConvergenceAchieved(true);
        result.setOutputDir(Path.of("out"));

        Map<String, Object> dict = result.toDict();
        assertEquals("t1", dict.get("task_id"));
        assertEquals("agent", dict.get("agent_name"));
        assertEquals("out", dict.get("output_dir"));
    }
}
