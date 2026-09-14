/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates complete agent configuration from clarification results.
 *
 * <p>Mirrors Python's {@code Generator} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/generator.py}.</p>
 */
public class Generator {
    public static final Map<String, String> EXTRACT_ELEMENTS = extractElements();

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    private final Model llm;

    public Generator(Model llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public Model getLlm() {
        return llm;
    }

    public static Map<String, Object> parseInfo(String content) {
        Map<String, Object> infoDict = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : EXTRACT_ELEMENTS.entrySet()) {
            infoDict.put(entry.getKey(), parseElement(content, entry.getValue()));
        }
        infoDict.put("plugin", parseElement(content, "选择的插件列表"));
        infoDict.put("knowledge", parseElement(content, "选择的知识库列表"));
        infoDict.put("workflow", parseElement(content, "选择的工作流列表"));
        return infoDict;
    }

    public Map<String, Object> generate(String message, String agentConfigInfo, String agentResourceInfo) {
        return generate(message, agentConfigInfo, agentResourceInfo, null);
    }

    public Map<String, Object> generate(String message, String agentConfigInfo, String agentResourceInfo,
                                        Map<String, ?> resourceIdDict) {
        List<BaseMessage> userMessages = LlmAgentPrompts.GENERATE_USER_PROMPT_TEMPLATE.format(Map.of(
                "user_message", message == null ? "" : message,
                "agent_config_info", agentConfigInfo == null ? "" : agentConfigInfo,
                "agent_resource_info", agentResourceInfo == null ? "" : agentResourceInfo
        )).toMessages();

        List<BaseMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT));
        messages.addAll(userMessages);

        AssistantMessage response = llm.invoke(messages).toCompletableFuture().join();
        String generatedContent = Objects.toString(response == null ? null : response.getContent(), "");
        LOGGER.debug("Generated Agent configuration, output_length={}", generatedContent.length());

        Map<String, Object> contentParse = parseInfo(generatedContent);
        if (resourceIdDict != null && !resourceIdDict.isEmpty()) {
            contentParse.put("plugin", resourceValue(resourceIdDict, "plugin"));
            contentParse.put("knowledge", resourceValue(resourceIdDict, "knowledge"));
            contentParse.put("workflow", resourceValue(resourceIdDict, "workflow"));
        }
        return contentParse;
    }

    private static Object resourceValue(Map<String, ?> resourceIdDict, String key) {
        Object value = resourceIdDict.get(key);
        return value == null ? List.of() : value;
    }

    private static String parseElement(String content, String key) {
        if (content == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("<" + Pattern.quote(key) + ">(.*?)</" + Pattern.quote(key) + ">",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        String result = matcher.group(1).strip();
        if (result.length() >= 2 && result.charAt(0) == '"' && result.charAt(result.length() - 1) == '"') {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static Map<String, String> extractElements() {
        Map<String, String> elements = new LinkedHashMap<>();
        elements.put("name", "角色名称");
        elements.put("description", "角色描述");
        elements.put("prompt", "提示词");
        elements.put("opening_remarks", "智能体开场白");
        elements.put("question", "预置问题");
        return elements;
    }
}
