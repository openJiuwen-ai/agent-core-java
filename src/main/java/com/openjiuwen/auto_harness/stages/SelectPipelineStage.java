/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.infra.PipelineSelector;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Session-level pipeline selection helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.select_pipeline} in
 * {@code openjiuwen/auto_harness/stages/select_pipeline.py}.</p>
 */
public final class SelectPipelineStage {

    private static final SelectPipelineAgentFactory DEFAULT_AGENT_FACTORY =
            config -> inputs -> AutoHarnessAgentFactory.createSelectPipelineAgent(config, null).stream(inputs);

    private SelectPipelineStage() {
    }

    /**
     * Mirrors Python's selector agent stream contract in
     * {@code openjiuwen/auto_harness/stages/select_pipeline.py}.
     */
    public interface SelectPipelineAgent {
        Iterator<Map<String, Object>> stream(Map<String, Object> inputs);
    }

    /**
     * Mirrors Python's late import of {@code create_select_pipeline_agent} in
     * {@code openjiuwen/auto_harness/stages/select_pipeline.py}.
     */
    public interface SelectPipelineAgentFactory {
        SelectPipelineAgent create(AutoHarnessConfig config);
    }

    public static Iterator<Map<String, Object>> runSelectPipelineStream(
            AutoHarnessConfig config,
            OptimizationTask task
    ) {
        return runSelectPipelineStream(config, task, "", null, DEFAULT_AGENT_FACTORY);
    }

    public static Iterator<Map<String, Object>> runSelectPipelineStream(
            AutoHarnessConfig config,
            OptimizationTask task,
            String assessment,
            List<String> availablePipelines
    ) {
        return runSelectPipelineStream(config, task, assessment, availablePipelines, DEFAULT_AGENT_FACTORY);
    }

    public static Iterator<Map<String, Object>> runSelectPipelineStream(
            AutoHarnessConfig config,
            OptimizationTask task,
            String assessment,
            List<String> availablePipelines,
            SelectPipelineAgentFactory agentFactory
    ) {
        SelectPipelineAgent agent = agentFactory.create(config);
        return agent.stream(Map.of(
                "query",
                buildQuery(
                        task,
                        assessment,
                        availablePipelines == null
                                ? List.of(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE)
                                : availablePipelines
                )
        ));
    }

    public static PipelineSelectionArtifact runSelectPipeline(
            AutoHarnessConfig config,
            OptimizationTask task
    ) {
        return runSelectPipeline(config, task, "", null);
    }

    public static PipelineSelectionArtifact runSelectPipeline(
            AutoHarnessConfig config,
            OptimizationTask task,
            String assessment,
            List<String> availablePipelines
    ) {
        return PipelineSelector.chooseSessionPipeline(
                List.of(task),
                config,
                availablePipelines == null
                        ? List.of(
                                AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                                AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
                        )
                        : availablePipelines
        );
    }

    public static String buildQuery(OptimizationTask task, String assessment, List<String> availablePipelines) {
        String summary = assessment == null ? "" : assessment.strip();
        if (summary.length() > 4000) {
            summary = summary.substring(0, 3997).stripTrailing() + "...";
        }
        List<String> pipelines = availablePipelines == null
                ? List.of(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE)
                : availablePipelines;
        return "任务主题: " + task.getTopic() + "\n"
                + "任务描述: " + defaultIfBlank(task.getDescription(), "无") + "\n"
                + "目标文件: " + defaultIfBlank(String.join(", ", task.getFiles()), "未指定") + "\n"
                + "评估摘要:\n" + defaultIfBlank(summary, "无") + "\n\n"
                + "可选 pipeline:\n"
                + String.join("\n", pipelines.stream().map(name -> "- " + name).toList());
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
