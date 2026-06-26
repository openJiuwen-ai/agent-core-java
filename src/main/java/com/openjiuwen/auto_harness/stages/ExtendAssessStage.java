/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.GapAnalysisArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Analyze runtime extension capability gaps using the assess agent.
 *
 * <p>Mirrors Python's {@code ExtendAssessStage} in
 * {@code openjiuwen/auto_harness/stages/assess.py}.</p>
 */
public class ExtendAssessStage extends AssessStage {

    private static final Logger LOGGER = Logger.getLogger(ExtendAssessStage.class.getName());

    private final AssessAgentFactory agentFactory;

    public ExtendAssessStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public ExtendAssessStage(AssessAgentFactory agentFactory) {
        this.agentFactory = safeFactory(agentFactory);
    }

    @Override
    public String name() {
        return "assess_ext";
    }

    @Override
    public String displayName() {
        return "评估扩展缺口";
    }

    @Override
    public String description() {
        return "Analyze runtime extension capability gaps.";
    }

    @Override
    public List<String> produces() {
        return List.of("gap_analysis");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("ExtendAssessStage requires a SessionContext");
        }
        List<OptimizationTask> taskList = MetaAssessStage.readInputTasks(sessionContext);
        String goal = nullToEmpty(sessionContext.getOrchestrator().getConfig().getOptimizationGoal());
        List<Object> events = new ArrayList<>();
        StringBuilder output = new StringBuilder();

        if (!taskList.isEmpty() || !goal.isBlank()) {
            try {
                AssessAgent agent = agentFactory.create(
                        sessionContext.getOrchestrator().getConfig(),
                        sessionContext.getOrchestrator().getStreamRails()
                );
                Iterator<?> stream = agent.stream(Map.of("query", buildGapQuery(taskList, goal)));
                while (stream.hasNext()) {
                    Object chunk = stream.next();
                    String text = Parsers.extractText(chunk);
                    if (!text.isEmpty()) {
                        output.append(text);
                    }
                    events.add(chunk);
                }
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Agent gap analysis failed", exception);
            }
        }

        List<Gap> gaps = output.isEmpty() ? List.of() : Parsers.parseGaps(output.toString());
        if (gaps.isEmpty()) {
            LOGGER.warning("Agent gap analysis returned no results, falling back to heuristic");
            gaps = buildGaps(taskList);
        }

        GapAnalysisArtifact artifact = GapAnalysisArtifact.builder()
                .gaps(gaps)
                .competitorSummary("")
                .rawAnalysis(output.isEmpty() ? "heuristic gap analysis" : output.toString())
                .build();
        List<String> messages = new ArrayList<>();
        messages.add("Gap analysis complete: " + artifact.getGaps().size() + " gap(s)");
        if (!artifact.getGaps().isEmpty()) {
            List<String> summaries = new ArrayList<>();
            for (Gap gap : artifact.getGaps()) {
                summaries.add(gapSummary(gap));
            }
            messages.add("Gaps: " + String.join("; ", summaries));
        }
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("gap_analysis", artifact);
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(messages)
                .build());
        return events.iterator();
    }
}
