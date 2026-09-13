/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.browser.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeTools;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BrowserAgentFactory.
 * 
 * @since 0.1.7
 */
public final class BrowserAgentFactory {
    /**
     * BROWSER_AGENT_FACTORY_NAME.
     * 
     * @since 0.1.7
     */
    public static final String BROWSER_AGENT_FACTORY_NAME = "browser_agent";

    /**
     * DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.
     * 
     * @since 0.1.7
     */
    public static final Map<String, String> DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT = Map.of("en",
            "You are a browser automation agent responsible for executing web tasks directly. "
                    + "Plan and decide at this agent level, then use Playwright browser tools to navigate, click, "
                    + "type, " + "select, inspect, and extract information. "
                    + "Use browser_custom_action only for deterministic helper actions that are awkward to express "
                    + "with " + "the primitive browser tools. "
                    + "Do not assume a nested browser worker or browser_run_task wrapper exists. "
                    + "Avoid redundant actions, preserve session continuity, and only claim completion when the "
                    + "requested browser outcome is actually evidenced.",
            "cn", "你是浏览器自动化代理，负责执行网页任务。" + "请使用浏览器工具完成导航、交互和信息提取。" + "每次请求优先发起一次完整的浏览器任务调用。" + "请如实、简洁地汇报结果。");

    /**
     * DEFAULT_BROWSER_AGENT_DESCRIPTION.
     * 
     * @since 0.1.7
     */
    public static final Map<String, String> DEFAULT_BROWSER_AGENT_DESCRIPTION =
        Map.of("en", "Dedicated browser subagent that directly controls the browser with Playwright MCP tools.", "cn",
                "专用浏览器子代理，直接使用 Playwright MCP 工具执行网页任务。");

    /**
     * BrowserAgentFactory.
     * 
     * @since 0.1.7
     */
    private BrowserAgentFactory() {
    }

    /**
     * buildBrowserAgentConfig.
     * 
     * @param settings settings
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildBrowserAgentConfig(BrowserRuntimeSettings settings, String language) {
        return buildBrowserAgentConfig(settings, language, Map.of());
    }

    /**
     * buildBrowserAgentConfig.
     * 
     * @param settings settings
     * @param language language
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildBrowserAgentConfig(BrowserRuntimeSettings settings, String language,
            Map<String, Object> factoryKwargs) {
        String isResolved = language != null ? language : "cn";
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("settings", settings);
        if (factoryKwargs != null) {
            kwargs.putAll(factoryKwargs);
        }
        SubAgentConfig config = SubAgentConfig.builder()
                .agentCard(SubAgentFactoryKwargsSupport.resolveAgentCard(kwargs, "browser_agent",
                        DEFAULT_BROWSER_AGENT_DESCRIPTION.getOrDefault(isResolved,
                                DEFAULT_BROWSER_AGENT_DESCRIPTION.get("cn"))))
                .systemPrompt(SubAgentFactoryKwargsSupport.systemPrompt(kwargs,
                        DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.getOrDefault(isResolved,
                                DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("cn"))))
                .language(isResolved).maxIterations(SubAgentFactoryKwargsSupport.maxIterations(kwargs, 15))
                .factoryName(BROWSER_AGENT_FACTORY_NAME).factoryKwargs(kwargs)
                .rails(SubAgentRailMergeSupport.mergeRails(List.of(), kwargs)).build();
        SubAgentFactoryKwargsSupport.applyCommonOverrides(config, kwargs);
        return config;
    }

    /**
     * createBrowserAgent.
     * 
     * @param settings settings
     * @param language language
     * @param workspace workspace
     * @param tools tools
     * @param subagents subagents
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createBrowserAgent(BrowserRuntimeSettings settings, String language, Workspace workspace,
            List<Object> tools, List<DeepAgent> subagents) {
        return createBrowserAgent(settings, language, workspace, tools, subagents, Map.of());
    }

    /**
     * createBrowserAgent.
     * 
     * @param settings settings
     * @param language language
     * @param workspace workspace
     * @param tools tools
     * @param subagents subagents
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createBrowserAgent(BrowserRuntimeSettings settings, String language, Workspace workspace,
            List<Object> tools, List<DeepAgent> subagents, Map<String, Object> factoryKwargs) {
        SubAgentConfig spec = buildBrowserAgentConfig(settings, language, factoryKwargs);
        BrowserAgentRuntime runtime = new BrowserAgentRuntime(settings.getProvider(), settings.getApiKey(),
                settings.getApiBase(), settings.getModelName(), settings.getMcpCfg(),
                settings.getGuardrails() != null ? settings.getGuardrails() : BrowserRunGuardrails.builder().build());
        runtime.ensureStarted();
        List<Object> browserTools =
            new ArrayList<>(BrowserRuntimeTools.buildBrowserRuntimeToolFunctions(runtime, spec.getAgentCard().getId()));
        if (tools != null) {
            browserTools.addAll(tools);
        }
        DeepAgentConfig config = spec.toDeepAgentConfig();
        config.setTools(browserTools);
        if (subagents != null) {
            config.setSubagents(new ArrayList<>(subagents));
        }
        BrowserRuntimeRail runtimeRail = new BrowserRuntimeRail(runtime);
        List<Object> rails = SubAgentRailMergeSupport.mergeRails(List.of(runtimeRail), config.getFactoryKwargs());
        if (rails.stream().noneMatch(BrowserRuntimeRail.class::isInstance)) {
            rails.add(0, runtimeRail);
        }
        config.setRails(rails);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), config, workspace);
    }
}
