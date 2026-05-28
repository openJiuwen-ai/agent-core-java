/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.IntentionDetector;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.DlReflector;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    // Workflow metadata
    private String workflowName;
    private String workflowNameEn;
    private String workflowDesc;
    private String dl;
    private String mermaidCode;

    // Components
    private final IntentionDetector intentionDetector;
    private final DlReflector dlReflector;
    private final Object workflowDesigner; // Placeholder for WorkflowDesigner
    private final Object dlGenerator;       // Placeholder for DLGenerator
    private final Object dlTransformer;     // Placeholder for DLTransformer
    private final Object cycleChecker;      // Placeholder for CycleChecker
    private final Object historyManager;    // Placeholder for HistoryManager

    // LLM service
    private final Object llm;

    /**
     * Initialize Workflow builder.
     * <p>
     * Mirrors Python's {@code __init__} method.
     *
     * @param llm            LLM service instance
     * @param historyManager History manager instance
     */
    public WorkflowBuilder(Object llm, Object historyManager) {
        super(null); // BaseAgentBuilder with null ProgressReporter
        this.llm = llm;
        this.historyManager = historyManager;

        // Initialize components
        this.intentionDetector = new IntentionDetector();
        this.dlReflector = new DlReflector();

        // Placeholders for components that need full implementation
        this.workflowDesigner = null; // TODO: WorkflowDesigner(llm)
        this.dlGenerator = null;      // TODO: DLGenerator(llm)
        this.dlTransformer = null;    // TODO: DLTransformer()
        this.cycleChecker = null;     // TODO: CycleChecker(llm)

        LOG.debug("WorkflowBuilder initialized");
    }

    /**
     * Initialize with ProgressReporter.
     *
     * @param progressReporter Progress reporter instance
     */
    public WorkflowBuilder(ProgressReporter progressReporter) {
        super(progressReporter);
        this.llm = null;
        this.historyManager = null;
        this.intentionDetector = new IntentionDetector();
        this.dlReflector = new DlReflector();
        this.workflowDesigner = null;
        this.dlGenerator = null;
        this.dlTransformer = null;
        this.cycleChecker = null;
    }

    // Property getters - Mirrors Python's property methods

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

    public Map<String, Object> getResource() {
        return resource;
    }

    public Object getLlm() {
        return llm;
    }

    @Override
    protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
        LOG.info("[WorkflowBuilder] Handling INITIAL state");

        if (progressReporter != null) {
            progressReporter.report(AgentBuilderEnums.ProgressStage.DETECTING_INTENTION,
                    AgentBuilderEnums.ProgressStatus.RUNNING, "Detecting workflow intention");
        }

        // Check initial instruction
        String userInput = extractUserInput(query);
        boolean hasInstruction = intentionDetector.detect(userInput) != IntentionDetector.Intention.UNKNOWN;

        if (!hasInstruction) {
            if (progressReporter != null) {
                progressReporter.report(AgentBuilderEnums.ProgressStage.DETECTING_INTENTION,
                        AgentBuilderEnums.ProgressStatus.SUCCESS, "More information needed");
            }
            state = AgentBuilderEnums.BuildState.PROCESSING;
            return Map.of("status", "request_content", "state", "processing",
                    "message", "Please provide workflow requirements");
        }

        if (progressReporter != null) {
            progressReporter.report(AgentBuilderEnums.ProgressStage.DETECTING_INTENTION,
                    AgentBuilderEnums.ProgressStatus.SUCCESS, "Intent detection completed");
            progressReporter.report(AgentBuilderEnums.ProgressStage.GENERATING_WORKFLOW_DESIGN,
                    AgentBuilderEnums.ProgressStatus.RUNNING, "Designing workflow");
        }

        // Update workflow info
        updateWorkflowInfo(Map.of(
                "name", userInput.length() > 100 ? userInput.substring(0, 100) : userInput,
                "name_en", "workflow",
                "description", "Workflow design"
        ));

        // Placeholder: Generate DL and Mermaid
        // TODO: Full implementation requires DLGenerator, WorkflowDesigner, etc.
        if (progressReporter != null) {
            progressReporter.report(AgentBuilderEnums.ProgressStage.GENERATING_DL,
                    AgentBuilderEnums.ProgressStatus.RUNNING, "Generating DL");
            progressReporter.report(AgentBuilderEnums.ProgressStage.VALIDATING_DL,
                    AgentBuilderEnums.ProgressStatus.RUNNING, "Validating DL");
        }

        state = AgentBuilderEnums.BuildState.PROCESSING;
        return Map.of("status", "detecting_intention", "state", "processing",
                "workflow_name", workflowName != null ? workflowName : "");
    }

    @Override
    protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
        LOG.info("[WorkflowBuilder] Handling PROCESSING state");

        if (progressReporter != null) {
            progressReporter.report(AgentBuilderEnums.ProgressStage.GENERATING_WORKFLOW_DESIGN,
                    AgentBuilderEnums.ProgressStatus.RUNNING, "Generating workflow design");
        }

        if (dl == null) {
            // Generate new workflow
            String userInput = extractUserInput(query);
            updateWorkflowInfo(Map.of(
                    "name", userInput.length() > 100 ? userInput.substring(0, 100) : userInput,
                    "name_en", "workflow",
                    "description", "Workflow design"
            ));

            if (progressReporter != null) {
                progressReporter.report(AgentBuilderEnums.ProgressStage.GENERATING_DL,
                        AgentBuilderEnums.ProgressStatus.RUNNING, "Generating DL");
            }

            // Placeholder for DL generation
            dl = generatePlaceholderDl(userInput);
            mermaidCode = generatePlaceholderMermaid(dl);

            return Map.of("status", "processing", "state", "processing",
                    "mermaid_code", mermaidCode != null ? mermaidCode : "");
        } else {
            // Refine existing workflow
            IntentionDetector.Intention intention = intentionDetector.detect(extractUserInput(query));

            if (intention == IntentionDetector.Intention.MODIFY_WORKFLOW) {
                if (progressReporter != null) {
                    progressReporter.report(AgentBuilderEnums.ProgressStage.REFINING_DL,
                            AgentBuilderEnums.ProgressStatus.RUNNING, "Refining DL");
                }

                // Placeholder for DL refinement
                return Map.of("status", "refining", "state", "processing",
                        "mermaid_code", mermaidCode != null ? mermaidCode : "");
            } else {
                // Transform to DSL
                if (progressReporter != null) {
                    progressReporter.report(AgentBuilderEnums.ProgressStage.TRANSFORMING_WORKFLOW_DSL,
                            AgentBuilderEnums.ProgressStatus.RUNNING, "Converting to DSL");
                }

                state = AgentBuilderEnums.BuildState.COMPLETED;
                return Map.of("status", "completed", "state", "completed",
                        "dsl", dl != null ? dl : "");
            }
        }
    }

    // Helper methods

    /**
     * Extract user input from query map.
     */
    private String extractUserInput(Map<String, Object> query) {
        if (query == null) return "";
        Object input = query.get("query");
        if (input == null) input = query.get("input");
        return input != null ? input.toString() : "";
    }

    /**
     * Update workflow info from design info.
     * <p>
     * Mirrors Python's {@code _update_workflow_info} method.
     */
    private void updateWorkflowInfo(Map<String, Object> designInfo) {
        this.workflowName = (String) designInfo.getOrDefault("name", null);
        this.workflowNameEn = (String) designInfo.getOrDefault("name_en", null);
        this.workflowDesc = (String) designInfo.getOrDefault("description", null);
        LOG.debug("Workflow info updated: name={}, name_en={}", workflowName, workflowNameEn);
    }

    /**
     * Reset internal state.
     * <p>
     * Mirrors Python's {@code _reset_internal_state} method.
     */
    public void reset() {
        this.workflowName = null;
        this.workflowNameEn = null;
        this.workflowDesc = null;
        this.dl = null;
        this.mermaidCode = null;
        this.state = AgentBuilderEnums.BuildState.INITIAL;
        LOG.debug("WorkflowBuilder state reset");
    }

    /**
     * Check if this is a workflow builder.
     * <p>
     * Mirrors Python's {@code _is_workflow_builder} method.
     */
    public boolean isWorkflowBuilder() {
        return true;
    }

    // Placeholder methods for full implementation

    /**
     * Generate placeholder DL for testing.
     * TODO: Replace with full DLGenerator implementation.
     */
    private String generatePlaceholderDl(String input) {
        return "[{\"id\": \"node_start\", \"type\": \"Start\", \"next\": \"node_end\"}, " +
                "{\"id\": \"node_end\", \"type\": \"End\"}]";
    }

    /**
     * Generate placeholder Mermaid for testing.
     * TODO: Replace with full DLTransformer implementation.
     */
    private String generatePlaceholderMermaid(String dlContent) {
        return "graph TD\n  node_start --> node_end";
    }
}