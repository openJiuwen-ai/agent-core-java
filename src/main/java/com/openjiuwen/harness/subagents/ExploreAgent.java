/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;

/**
 * Explore agent configuration and factory helpers.
 *
 * <p>Mirrors Python's {@code explore_agent} in
 * {@code openjiuwen.harness.subagents.explore_agent}.
 */
public final class ExploreAgent {

    public static final String FACTORY_NAME = "explore_agent";

    private static final String SYSTEM_PROMPT_CN =
            "你是一个只读代码探索子代理，负责快速定位文件、检索内容并总结代码库结构。";
    private static final String SYSTEM_PROMPT_EN =
            "You are a read-only code exploration subagent focused on locating files, "
                    + "searching content, and summarizing repository structure.";

    private static final String DESCRIPTION_CN =
            "代码探索子代理，擅长快速定位代码模式并理解仓库结构。";
    private static final String DESCRIPTION_EN =
            "Code exploration subagent that quickly locates code patterns and explains repository structure.";

    private static final List<String> DEFAULT_TOOL_NAMES = List.of(
            "read_file",
            "glob",
            "list_files",
            "grep",
            "bash",
            "write_file",
            "edit_file"
    );

    private ExploreAgent() {
    }

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }

    /**
     * Build the default explore subagent config.
     */
    public static DeepAgentConfig buildExploreAgentConfig(String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";

        AgentCard card = new AgentCard();
        card.setName(FACTORY_NAME);
        card.setDescription(getDescription(resolvedLanguage));

        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setSystemPrompt(getSystemPrompt(resolvedLanguage));
        config.setEnableTaskLoop(false);
        config.setMaxIterations(15);
        config.setRails(List.of(new SysOperationRail()));
        config.setTools(DEFAULT_TOOL_NAMES.stream().map(ExploreAgent::toolCard).toList());
        return config;
    }

    /**
     * Create a configured explore subagent.
     */
    public static DeepAgent createExploreSubagent(String language, Workspace workspace) {
        DeepAgentConfig config = buildExploreAgentConfig(language);
        config.setWorkspace(workspace);
        DeepAgent agent = new DeepAgent(config.getCard());
        agent.configure(config);
        return agent;
    }

    private static ToolCard toolCard(String name) {
        ToolCard card = new ToolCard();
        card.setName(name);
        card.setDescription(name);
        return card;
    }
}
