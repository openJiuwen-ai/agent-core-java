/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.Model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration generator for LLM agent builder.
 * <p>
 * Mirrors Python's {@code Generator} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent.generator}.
 */
public class Generator {

    public static final Map<String, String> EXTRACT_ELEMENTS = Map.of(
            "name", "角色名称",
            "description", "角色描述",
            "prompt", "提示词",
            "opening_remarks", "智能体开场白",
            "question", "预置问题"
    );

    private final Model llm;

    public Generator(Model llm) {
        this.llm = llm;
    }

    public Model getLlm() {
        return llm;
    }

    public static Map<String, Object> parseInfo(String content) {
        String safeContent = content == null ? "" : content;
        Map<String, Object> info = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : EXTRACT_ELEMENTS.entrySet()) {
            info.put(entry.getKey(), parseElement(safeContent, entry.getValue()));
        }
        info.put("plugin", parseElement(safeContent, "选择的插件列表"));
        info.put("knowledge", parseElement(safeContent, "选择的知识库列表"));
        info.put("workflow", parseElement(safeContent, "选择的工作流列表"));
        return info;
    }

    public Map<String, Object> generate(
            String message,
            String agentConfigInfo,
            String agentResourceInfo,
            Map<String, Object> resourceIdDict) {
        Map<String, Object> parsed = parseInfo(agentConfigInfo == null ? "" : agentConfigInfo);
        if (resourceIdDict != null && !resourceIdDict.isEmpty()) {
            parsed.put("plugin", resourceIdDict.getOrDefault("plugin", parsed.get("plugin")));
            parsed.put("knowledge", resourceIdDict.getOrDefault("knowledge", parsed.get("knowledge")));
            parsed.put("workflow", resourceIdDict.getOrDefault("workflow", parsed.get("workflow")));
        }
        return parsed;
    }

    private static String parseElement(String content, String key) {
        Pattern pattern = Pattern.compile("<" + Pattern.quote(key) + ">(.*?)</" + Pattern.quote(key) + ">",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        String result = matcher.group(1).trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }
}
