/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Session-level pipeline selection helpers.
 * <p>
 * Mirrors Python's module functions in
 * {@code openjiuwen/auto_harness/infra/pipeline_selector.py}.
 */
public final class PipelineSelector {

    private static final List<String> COMPETITOR_SIGNAL_KEYWORDS = List.of(
            "竞品",
            "competitor",
            "吸收",
            "absorb",
            "hermes",
            "evolver",
            "devin",
            "cursor",
            "扩展能力",
            "extend capability"
    );

    private static final List<Pattern> COMPETITOR_SIGNAL_PATTERNS = List.of(
            Pattern.compile("把.+的.+能力.+加入"),
            Pattern.compile("学习.+的做法")
    );

    private PipelineSelector() {
    }

    public static String detectPipelineSignal(Iterable<OptimizationTask> tasks, AutoHarnessConfig config) {
        List<String> textParts = new ArrayList<>();
        if (tasks != null) {
            for (OptimizationTask task : tasks) {
                if (task == null) {
                    continue;
                }
                textParts.add(nullToEmpty(task.getTopic()));
                textParts.add(nullToEmpty(task.getDescription()));
                textParts.add(nullToEmpty(task.getExpectedEffect()));
            }
        }
        if (config != null && config.getOptimizationGoal() != null && !config.getOptimizationGoal().isBlank()) {
            textParts.add(config.getOptimizationGoal());
        }
        String text = String.join(" ", textParts).toLowerCase(Locale.ROOT).strip();
        if (text.isEmpty()) {
            return null;
        }
        for (String keyword : COMPETITOR_SIGNAL_KEYWORDS) {
            if (text.contains(keyword)) {
                return AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE;
            }
        }
        for (Pattern pattern : COMPETITOR_SIGNAL_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE;
            }
        }
        return null;
    }

    public static PipelineSelectionArtifact chooseSessionPipeline(
            List<OptimizationTask> tasks,
            AutoHarnessConfig config,
            List<String> availablePipelines
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        String preference = AutoHarnessSchema.normalizePipelinePreference(resolvedConfig.getPipelinePreference());
        if (!AutoHarnessSchema.PIPELINE_PREFERENCE_AUTO.equals(preference) && availablePipelines.contains(preference)) {
            return PipelineSelectionArtifact.builder()
                    .pipelineName(preference)
                    .reason("config pipeline preference")
                    .alternatives(alternatives(availablePipelines, preference))
                    .confidence(1.0)
                    .fallbackPipeline(preference)
                    .build();
        }

        String signal = detectPipelineSignal(tasks, resolvedConfig);
        if (signal != null && availablePipelines.contains(signal)) {
            return PipelineSelectionArtifact.builder()
                    .pipelineName(signal)
                    .reason("auto signal matched extended evolve pipeline")
                    .alternatives(alternatives(availablePipelines, signal))
                    .confidence(0.85)
                    .fallbackPipeline(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE)
                    .build();
        }

        String pipelineName = availablePipelines.contains(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE)
                ? AutoHarnessPipelineNames.META_EVOLVE_PIPELINE
                : availablePipelines.get(0);
        return PipelineSelectionArtifact.builder()
                .pipelineName(pipelineName)
                .reason("auto default meta evolve pipeline")
                .alternatives(alternatives(availablePipelines, pipelineName))
                .confidence(0.7)
                .fallbackPipeline(pipelineName)
                .build();
    }

    private static List<String> alternatives(List<String> availablePipelines, String selected) {
        List<String> result = new ArrayList<>();
        for (String pipeline : availablePipelines) {
            if (!pipeline.equals(selected)) {
                result.add(pipeline);
            }
        }
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
