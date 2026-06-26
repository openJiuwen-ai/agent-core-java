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
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for the mobile GUI VLM grounding subagent.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.mobile_gui_agent} in
 * {@code openjiuwen/harness/subagents/mobile_gui_agent.py}.</p>
 */
public final class MobileGuiAgentFactory {

    public static final String MOBILE_GUI_AGENT_FACTORY_NAME = "mobile_gui_agent";

    public static final Map<String, String> DEFAULT_MOBILE_GUI_DESCRIPTION = Map.of(
            "cn", "专用移动子代理，基于 VLM 坐标 grounding 操控 Android GUI。",
            "en", "Dedicated mobile subagent for Android GUI automation using VLM coordinate grounding."
    );

    private MobileGuiAgentFactory() {
    }

    public static DeepAgentConfig.SubAgentConfig buildMobileGuiAgentConfig(Object model) {
        return buildMobileGuiAgentConfig(model, null, null, null, null, null, "cn", false, 30,
                MobileGuiRuntimeSettings.fromEnv());
    }

    public static DeepAgentConfig.SubAgentConfig buildMobileGuiAgentConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            String language,
            boolean enableTaskLoop,
            int maxIterations,
            MobileGuiRuntimeSettings settings
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        MobileGuiRuntimeSettings resolvedSettings = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        AgentCard finalCard = card == null
                ? new AgentCard(MOBILE_GUI_AGENT_FACTORY_NAME, MOBILE_GUI_AGENT_FACTORY_NAME,
                DEFAULT_MOBILE_GUI_DESCRIPTION.get(resolvedLanguage))
                : card;
        DeepAgentConfig config = ExploreAgent.baseConfig(
                model,
                finalCard,
                systemPrompt == null ? buildVlmGroundingSystemPrompt(resolvedSettings) : systemPrompt,
                tools,
                rails == null ? List.of() : List.copyOf(rails),
                resolvedLanguage,
                enableTaskLoop
        );
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
        spec.setFactoryName(MOBILE_GUI_AGENT_FACTORY_NAME);
        spec.setFactoryKwargs(Map.of("settings", resolvedSettings));
        Map<String, Object> metadata = new LinkedHashMap<>(ExploreAgent.metadata(
                MOBILE_GUI_AGENT_FACTORY_NAME, maxIterations, mcps));
        metadata.put("settings", resolvedSettings);
        spec.setMetadata(metadata);
        return spec;
    }

    public static DeepAgent createMobileGuiAgent(
            Object model,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            String language,
            MobileGuiRuntimeSettings settings
    ) {
        DeepAgentConfig.SubAgentConfig spec = buildMobileGuiAgentConfig(
                model, null, null, tools == null ? new ArrayList<>() : tools, mcps, rails,
                language, false, 30, settings);
        DeepAgent agent = new DeepAgent(spec.getCard());
        agent.configure(spec.getConfig());
        return agent;
    }

    private static String buildVlmGroundingSystemPrompt(MobileGuiRuntimeSettings settings) {
        return "You are a mobile GUI automation agent using VLM coordinate grounding. "
                + "Act only through approved mobile GUI tools and rails. "
                + "Device serial=" + (settings == null ? "" : settings.getDeviceSerial()) + ".";
    }

    private static List<Object> toObjectList(List<?> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
