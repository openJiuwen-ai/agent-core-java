/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.resource.ResourceRetriever;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * LLM agent builder state machine.
 *
 * <p>Mirrors Python's {@code LlmAgentBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/builder.py}.</p>
 */
public class LlmAgentBuilder extends BaseAgentBuilder {
    public static final Map<String, String> RESOURCE_UNIQUE_KEY = Map.of("plugins", "tool_id");

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    private final ResourceRetriever retriever;
    private final Clarifier clarifier;
    private final Generator generator;
    private final Transformer transformer;
    private final IntentionDetector intentionDetector;
    private String agentConfigInfo;
    private String factorOutputInfo;
    private String displayResourceInfo;
    private Map<String, List<String>> resourceIdDictInfo;

    public LlmAgentBuilder(Model llm, HistoryManager historyManager) {
        this(llm, historyManager, null);
    }

    public LlmAgentBuilder(Model llm, HistoryManager historyManager, ProgressReporter progressReporter) {
        super(llm, historyManager, progressReporter);
        this.retriever = new ResourceRetriever(llm);
        this.clarifier = new Clarifier(llm);
        this.generator = new Generator(llm);
        this.transformer = new Transformer();
        this.intentionDetector = new IntentionDetector(llm);
    }

    public String getAgentConfigInfo() {
        return agentConfigInfo;
    }

    public String getFactorOutputInfo() {
        return factorOutputInfo;
    }

    public String getDisplayResourceInfo() {
        return displayResourceInfo;
    }

    public Map<String, List<String>> getResourceIdDictInfo() {
        if (resourceIdDictInfo == null) {
            return null;
        }
        return new LinkedHashMap<>(resourceIdDictInfo);
    }

    @Override
    protected String handleInitial(String query, List<Map<String, String>> dialogHistory) {
        String safeQuery = query == null ? "" : query;
        LOGGER.info("Start clarifying requirements query_length={}", safeQuery.length());

        ProgressReporter reporter = getProgressReporter();
        if (reporter != null) {
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    "正在分析需求并澄清问题...",
                    Map.of("query_length", safeQuery.length()));
        }

        String messages = AgentBuilderUtils.formatDialogHistory(toWildcardList(dialogHistory));
        Clarifier.ClarifyResult result = clarifier.clarify(messages, getResource());
        factorOutputInfo = result.factorOutput();
        displayResourceInfo = result.displayResource();
        resourceIdDictInfo = result.resourceIdDict();

        if (reporter != null) {
            reporter.updateStage(
                    "需求澄清完成，正在整理信息...",
                    Map.of("has_resources", displayResourceInfo != null && !displayResourceInfo.isEmpty()),
                    null);
        }

        String response = factorOutputInfo == null ? "" : factorOutputInfo;
        if (displayResourceInfo != null && !displayResourceInfo.isEmpty()) {
            response += "\n\n" + displayResourceInfo;
        }

        getHistoryManager().addAssistantMessage(response);
        agentConfigInfo = factorOutputInfo;

        if (reporter != null) {
            reporter.completeStage(
                    "需求澄清完成",
                    Map.of("resource_count", resourceIdDictInfo == null ? 0 : resourceIdDictInfo.size()));
        }

