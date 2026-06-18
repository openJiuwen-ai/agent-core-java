/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeTools;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for the browser subagent.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.browser_agent} in
 * {@code openjiuwen/harness/subagents/browser_agent.py}.</p>
 */
public final class BrowserAgentFactory {

    public static final String BROWSER_AGENT_FACTORY_NAME = "browser_agent";

    public static final Map<String, String> DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", "你是浏览器自动化代理，负责直接执行网页任务。请使用 Playwright 浏览器工具和已批准的运行时辅助工具完成导航、点击、输入、选择、检查和信息提取。",
            "en", "You are a browser automation agent responsible for executing web tasks directly. "
                    + "Use Playwright browser tools and approved runtime helper tools for navigation, interaction, and extraction."
    );
    public static final Map<String, String> DEFAULT_BROWSER_AGENT_DESCRIPTION = Map.of(
            "cn", "专用浏览器子代理，直接使用 Playwright MCP 工具执行网页任务。",
            "en", "Dedicated browser subagent that directly controls the browser with Playwright MCP tools."
    );

    private BrowserAgentFactory() {
    }

    public static DeepAgentConfig.SubAgentConfig buildBrowserAgentConfig(Object model) {
        return buildBrowserAgentConfig(model, null, null, null, null, null, null, "cn", false, 25);
    }

    public static DeepAgentConfig.SubAgentConfig buildBrowserAgentConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            RuntimeSettings settings,
            String language,
            boolean enableTaskLoop,
            int maxIterations
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        RuntimeSettings resolvedSettings = settings == null ? BrowserRuntimeConfig.buildRuntimeSettings() : settings;
        AgentCard finalCard = card == null
                ? new AgentCard(BROWSER_AGENT_FACTORY_NAME, BROWSER_AGENT_FACTORY_NAME,
                DEFAULT_BROWSER_AGENT_DESCRIPTION.get(resolvedLanguage))
                : card;
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        config.setLanguage(resolvedLanguage);
        config.setSystemPrompt(systemPrompt == null
                ? DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get(resolvedLanguage)
                : systemPrompt);
        config.setTools(tools == null ? List.of() : List.copyOf(tools));
        config.setRails(rails == null ? List.of() : List.copyOf(rails));
        config.setEnableTaskLoop(enableTaskLoop);

        DeepAgentConfig.SubAgentConfig spec = new DeepAgentConfig.SubAgentConfig(
                finalCard.getName(),
                finalCard.getDescription(),
                config.getSystemPrompt()
        );
        spec.setCard(finalCard);
        spec.setConfig(config);
        spec.setTools(config.getTools());
        spec.setMcps(toObjectList(mcps));
        spec.setModel(model);
        spec.setRails(config.getRails());
        spec.setLanguage(resolvedLanguage);
        spec.setEnableTaskLoop(enableTaskLoop);
        spec.setMaxIterations(maxIterations);
        spec.setFactoryName(BROWSER_AGENT_FACTORY_NAME);
        spec.setFactoryKwargs(Map.of("settings", resolvedSettings));
        Map<String, Object> metadata = new LinkedHashMap<>(ExploreAgent.metadata(
                BROWSER_AGENT_FACTORY_NAME, maxIterations, mcps));
        metadata.put("settings", resolvedSettings);
        spec.setMetadata(metadata);
        return spec;
    }

    public static DeepAgent createBrowserAgent(
            Object model,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            AgentCard card,
            String language,
            RuntimeSettings settings
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        RuntimeSettings resolvedSettings = settings == null ? BrowserRuntimeConfig.buildRuntimeSettings() : settings;
        BrowserAgentRuntime runtime = new BrowserAgentRuntime(
                resolvedSettings.getProvider(),
                resolvedSettings.getApiKey(),
                resolvedSettings.getApiBase(),
                resolvedSettings.getModelName(),
                resolvedSettings.getMcpConfig(),
                resolvedSettings.getGuardrails()
        );

        List<Tool> finalTools = new ArrayList<>();
        if (tools != null) {
            finalTools.addAll(tools);
        }
        finalTools.addAll(BrowserRuntimeTools.buildBrowserRuntimeTools(runtime, resolvedLanguage));

        List<DeepAgentRail> finalRails = new ArrayList<>();
        if (rails != null) {
            finalRails.addAll(rails);
        }
        finalRails.add(new BrowserRuntimeRail(runtime));

        DeepAgentConfig.SubAgentConfig spec = buildBrowserAgentConfig(
                model, card, null, finalTools, mcps, finalRails, resolvedSettings, resolvedLanguage, false, 25);
        DeepAgent agent = new DeepAgent(spec.getCard());
        agent.configure(spec.getConfig());
        return agent;
    }

    private static List<Object> toObjectList(List<?> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
