/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DLTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.WorkflowDesigner;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderConstants;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow builder state machine.
 *
 * <p>Mirrors Python's {@code WorkflowBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/builder.py}.</p>
 */
public class WorkflowBuilder extends BaseAgentBuilder {
    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    private final IntentionDetector intentionDetector;
    private final WorkflowDesigner workflowDesigner;
    private final DLGenerator dlGenerator;
    private final Reflector dlReflector;
    private final DLTransformer dlTransformer;
    private final CycleChecker cycleChecker;

    private String workflowName;
    private String workflowNameEn;
    private String workflowDesc;
    private String dl;
    private String mermaidCode;

    public WorkflowBuilder(Model llm, HistoryManager historyManager) {
        this(llm, historyManager, null);
    }

    public WorkflowBuilder(Model llm, HistoryManager historyManager, ProgressReporter progressReporter) {
        super(llm, historyManager, progressReporter);
        this.intentionDetector = new IntentionDetector(llm);
        this.workflowDesigner = new WorkflowDesigner(llm);
        this.dlGenerator = new DLGenerator(llm);
        this.dlReflector = new Reflector();
        this.dlTransformer = new DLTransformer();
        this.cycleChecker = new CycleChecker(llm);
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

    @Override
    protected String handleInitial(String query, List<Map<String, String>> dialogHistory) {
        String safeQuery = query == null ? "" : query;
        ProgressReporter reporter = getProgressReporter();
        if (reporter != null) {
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.DETECTING_INTENTION,
                    "Detecting user intent...",
                    Map.of("query_length", safeQuery.length()));
        }

        List<Map<String, Object>> objectHistory = toObjectHistory(dialogHistory);
        if (!intentionDetector.detectInitialInstruction(objectHistory)) {
            if (reporter != null) {
                reporter.completeStage("More information needed");
            }
            getHistoryManager().addAssistantMessage(AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT);
            setState(AgentBuilderEnums.BuildState.PROCESSING);
            return AgentBuilderConstants.WORKFLOW_REQUEST_CONTENT;
        }

        if (reporter != null) {
            reporter.completeStage("Intent detection completed");
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.GENERATING_WORKFLOW_DESIGN,
                    "Designing workflow...",
                    Map.of("has_query", !safeQuery.isEmpty()));
        }

        String design = designWorkflow(safeQuery);
        if (reporter != null) {
            reporter.completeStage(
                    "工作流设计完成",
                    Map.of("design_length", design == null ? 0 : design.length()));
        }

        dl = generateAndReflectGenerate(
                AgentBuilderConstants.GENERATE_DL_FROM_DESIGN_CONTENT + Objects.toString(design, ""),
                resourceForDl());
        mermaidCode = generateMermaidWithCycleCheck(dl);

