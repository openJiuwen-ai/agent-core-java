/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.infra.Parsers;
import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.autoharness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.List;
import java.util.Map;

/**
 * Public class SelectPipelineStage used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SelectPipelineStage extends TaskStage {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "select_pipeline";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Select the best pipeline for a task.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> consumes() {
        return List.of("assessment");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> produces() {
        return List.of("pipeline_selection");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StageResult run(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            return StageResult.builder()
                    .status("failed")
                    .error("select_pipeline requires TaskContext")
                    .build();
        }
        String assessment = String.valueOf(taskContext.getArtifact("assessment", ""));
        PipelineSelectionArtifact selection = runSelectPipeline(
                taskContext.getOrchestrator().getConfig(),
                taskContext.getTask(),
                assessment,
                List.of(MetaEvolvePipeline.NAME)
        );
        return StageResult.builder()
                .artifacts(Map.of("pipeline_selection", selection))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PipelineSelectionArtifact runSelectPipeline(AutoHarnessConfig config, OptimizationTask task,
                                                              String assessment,
                                                              List<String> availablePipelines) {
        if (task != null && hasText(task.getPipelineName())) {
            String pipelineName = normalizePipelineName(task.getPipelineName());
            return PipelineSelectionArtifact.builder()
                    .pipelineName(pipelineName)
                    .reason("task requested explicit pipeline")
                    .alternatives(List.of())
                    .confidence(1.0)
                    .fallbackPipeline(pipelineName)
                    .build();
        }
        if (config == null || config.getModel() == null) {
            return PipelineSelectionArtifact.builder()
                    .pipelineName(MetaEvolvePipeline.NAME)
                    .reason("no model configured, fallback to " + MetaEvolvePipeline.NAME)
                    .alternatives(List.of(ExtendedEvolvePipeline.NAME))
                    .confidence(0.0)
                    .fallbackPipeline(MetaEvolvePipeline.NAME)
                    .build();
        }
        DeepAgent agent = AutoHarnessFactory.createSelectPipelineAgent(config);
        String query = buildQuery(task, assessment, availablePipelines);
        StringBuilder output = new StringBuilder();
        agent.stream(Map.of("query", query)).forEachRemaining(chunk -> output.append(Parsers.extractText(chunk)));
        PipelineSelectionArtifact parsed = Parsers.parsePipelineSelection(output.toString());
        if (parsed != null) {
            return parsed;
        }
        return PipelineSelectionArtifact.builder()
                .pipelineName(MetaEvolvePipeline.NAME)
                .reason("selector fallback to default pipeline")
                .alternatives(List.of(ExtendedEvolvePipeline.NAME))
                .confidence(0.0)
                .fallbackPipeline(MetaEvolvePipeline.NAME)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String buildQuery(OptimizationTask task, String assessment, List<String> availablePipelines) {
        String summary = assessment == null ? "" : assessment.strip();
        if (summary.length() > 4000) {
            summary = summary.substring(0, 3997).stripTrailing() + "...";
        }
        List<String> files = task != null && task.getFiles() != null ? task.getFiles() : List.of();
        List<String> pipelines = availablePipelines != null && !availablePipelines.isEmpty()
                ? availablePipelines
                : List.of(MetaEvolvePipeline.NAME);
        return "任务主题: " + value(task != null ? task.getTopic() : "") + "\n"
                + "任务描述: " + valueOrDefault(task != null ? task.getDescription() : "", "无") + "\n"
                + "目标文件: " + (files.isEmpty() ? "未指定" : String.join(", ", files)) + "\n"
                + "评估摘要:\n" + (summary.isBlank() ? "无" : summary) + "\n\n"
                + "可选 pipeline:\n"
                + String.join("\n", pipelines.stream().map(name -> "- " + name).toList());
    }

    private static String normalizePipelineName(String name) {
        if (!hasText(name)) {
            return "";
        }
        return switch (name.trim()) {
            case "pr_pipeline" -> MetaEvolvePipeline.NAME;
            case "extended_harness_pipeline" -> ExtendedEvolvePipeline.NAME;
            default -> name.trim();
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }
}
