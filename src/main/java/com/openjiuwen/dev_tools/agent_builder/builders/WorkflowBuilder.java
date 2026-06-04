/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.CycleChecker;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.DlGenerator;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.DlReflector;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.IntentionDetector;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.WorkflowDesigner;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow agent builder — creates workflow-based agents from user specifications.
 * <p>
 * Includes:
 * 1. Intent detection and SE workflow design (INITIAL state)
 * 2. DL generation and optimization (PROCESSING state)
 * 3. DSL transformation (COMPLETED state)
 * <p>
 * Mirrors Python's {@code WorkflowBuilder} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.builder}.
 */
public class WorkflowBuilder extends BaseAgentBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowBuilder.class);

    private static final String REQUEST_CONTENT = "Please provide workflow requirements";

    // Workflow metadata
    private String workflowName;
    private String workflowNameEn;
    private String workflowDesc;
    private String dl;
    private String mermaidCode;

    // Components
    private final IntentionDetector intentionDetector;
    private final DlReflector dlReflector;
    private final WorkflowDesigner workflowDesigner;
    private final DlGenerator dlGenerator;
    private final DlTransformer dlTransformer;
    private final CycleChecker cycleChecker;

    /**
     * Initialize Workflow builder.
     * <p>
     * Mirrors Python's {@code __init__} method.
     *
     * @param llm            LLM service instance
     * @param historyManager History manager instance
     */
    public WorkflowBuilder(Object llm, Object historyManager) {
        super(llm, historyManager instanceof HistoryManager manager ? manager : null, null);
        this.intentionDetector = new IntentionDetector(llm);
        this.dlReflector = new DlReflector();
        this.workflowDesigner = new WorkflowDesigner(llm);
        this.dlGenerator = new DlGenerator(llm);
        this.dlTransformer = new DlTransformer();
        this.cycleChecker = new CycleChecker(llm);
        LOG.debug("WorkflowBuilder initialized");
    }

    /**
     * Initialize with ProgressReporter.
     *
     * @param progressReporter Progress reporter instance
     */
    public WorkflowBuilder(ProgressReporter progressReporter) {
        super(progressReporter);
        this.intentionDetector = new IntentionDetector();
        this.dlReflector = new DlReflector();
        this.workflowDesigner = new WorkflowDesigner();
        this.dlGenerator = new DlGenerator(null);
        this.dlTransformer = new DlTransformer();
        this.cycleChecker = new CycleChecker();
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getWorkflowNameEn() {
        return workflowNameEn;
    }

    public String getWorkflowDesc() {
        return workflowDesc;
    }

    public String getDl() {
        return dl;
    }

    public String getMermaidCode() {
        return mermaidCode;
    }

    public IntentionDetector getIntentionDetector() {
        return intentionDetector;
    }

    public WorkflowDesigner getWorkflowDesigner() {
        return workflowDesigner;
    }

    public DlGenerator getDlGenerator() {
        return dlGenerator;
    }

    public DlReflector getDlReflector() {
        return dlReflector;
    }

    public DlTransformer getDlTransformer() {
        return dlTransformer;
    }

    public CycleChecker getCycleChecker() {
        return cycleChecker;
    }

    @Override
    protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
        LOG.info("[WorkflowBuilder] Handling INITIAL state");

        if (progressReporter != null) {
            progressReporter.report(
                    AgentBuilderEnums.ProgressStage.DETECTING_INTENTION,
                    AgentBuilderEnums.ProgressStatus.RUNNING,
                    "Detecting workflow intention"
            );
        }

        String userInput = extractUserInput(query);
        boolean hasInstruction = intentionDetector.detect(userInput) != IntentionDetector.Intention.UNKNOWN;
        if (!hasInstruction) {
            state = AgentBuilderEnums.BuildState.PROCESSING;
            return Map.of("status", "request_content", "state", "processing", "message", REQUEST_CONTENT);
        }

        updateWorkflowInfo(Map.of(
                "name", abbreviate(userInput),
                "name_en", "workflow",
                "description", "Workflow design"
        ));

        String design = workflowDesigner.design(userInput, formatToolList());
        String generatedDl = dlGenerator.generate(design == null || design.isBlank() ? userInput : design, resource);
        this.dl = generatedDl == null || generatedDl.isBlank() ? fallbackDl() : generatedDl;
        this.mermaidCode = generateMermaid(dl);

        state = AgentBuilderEnums.BuildState.PROCESSING;
        return Map.of(
                "status", "detecting_intention",
                "state", "processing",
                "workflow_name", workflowName != null ? workflowName : "",
                "mermaid_code", mermaidCode != null ? mermaidCode : ""
        );
    }

    @Override
    protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
        LOG.info("[WorkflowBuilder] Handling PROCESSING state");

        String userInput = extractUserInput(query);
        if (dl == null) {
            updateWorkflowInfo(Map.of(
                    "name", abbreviate(userInput),
                    "name_en", "workflow",
                    "description", "Workflow design"
            ));
            String design = workflowDesigner.design(userInput, formatToolList());
            String generatedDl = dlGenerator.generate(design == null || design.isBlank() ? userInput : design, resource);
            dl = generatedDl == null || generatedDl.isBlank() ? fallbackDl() : generatedDl;
            mermaidCode = generateMermaid(dl);
            return Map.of("status", "processing", "state", "processing", "mermaid_code", mermaidCode);
        }

        IntentionDetector.Intention intention = intentionDetector.detect(userInput);
        if (intention == IntentionDetector.Intention.MODIFY_WORKFLOW
                || intention == IntentionDetector.Intention.REFINE_WORKFLOW) {
            String refinedDl = dlGenerator.refine(userInput, resource, dl, mermaidCode == null ? "" : mermaidCode);
            if (refinedDl != null && !refinedDl.isBlank()) {
                dl = refinedDl;
                mermaidCode = generateMermaid(dl);
            }
            return Map.of("status", "refining", "state", "processing", "mermaid_code", mermaidCode);
        }

        state = AgentBuilderEnums.BuildState.COMPLETED;
        return Map.of("status", "completed", "state", "completed", "dsl", dlTransformer.transformToDsl(dl, resource));
    }

    @Override
    protected Map<String, Object> handleCompleted(Map<String, Object> query, List<Map<String, Object>> history) {
        if (dl == null) {
            return Map.of("status", "completed", "state", "completed");
        }
        return Map.of("status", "completed", "state", "completed", "dsl", dlTransformer.transformToDsl(dl, resource));
    }

    private String extractUserInput(Map<String, Object> query) {
        if (query == null) {
            return "";
        }
        Object input = query.get("query");
        if (input == null) {
            input = query.get("input");
        }
        return input != null ? input.toString() : "";
    }

    private void updateWorkflowInfo(Map<String, Object> designInfo) {
        this.workflowName = (String) designInfo.getOrDefault("name", null);
        this.workflowNameEn = (String) designInfo.getOrDefault("name_en", null);
        this.workflowDesc = (String) designInfo.getOrDefault("description", null);
        LOG.debug("Workflow info updated: name={}, name_en={}", workflowName, workflowNameEn);
    }

    @Override
    public void reset() {
        super.reset();
        this.workflowName = null;
        this.workflowNameEn = null;
        this.workflowDesc = null;
        this.dl = null;
        this.mermaidCode = null;
        LOG.debug("WorkflowBuilder state reset");
    }

    /**
     * Mirrors Python's {@code _is_workflow_builder} method.
     */
    public boolean isWorkflowBuilder() {
        return true;
    }

    private String formatToolList() {
        Object plugins = resource.get("plugins");
        if (plugins instanceof Collection<?> collection && !collection.isEmpty()) {
            return String.join("\n", collection.stream().map(Objects::toString).toList());
        }
        return plugins == null ? "" : plugins.toString();
    }

    private String abbreviate(String userInput) {
        if (userInput == null) {
            return "";
        }
        return userInput.length() > 100 ? userInput.substring(0, 100) : userInput;
    }

    private String fallbackDl() {
        return "[{\"id\":\"node_start\",\"type\":\"Start\",\"next\":\"node_end\"}," +
                "{\"id\":\"node_end\",\"type\":\"End\"}]";
    }

    private String generateMermaid(String dlContent) {
        try {
            String mermaid = DlTransformer.transformToMermaid(dlContent);
            CycleChecker.CycleResult cycleResult = cycleChecker.checkAndParse(mermaid);
            if (cycleResult.needRefined()) {
                LOG.warn("Generated Mermaid may contain cycle: {}", cycleResult.loopDesc());
            }
            return mermaid;
        } catch (Exception e) {
            LOG.warn("Failed to transform DL to Mermaid, using fallback graph", e);
            return "graph TD\n  node_start --> node_end";
        }
    }
}
