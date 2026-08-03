/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.util.List;

/**
 * Package bridge for built-in subagent factory exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents} module in
 * {@code openjiuwen/harness/subagents/__init__.py}.</p>
 */
public final class SubagentsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/subagents/__init__.py";

    public static final List<String> ALL = List.of(
            "build_browser_agent_config",
            "build_code_agent_config",
            "build_research_agent_config",
            "build_verification_agent_config",
            "create_browser_agent",
            "create_code_agent",
            "create_research_agent",
            "create_verification_agent",
            "build_mobile_gui_agent_config",
            "create_mobile_gui_agent"
    );

    private SubagentsPackage() {
    }

    public static List<String> exports() {
        return ALL;
    }

    public static Object getAttribute(String name) {
        if (ALL.contains(name)) {
            return name;
        }
        throw new IllegalArgumentException(
                "module 'openjiuwen.harness.subagents' has no attribute '" + name + "'"
        );
    }

    public static DeepAgentConfig.SubAgentConfig buildBrowserAgentConfig(Object model) {
        return BrowserAgentFactory.buildBrowserAgentConfig(model);
    }

    public static DeepAgentConfig.SubAgentConfig buildCodeAgentConfig(Object model) {
        return CodeAgentFactory.buildCodeAgentConfig(model);
    }

    public static DeepAgentConfig.SubAgentConfig buildResearchAgentConfig(Object model) {
        return ResearchAgentFactory.buildResearchAgentConfig(model);
    }

    public static DeepAgentConfig.SubAgentConfig buildVerificationAgentConfig(Object model) {
        return VerificationAgentFactory.buildVerificationAgentConfig(model);
    }

    public static DeepAgentConfig.SubAgentConfig buildMobileGuiAgentConfig(Object model) {
        return MobileGuiAgentFactory.buildMobileGuiAgentConfig(model);
    }

    public static DeepAgent createBrowserAgent(Object model) {
        return BrowserAgentFactory.createBrowserAgent(model, null, null, null, null, null, null);
    }

    public static DeepAgent createCodeAgent(Object model) {
        return CodeAgentFactory.createCodeAgent(model);
    }

    public static DeepAgent createResearchAgent(Object model) {
        return ResearchAgentFactory.createResearchAgent(model);
    }

    public static DeepAgent createVerificationAgent(Object model) {
        return VerificationAgentFactory.createVerificationAgent(model);
    }

    public static DeepAgent createMobileGuiAgent(Object model) {
        return MobileGuiAgentFactory.createMobileGuiAgent(model, null, null, null, null, null);
    }
}
