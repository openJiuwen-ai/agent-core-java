/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void fromDictAppliesOverrides() {
        PipelineConfig config = PipelineConfig.fromDict(Map.of(
                "agent", "custom",
                "benchmark", "bench",
                "maxIterations", 3,
                "taskIds", List.of("t1", "t2")
        ));

        assertEquals("custom", config.getAgent());
        assertEquals("bench", config.getBenchmark());
        assertEquals(3, config.getMaxIterations());
        assertEquals(List.of("t1", "t2"), config.getTaskIds());
    }

    @Test
    void fromYamlLoadsPipelineAndNestedConfigs() throws Exception {
        Path yaml = tempDir.resolve("config.yaml");
        Files.writeString(yaml, """
                pipeline:
                  agent: custom-agent
                  benchmark: custom-bench
                  max_iterations: 4
                agent_config:
                  api_key: test-key
                bench_config:
                  tasks_dir: tasks
                """);

        PipelineConfig config = PipelineConfig.fromYaml(yaml);
        assertEquals("custom-agent", config.getAgent());
        assertEquals("custom-bench", config.getBenchmark());
        assertEquals(4, config.getMaxIterations());
        assertEquals("test-key", config.getAgentConfig().get("api_key"));
        assertTrue(config.getBenchConfig().containsKey("tasks_dir"));
    }
}
