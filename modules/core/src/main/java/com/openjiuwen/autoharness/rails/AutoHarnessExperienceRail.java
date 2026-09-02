/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.autoharness.tools.ExperienceSearchTool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Register experience search and inject the auto-harness experience prompt section.
 * 
 * @since 0.1.7
 */
public class AutoHarnessExperienceRail extends DeepAgentRail {
    private static final String MEMORY_SECTION = "memory";

    private final String experienceDir;
    private final String language;

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> ownedToolNames = new HashSet<>();

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> ownedToolIds = new HashSet<>();
    private SystemPromptBuilder systemPromptBuilder;

    /**
     * AutoHarnessExperienceRail.
     * 
     * @param experienceDir experienceDir
     * @since 0.1.7
     */
    public AutoHarnessExperienceRail(String experienceDir) {
        this(experienceDir, "cn");
    }

    /**
     * AutoHarnessExperienceRail.
     * 
     * @param experienceDir experienceDir
     * @param language language
     * @since 0.1.7
     */
    public AutoHarnessExperienceRail(String experienceDir, String language) {
        this.experienceDir = experienceDir;
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 80;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            systemPromptBuilder = deepAgent.getAgent().getSystemPromptBuilder();
            registerExperienceTool(deepAgent);
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (String toolName : Set.copyOf(ownedToolNames)) {
                deepAgent.getAgent().getAbilityManager().remove(toolName);
            }
        }
        for (String toolId : Set.copyOf(ownedToolIds)) {
            if (Runner.resourceMgr().getTool(toolId) != null) {
                Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
            }
        }
        ownedToolNames.clear();
        ownedToolIds.clear();
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(MEMORY_SECTION);
            systemPromptBuilder = null;
        }
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        systemPromptBuilder.removeSection(MEMORY_SECTION);
        systemPromptBuilder.addSection(buildExperienceSection(systemPromptBuilder.getLanguage(), experienceDir));
    }

    /**
     * buildExperienceSection.
     * 
     * @param language language
     * @param experienceDir experienceDir
     * @return the result
     * @since 0.1.7
     */
    public static PromptSection buildExperienceSection(String language, String experienceDir) {
        String dir = experienceDir == null || experienceDir.isBlank() ? ".auto_harness/experience" : experienceDir;
        Map<String, String> content = Map.of("cn",
                "## Experience Library\n\n" + "经验库位于 `" + dir + "`。\n" + "需要回顾历史优化、失败案例和洞察时，使用 `experience_search`。",
                "en", "## Experience Library\n\n" + "The experience library lives at `" + dir + "`.\n"
                        + "Use `experience_search` when reviewing prior optimizations, failures, and" + " insights.");
        return new PromptSection(MEMORY_SECTION, content, 85);
    }

    /**
     * hasExperiencePromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasExperiencePromptSection() {
        return systemPromptBuilder != null && systemPromptBuilder.hasSection(MEMORY_SECTION);
    }

    /**
     * registerExperienceTool.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    private void registerExperienceTool(DeepAgent agent) {
        ExperienceSearchTool tool =
            new ExperienceSearchTool(experienceDir, UUID.randomUUID().toString().replace("-", ""), language);
        if (Runner.resourceMgr().getTool(tool.getCard().getId()) == null) {
            Runner.resourceMgr().addTool(tool, agent.getCard().getId());
            ownedToolIds.add(tool.getCard().getId());
        }
        if (agent.getAgent().getAbilityManager().get(tool.getCard().getName()) == null) {
            agent.getAgent().getAbilityManager().add(tool.getCard());
            ownedToolNames.add(tool.getCard().getName());
        }
    }
}
