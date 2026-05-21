/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.dev_tools.agent_builder.builders.llm_agent.LlmAgentPrompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Requirement Clarifier for LLM Agent Builder.
 * <p>
 * Responsible for analyzing user requirements, extracting Agent basic elements,
 * and planning required resources.
 * <p>
 * Mirrors Python's {@code Clarifier} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent.clarifier}.
 */
public class Clarifier {

    private static final Logger LOG = LoggerFactory.getLogger(Clarifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Map<String, String>> RESOURCE_CONFIG = new LinkedHashMap<>();

    static {
        Map<String, String> pluginConfig = new LinkedHashMap<>();
        pluginConfig.put("label", "插件");
        pluginConfig.put("id_key", "tool_id");
        pluginConfig.put("name_key", "tool_name");
        pluginConfig.put("desc_key", "tool_desc");
        RESOURCE_CONFIG.put("plugin", pluginConfig);

        Map<String, String> knowledgeConfig = new LinkedHashMap<>();
        knowledgeConfig.put("label", "知识库");
        knowledgeConfig.put("id_key", "knowledge_id");
        knowledgeConfig.put("name_key", "knowledge_name");
        knowledgeConfig.put("desc_key", "knowledge_desc");
        RESOURCE_CONFIG.put("knowledge", knowledgeConfig);

        Map<String, String> workflowConfig = new LinkedHashMap<>();
        workflowConfig.put("label", "工作流");
        workflowConfig.put("id_key", "workflow_id");
        workflowConfig.put("name_key", "workflow_name");
        workflowConfig.put("desc_key", "workflow_desc");
        RESOURCE_CONFIG.put("workflow", workflowConfig);
    }

    private final Model llm;

    public Clarifier(Model llm) {
        this.llm = llm;
    }

    /**
     * Parse resource planning output.
     *
     * @param resourceOutput    Resource planning text returned by LLM
     * @param availableResources Available resources dict
     * @return Map containing display content and resource ID dict
     */
    public static Map<String, Object> parseResourceOutput(
            String resourceOutput,
            Map<String, Object> availableResources) {

        if (resourceOutput == null || !resourceOutput.contains("## Agent资源规划")) {
            return Map.of("display_content", "", "id_dict", Map.of());
        }

        String resourcePlanning = resourceOutput.split("## Agent资源规划")[1].strip();

        List<String> displayContent = new ArrayList<>();
        Map<String, List<String>> idDict = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : RESOURCE_CONFIG.entrySet()) {
            String resourceType = entry.getKey();
            Map<String, String> config = entry.getValue();

            String sectionStart = "【选择的" + config.get("label") + "】";
            if (!resourcePlanning.contains(sectionStart)) {
                continue;
            }

            String sectionContent = resourcePlanning.split(sectionStart)[1];
            if (sectionContent.contains("【选择")) {
                sectionContent = sectionContent.split("【选择")[0].strip();
            }

            try {
                List<Map<String, Object>> resourceList = parseResourceList(sectionContent);
                if (resourceList == null || resourceList.isEmpty()) {
                    continue;
                }

                String availableKey = resourceType.equals("plugin") ? "plugins" : resourceType;
                Set<String> availableIds = new java.util.HashSet<>();

                if (availableResources.containsKey(availableKey)) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) availableResources.get(availableKey);
                    for (Map<String, Object> item : items) {
                        String itemId = (String) item.get(config.get("id_key"));
                        if (itemId != null) {
                            availableIds.add(itemId);
                        }
                    }
                }

                List<String> validResources = new ArrayList<>();
                List<String> idList = new ArrayList<>();

                int idx = 1;
                for (Map<String, Object> resource : resourceList) {
                    String name = (String) resource.getOrDefault(config.get("name_key"), "");
                    String desc = (String) resource.getOrDefault(config.get("desc_key"), "");
                    String resourceId = (String) resource.get(config.get("id_key"));

                    if (resourceId != null && availableIds.contains(resourceId)) {
                        if (!name.isEmpty() && !desc.isEmpty()) {
                            validResources.add(idx + ". " + name + ": " + desc);
                        }
                        idList.add(resourceId);
                        idx++;
                    } else {
                        LOG.warn("Resource ID {} not in available resources for type {}", resourceId, resourceType);
                    }
                }

                if (!validResources.isEmpty()) {
                    displayContent.add("【选择的" + config.get("label") + "】\n" + String.join("\n", validResources));
                }
                if (!idList.isEmpty()) {
                    idDict.put(resourceType, idList);
                }

            } catch (Exception e) {
                LOG.error("Resource parsing failed for type {}: {}", resourceType, e.getMessage());
                throw ErrorHelper.buildError(StatusCode.AGENT_BUILDER_RESOURCE_PARSE_ERROR,
                        "error_msg", "Resource parsing exception: " + e.getMessage());
            }
        }

        return Map.of(
                "display_content", String.join("\n\n", displayContent),
                "id_dict", idDict
        );
    }

    /**
     * Parse resource list from string representation.
     */
    private static List<Map<String, Object>> parseResourceList(String content) {
        try {
            // Simple parsing for list format like [{...}, {...}]
            content = content.trim();
            if (!content.startsWith("[") || !content.endsWith("]")) {
                return null;
            }
            return MAPPER.readValue(content,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            LOG.warn("Failed to parse resource list: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Clarify user requirements and plan resources.
     *
     * @param messages  User messages
     * @param resource  Available resources
     * @return Clarification result containing factor output and resource planning
     */
    public Map<String, Object> clarify(String messages, Map<String, Object> resource) {
        // Step 1: Factor analysis
        String factorOutput = analyzeFactors(messages);

        // Step 2: Resource planning
        String resourceOutput = planResources(factorOutput, resource);

        // Step 3: Parse resource output
        Map<String, Object> parsedResource = parseResourceOutput(resourceOutput, resource);

        return Map.of(
                "factor_output", factorOutput,
                "resource_output", resourceOutput,
                "display_resource", parsedResource.get("display_content"),
                "resource_id_dict", parsedResource.get("id_dict")
        );
    }

    /**
     * Analyze agent factors from user messages.
     */
    private String analyzeFactors(String messages) {
        SystemMessage systemMessage = new SystemMessage(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT);
        // Invoke LLM with system prompt and user messages
        // Placeholder for actual LLM invocation
        return "Factor analysis placeholder";
    }

    /**
     * Plan resources based on factor analysis.
     */
    private String planResources(String factorOutput, Map<String, Object> resource) {
        SystemMessage systemMessage = new SystemMessage(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT);
        // Invoke LLM with system prompt, factor output, and resource info
        // Placeholder for actual LLM invocation
        return "Resource planning placeholder";
    }
}