        setState(AgentBuilderEnums.BuildState.PROCESSING);
        return mermaidCode;
    }

    @Override
    protected String handleProcessing(String query, List<Map<String, String>> dialogHistory) {
        String safeQuery = query == null ? "" : query;
        List<Map<String, Object>> objectHistory = toObjectHistory(dialogHistory);
        if (dl == null) {
            String userInput = intentionDetector.detectInitialInstruction(objectHistory)
                    ? safeQuery
                    : formatDialogHistoryLikePython(dialogHistory);
            String design = designWorkflow(userInput);
            dl = generateAndReflectGenerate(
                    AgentBuilderConstants.GENERATE_DL_FROM_DESIGN_CONTENT + Objects.toString(design, ""),
                    resourceForDl());
            mermaidCode = generateMermaidWithCycleCheck(dl);
            return mermaidCode;
        }

        if (intentionDetector.detectRefineIntent(objectHistory, mermaidCode == null ? "" : mermaidCode)) {
            dl = generateAndReflectRefine(
                    safeQuery,
                    resourceForDl(),
                    dl,
                    mermaidCode == null ? "" : mermaidCode);
            mermaidCode = generateMermaidWithCycleCheck(dl);
            return mermaidCode;
        }

        ProgressReporter reporter = getProgressReporter();
        if (reporter != null) {
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.TRANSFORMING_WORKFLOW_DSL,
                    "Converting to workflow DSL...");
        }

        String dsl = dlTransformer.transformToDsl(dl, getResource());
        if (reporter != null) {
            reporter.completeStage("Workflow DSL conversion completed");
        }

        reset();
        return dsl;
    }

    @Override
    protected String handleCompleted(String query, List<Map<String, String>> dialogHistory) {
        if (intentionDetector.detectRefineIntent(toObjectHistory(dialogHistory), mermaidCode == null ? "" : mermaidCode)) {
            setState(AgentBuilderEnums.BuildState.PROCESSING);
            return handleProcessing(query, dialogHistory);
        }
        if (dl != null) {
            return dlTransformer.transformToDsl(dl, getResource());
        }
        return "Workflow build completed";
    }

    @Override
    protected void resetInternalState() {
        workflowName = null;
        workflowNameEn = null;
        workflowDesc = null;
        dl = null;
        mermaidCode = null;
        dlGenerator.getReflectPrompts().clear();
        LOGGER.debug("Workflow builder internal state reset");
    }

    @Override
    protected boolean isWorkflowBuilderInternal() {
        return true;
    }

    private String designWorkflow(String userInput) {
        String toolList = formatToolList();
        String design = workflowDesigner.design(userInput, toolList);
        updateWorkflowInfo(Map.of(
                "name", !userInput.isEmpty() ? truncate(userInput, 100) : "Workflow",
                "name_en", "workflow",
                "description", design != null && !design.isEmpty() ? truncate(design, 300) : "Workflow design"
        ));
        getHistoryManager().addAssistantMessage(
                AgentBuilderConstants.WORKFLOW_DESIGN_RESPONSE_CONTENT + Objects.toString(design, ""));
        return design;
    }

    private String generateAndReflectGenerate(String query, Map<String, List<Map<String, Object>>> resource) {
        return generateAndReflectDl(
                (currentQuery, currentResource, existDl, existMermaid) -> dlGenerator.generate(currentQuery, currentResource),
                query,
                resource,
                null,
                null);
    }

    private String generateAndReflectRefine(String query, Map<String, List<Map<String, Object>>> resource,
                                            String existDl, String existMermaid) {
        return generateAndReflectDl(
                (currentQuery, currentResource, currentDl, currentMermaid) ->
                        dlGenerator.refine(currentQuery, currentResource, currentDl, currentMermaid),
                query,
                resource,
                existDl,
                existMermaid);
    }

    private String generateAndReflectDl(DlOperation operation, String query,
                                        Map<String, List<Map<String, Object>>> resource,
                                        String existDl, String existMermaid) {
        int maxRetries = AgentBuilderConstants.DEFAULT_MAX_RETRIES;
        LOGGER.debug("Starting DL generation max_retries={}", maxRetries);

        ProgressReporter reporter = getProgressReporter();
        if (reporter != null) {
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.GENERATING_DL,
                    "Generating process definition language (DL)...",
                    Map.of("max_retries", maxRetries));
        }

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (reporter != null && attempt > 0) {
                    reporter.updateStage(
                            "Retrying DL generation (attempt " + (attempt + 1) + ")...",
                            Map.of("attempt", attempt + 1, "max_retries", maxRetries),
                            null);
                }

                String generatedDl = AgentBuilderUtils.extractJsonFromText(
                        operation.apply(query, resource, existDl, existMermaid));
                LOGGER.debug(
                        "DL generation completed attempt={} dl_length={}",
                        attempt + 1,
                        generatedDl == null ? 0 : generatedDl.length());

                if (reporter != null) {
                    reporter.startStage(
                            AgentBuilderEnums.ProgressStage.VALIDATING_DL,
                            "Validating DL format...",
                            Map.of("attempt", attempt + 1));
                }

                dlReflector.checkFormat(generatedDl);
                if (dlReflector.getErrors().isEmpty()) {
                    getHistoryManager().addAssistantMessage(generatedDl);
                    LOGGER.info("DL generation succeeded attempt={} max_retries={}", attempt + 1, maxRetries);
                    if (reporter != null) {
                        reporter.completeStage(
                                "DL generation and validation succeeded",
                                Map.of("attempt", attempt + 1));
                    }
                    return generatedDl;
                }

                String errorMessages = String.join(";\n", dlReflector.getErrors());
                LOGGER.warning(
                        "DL format validation failed, preparing retry attempt={} max_retries={} errors={}",
                        attempt + 1,
                        maxRetries,
                        errorMessages);
                if (reporter != null) {
                    reporter.warnStage(
                            "Format validation failed: " + errorMessages,
                            "Optimizing DL (retry " + (attempt + 1) + ")...",
                            Map.of("errors", new ArrayList<>(dlReflector.getErrors()), "attempt", attempt + 1));
                    reporter.startStage(
                            AgentBuilderEnums.ProgressStage.REFINING_DL,
                            "Optimizing DL (retry " + (attempt + 1) + ")...",
                            Map.of("errors", new ArrayList<>(dlReflector.getErrors())));
                }
                dlGenerator.getReflectPrompts().clear();
                dlGenerator.getReflectPrompts().add(new AssistantMessage(generatedDl));
                dlGenerator.getReflectPrompts().add(new UserMessage(AgentBuilderConstants.MODIFY_DL_CONTENT + errorMessages));
                if (attempt < maxRetries - 1) {
                    dlReflector.reset();
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Error during DL generation attempt={} error={}", attempt + 1, exceptionMessage(exception));
                if (attempt == maxRetries - 1) {
                    if (reporter != null) {
                        reporter.failStage(exceptionMessage(exception), "DL generation failed");
                    }
                    throw exception;
                }
            }
        }

        String errorMessages = String.join(";\n", dlReflector.getErrors());
        if (reporter != null) {
            reporter.failStage(errorMessages, "DL generation failed, max retries reached");
        }
        throw new ApplicationError(
                StatusCode.WORKFLOW_DL_GENERATION_ERROR,
                "Process definition language (DL) generation failed, errors: " + errorMessages,
                null,
                null,
                Map.of("error_msg", errorMessages));
    }

    private String generateMermaidWithCycleCheck(String dlContent) {
        int maxRetries = 3;
        int attempts = 0;
        boolean needRefined = true;
        String tempDl = dlContent;

        while (attempts < maxRetries && needRefined) {
            attempts++;
            ProgressReporter reporter = getProgressReporter();
            if (reporter != null) {
                reporter.startStage(
                        AgentBuilderEnums.ProgressStage.TRANSFORMING_MERMAID,
                        "正在从 DL 转换生成流程图（第 " + attempts + " 次）...");
            }

            String currentMermaid = DLTransformer.transformToMermaid(tempDl);
            CycleChecker.CycleResult cycleResult = cycleChecker.checkAndParse(currentMermaid);
            needRefined = cycleResult.needRefined();

            if (needRefined) {
                if (attempts < maxRetries) {
                    String loopDesc = "当前工作流设计方案可能包含环的结构：" + cycleResult.loopDesc() + "\n"
                            + "需严格遵循有向无环图(DAG)原则，可对设计方案进行改造，"
                            + "确保工作流不包含任何闭环或循环结构！！！";
                    if (reporter != null) {
                        reporter.warnStage(
                                "检测到环结构：" + cycleResult.loopDesc(),
                                "正在优化 DL（第 " + (attempts + 1) + " 次尝试）...");
                    }
                    tempDl = generateAndReflectRefine(
                            "请修复以下流程图中的环结构：" + loopDesc,
                            resourceForDl(),
                            tempDl,
                            currentMermaid);
                } else {
                    throw workflowInputError("已达到最大重试次数，无法生成无环流程图", maxRetries);
                }
            } else {
                LOGGER.info("Successfully generated acyclic flowchart");
                if (reporter != null) {
                    reporter.completeStage("流程图生成完成");
                }
                return currentMermaid;
            }
        }

        throw workflowInputError("无法生成无环流程图", maxRetries);
    }

    private String formatToolList() {
        Object plugins = getResource().getOrDefault("plugins", List.of());
        if (!(plugins instanceof Collection<?> collection) || collection.isEmpty()) {
            return "";
        }
        List<String> formatted = new ArrayList<>();
        for (Object plugin : collection) {
            formatted.add(pythonRepr(plugin));
        }
        return String.join("\n", formatted);
    }

    private void updateWorkflowInfo(Map<String, Object> designInfo) {
        workflowName = stringOrNull(designInfo.get("name"));
        workflowNameEn = stringOrNull(designInfo.get("name_en"));
        workflowDesc = stringOrNull(designInfo.get("description"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> resourceForDl() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        Object plugins = getResource().get("plugins");
        if (plugins instanceof List<?> list) {
            List<Map<String, Object>> pluginList = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> converted = new LinkedHashMap<>();
                    map.forEach((key, value) -> converted.put(String.valueOf(key), value));
                    pluginList.add(converted);
                }
            }
            result.put("plugins", pluginList);
        }
        return result;
    }

    private static ApplicationError workflowInputError(String message, int maxRetries) {
        return new ApplicationError(
                StatusCode.WORKFLOW_EXECUTE_INPUT_INVALID,
                message,
                Map.of(
                        "max_retries", maxRetries,
                        "error_code", StatusCode.WORKFLOW_DL_GENERATION_ERROR.getCode()),
                null,
                Map.of("error_msg", message));
    }

    private static List<Map<String, Object>> toObjectHistory(List<Map<String, String>> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Map<String, String> item : source) {
            result.add(new LinkedHashMap<>(item));
        }
        return result;
    }

    private static String formatDialogHistoryLikePython(List<Map<String, String>> dialogHistory) {
        if (dialogHistory == null || dialogHistory.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, String> message : dialogHistory) {
            lines.add(message.getOrDefault("role", "user") + ": " + message.getOrDefault("content", ""));
        }
        return String.join("\n", lines);
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String pythonRepr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(pythonRepr(String.valueOf(entry.getKey())) + ": " + pythonRepr(entry.getValue()));
            }
            return "{" + String.join(", ", parts) + "}";
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object item : collection) {
                parts.add(pythonRepr(item));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        return String.valueOf(value);
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof java.util.concurrent.CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }

    @FunctionalInterface
    private interface DlOperation {
        String apply(String query, Map<String, List<Map<String, Object>>> resource, String existDl, String existMermaid);
    }
}
