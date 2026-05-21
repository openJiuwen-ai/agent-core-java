/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

/**
 * Factory helpers for the browser subagent.
 *
 * <p>Provides configuration and creation helpers for browser automation agents
 * that directly control the browser with Playwright MCP tools.
 *
 * <p>Mirrors Python's {@code browser_agent} module in
 * {@code openjiuwen.harness.subagents.browser_agent}.
 */
public final class BrowserAgentFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserAgentFactory.class);

    public static final String BROWSER_AGENT_FACTORY_NAME = "browser_agent";

    /** Default system prompts by language. */
    public static final Map<String, String> DEFAULT_SYSTEM_PROMPTS = Map.of(
            "cn", "你是浏览器自动化代理，负责执行网页任务。请使用浏览器工具完成导航、交互和信息提取。每次请求优先发起一次完整的浏览器任务调用。请如实、简洁地汇报结果。",
            "en", "You are a browser automation agent responsible for executing web tasks directly. " +
                    "Plan and decide at this agent level, then use Playwright browser tools to navigate, click, type, " +
                    "select, inspect, and extract information. Use browser_custom_action only for deterministic helper " +
                    "actions that are awkward to express with the primitive browser tools. " +
                    "Do not assume a nested browser worker or browser_run_task wrapper exists. " +
                    "Avoid redundant actions, preserve session continuity, and only claim completion when the " +
                    "requested browser outcome is actually evidenced."
    );

    /** Default descriptions by language. */
    public static final Map<String, String> DEFAULT_DESCRIPTIONS = Map.of(
            "cn", "专用浏览器子代理，直接使用 Playwright MCP 工具执行网页任务。",
            "en", "Dedicated browser subagent that directly controls the browser with Playwright MCP tools."
    );

    private BrowserAgentFactory() {
    }

    /**
     * Resolve language to supported value.
     */
    public static String resolveLanguage(String language) {
        if ("cn".equals(language) || "en".equals(language)) {
            return language;
        }
        return "cn";
    }

    /**
     * Get default system prompt for language.
     */
    public static String getDefaultSystemPrompt(String language) {
        return DEFAULT_SYSTEM_PROMPTS.getOrDefault(resolveLanguage(language), DEFAULT_SYSTEM_PROMPTS.get("cn"));
    }

    /**
     * Get default description for language.
     */
    public static String getDefaultDescription(String language) {
        return DEFAULT_DESCRIPTIONS.getOrDefault(resolveLanguage(language), DEFAULT_DESCRIPTIONS.get("cn"));
    }

    /**
     * Browser agent configuration.
     */
    @Data
    @Builder
    public static class BrowserAgentConfig {
        private String name;
        private String description;
        private String systemPrompt;
        private Object model;
        private List<Object> tools;
        private List<Object> mcps;
        private List<Object> rails;
        private boolean enableTaskLoop;
        private int maxIterations;
        private Object workspace;
        private List<String> skills;
        private Object backend;
        private Object sysOperation;
        private String language;
        private String promptMode;
        private Object settings;
    }

    /**
     * Build browser agent config.
     */
    public static BrowserAgentConfig buildBrowserAgentConfig(
            Object model,
            String language,
            Object settings) {
        String resolvedLanguage = resolveLanguage(language);

        return BrowserAgentConfig.builder()
                .name("browser_agent")
                .description(getDefaultDescription(resolvedLanguage))
                .systemPrompt(getDefaultSystemPrompt(resolvedLanguage))
                .model(model)
                .tools(new ArrayList<>())
                .mcps(new ArrayList<>())
                .rails(new ArrayList<>())
                .enableTaskLoop(false)
                .maxIterations(25)
                .language(resolvedLanguage)
                .settings(settings)
                .factoryName(BROWSER_AGENT_FACTORY_NAME)
                .build();
    }

    /**
     * Runtime settings for browser agent.
     */
    @Data
    @Builder
    public static class RuntimeSettings {
        private String provider;
        private String apiKey;
        private String apiBase;
        private String modelName;
        private Object mcpCfg;
        private Object guardrails;
    }

    /**
     * Build default runtime settings.
     */
    public static RuntimeSettings buildDefaultRuntimeSettings() {
        return RuntimeSettings.builder()
                .provider("")
                .apiKey("")
                .apiBase("")
                .modelName("")
                .build();
    }
}