        setState(AgentBuilderEnums.BuildState.PROCESSING);
        return response;
    }

    @Override
    protected String handleProcessing(String query, List<Map<String, String>> dialogHistory) {
        String safeQuery = query == null ? "" : query;
        LOGGER.info("Start processing LLM agent build request");
        String messages = AgentBuilderUtils.formatDialogHistory(toWildcardList(dialogHistory));

        if (intentionDetector.detectRefineIntent(safeQuery, agentConfigInfo == null ? "" : agentConfigInfo)) {
            return handleRefine(messages);
        }

        LOGGER.info("Start generating DSL");
        ProgressReporter reporter = getProgressReporter();
        if (reporter != null) {
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.GENERATING_CONFIG,
                    "正在生成智能体配置...",
                    Map.of("has_config_info", agentConfigInfo != null && !agentConfigInfo.isEmpty()));
        }

        Map<String, Object> constructorOutput = generator.generate(
                messages,
                agentConfigInfo == null ? "" : agentConfigInfo,
                displayResourceInfo == null ? "" : displayResourceInfo,
                resourceIdDictInfo == null ? Map.of() : resourceIdDictInfo);

        if (reporter != null) {
            reporter.completeStage("配置生成完成");
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.TRANSFORMING_DSL,
                    "正在转换为 DSL 格式...",
                    Map.of("output_length", String.valueOf(constructorOutput).length()));
        }

        String dsl = transformer.transformToDsl(constructorOutput, getResource());
        if (reporter != null) {
            reporter.completeStage("DSL 转换完成");
        }

        reset();
        return dsl;
    }

    @Override
    protected String handleCompleted(String query, List<Map<String, String>> dialogHistory) {
        LOGGER.warning("LLM Agent should not enter COMPLETED state, re-executing processing logic");
        return handleProcessing(query, dialogHistory);
    }

    @Override
    protected void resetInternalState() {
        agentConfigInfo = null;
        factorOutputInfo = null;
        displayResourceInfo = null;
        resourceIdDictInfo = null;
        LOGGER.debug("LLM Agent builder internal state reset");
    }

    @Override
    protected void updateResource(List<Map<String, String>> dialogHistory) {
        try {
            Map<String, Object> newResource = retriever.retrieve(toWildcardList(dialogHistory), isWorkflowBuilder());
            for (Map.Entry<String, Object> entry : newResource.entrySet()) {
                mergeResourceValue(entry.getKey(), entry.getValue());
            }
            LOGGER.debug("Resource update completed resource_keys={}", getResource().keySet());
        } catch (RuntimeException exception) {
            LOGGER.warning(
                    "Resource update failed, continuing with existing resources error={}",
                    exceptionMessage(exception));
        }
    }

    @Override
    protected boolean isWorkflowBuilderInternal() {
        return false;
    }

    private String handleRefine(String messages) {
        LOGGER.info("Detected refinement intent, clarifying requirements again");

        ProgressReporter reporter = getProgressReporter();
        if (reporter != null) {
            reporter.startStage(
                    AgentBuilderEnums.ProgressStage.CLARIFYING,
                    "检测到优化意图，正在重新澄清需求...");
        }

        Clarifier.ClarifyResult result = clarifier.clarify(messages, getResource());
        factorOutputInfo = result.factorOutput();
        displayResourceInfo = result.displayResource();
        resourceIdDictInfo = result.resourceIdDict();

        if (reporter != null) {
            reporter.completeStage(
                    "需求重新澄清完成",
                    Map.of("has_resources", displayResourceInfo != null && !displayResourceInfo.isEmpty()));
        }

        agentConfigInfo = factorOutputInfo;
        getHistoryManager().addAssistantMessage(agentConfigInfo == null ? "" : agentConfigInfo);
        LOGGER.debug("Refined agent config: {}", agentConfigInfo);
        LOGGER.debug("Refined resource display: {}", displayResourceInfo);
        return factorOutputInfo == null ? "" : factorOutputInfo;
    }

    @SuppressWarnings("unchecked")
    private void mergeResourceValue(String key, Object value) {
        Map<String, Object> resource = getResource();
        if (!resource.containsKey(key)) {
            resource.put(key, value);
            return;
        }

        Object existing = resource.get(key);
        if (value instanceof Map<?, ?> valueMap) {
            if (existing instanceof Map<?, ?> existingMap) {
                ((Map<Object, Object>) existingMap).putAll(valueMap);
            } else {
                resource.put(key, value);
            }
            return;
        }

        if (!(value instanceof List<?> valueList)) {
            resource.put(key, value);
            return;
        }

        String uniqueKey = RESOURCE_UNIQUE_KEY.get(key);
        if (uniqueKey == null) {
            return;
        }

        List<Object> existingList;
        if (existing instanceof List<?> list) {
            existingList = new ArrayList<>((List<Object>) list);
            resource.put(key, existingList);
        } else {
            existingList = new ArrayList<>();
            resource.put(key, existingList);
        }

        Set<Object> existingKeys = new LinkedHashSet<>();
        for (Object item : existingList) {
            if (item instanceof Map<?, ?> map) {
                Object itemKey = map.get(uniqueKey);
                if (itemKey != null) {
                    existingKeys.add(itemKey);
                }
            }
        }

        for (Object item : valueList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object itemKey = map.get(uniqueKey);
            if (itemKey == null || existingKeys.contains(itemKey)) {
                continue;
            }
            existingList.add(Objects.requireNonNull(item));
            existingKeys.add(itemKey);
        }
    }

    private static List<Map<String, ?>> toWildcardList(List<Map<String, String>> source) {
        List<Map<String, ?>> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Map<String, String> item : source) {
            result.add(item);
        }
        return result;
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof java.util.concurrent.CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }
}
