/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated evaluator pipeline result.
 * <p>
 * Mirrors Python's {@code PipelineResult} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResult {

    private String taskId;
    private String agentName;
    private String benchmarkName;
    private int totalIterations;
    private boolean convergenceAchieved;
    private String convergenceType = "";
    private List<IterationResult> results = new ArrayList<>();
    private Map<String, Object> metrics = new LinkedHashMap<>();
    private Path outputDir = Path.of("./evolution_results");
    private Path reportPath;
    private double timestamp = System.currentTimeMillis() / 1000.0d;

    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("agent_name", agentName);
        result.put("benchmark_name", benchmarkName);
        result.put("total_iterations", totalIterations);
        result.put("convergence_achieved", convergenceAchieved);
        result.put("convergence_type", convergenceType);
        result.put("metrics", metrics);
        result.put("output_dir", outputDir != null ? outputDir.toString() : null);
        result.put("timestamp", timestamp);
        return result;
    }
}
