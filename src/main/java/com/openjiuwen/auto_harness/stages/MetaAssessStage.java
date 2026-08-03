/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AssessmentArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assess the repository state for the current session.
 *
 * <p>Mirrors Python's {@code MetaAssessStage} in
 * {@code openjiuwen/auto_harness/stages/assess.py}.</p>
 */
public class MetaAssessStage extends AssessStage {

    private final AssessAgentFactory agentFactory;

    public MetaAssessStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public MetaAssessStage(AssessAgentFactory agentFactory) {
        this.agentFactory = safeFactory(agentFactory);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("MetaAssessStage requires a SessionContext");
        }
        List<OptimizationTask> taskList = readInputTasks(sessionContext);
        List<Object> events = new ArrayList<>();
        StringBuilder assessment = new StringBuilder();
        Iterator<Object> stream = runAssessStream(
                sessionContext.getOrchestrator().getConfig(),
                sessionContext.getOrchestrator().getExperienceStore(),
                taskList,
                sessionContext.getOrchestrator().getStreamRails(),
                agentFactory
        );
        while (stream.hasNext()) {
            Object chunk = stream.next();
            String text = Parsers.extractText(chunk);
            if (!text.isEmpty()) {
                assessment.append(text);
            }
            events.add(chunk);
        }
        if (assessment.isEmpty()) {
            assessment.append(runAssessWithFallback(
                    sessionContext.getOrchestrator().getConfig(),
                    sessionContext.getOrchestrator().getExperienceStore(),
                    agentFactory
            ));
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        String report = assessment.toString();
        if (!report.strip().isEmpty()) {
            AutoHarnessOrchestratorBridge.writeDebugArtifact(
                    sessionContext.getOrchestrator().getConfig().getRunsDir(),
                    "latest_assessment.md",
                    report
            );
            artifacts.put("assessment", AssessmentArtifact.builder().report(report).build());
        }
        events.add(StageResult.builder().artifacts(artifacts).build());
        return events.iterator();
    }

    static List<OptimizationTask> readInputTasks(BaseExecutionContext ctx) {
        Object tasks = ctx.getArtifact("input_tasks", List.of());
        if (!(tasks instanceof List<?> list)) {
            return List.of();
        }
        List<OptimizationTask> typed = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof OptimizationTask task) {
                typed.add(task);
            }
        }
        return typed;
    }

    private static final class AutoHarnessOrchestratorBridge {
        private AutoHarnessOrchestratorBridge() {
        }

        private static void writeDebugArtifact(String runsDir, String filename, String content) {
            com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator.writeDebugArtifact(
                    runsDir,
                    filename,
                    content
            );
        }
    }
}
