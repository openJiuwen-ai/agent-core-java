/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Experience rail for auto-harness agents.
 *
 * <p>Mirrors Python's {@code AutoHarnessExperienceRail} in {@code openjiuwen.auto_harness.rails.experience_rail}.</p>
 */
public class ExperienceRail {

    private static final Logger logger = Logger.getLogger(ExperienceRail.class.getName());

    private final String experienceDir;
    private final String language;
    private final Set<String> ownedToolNames = new HashSet<>();
    private final Set<String> ownedToolIds = new HashSet<>();
    private Object systemPromptBuilder;

    public ExperienceRail(String experienceDir) {
        this(experienceDir, "cn");
    }

    public ExperienceRail(String experienceDir, String language) {
        this.experienceDir = experienceDir;
        this.language = language;
    }

    /**
     * Initialize with agent.
     *
     * @param agent the agent
     */
    public void init(Object agent) {
        this.systemPromptBuilder = getAttribute(agent, "system_prompt_builder");
        registerExperienceTool(agent);
    }

    /**
     * Uninitialize with agent.
     *
     * @param agent the agent
     */
    public void uninit(Object agent) {
        // TODO: Remove registered tools
    }

    private void registerExperienceTool(Object agent) {
        // TODO: Register experience_search tool
    }

    private Object getAttribute(Object obj, String name) {
        try {
            return obj.getClass().getField(name).get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the experience directory.
     *
     * @return the experience directory path
     */
    public String getExperienceDir() {
        return experienceDir;
    }

    /**
     * Get the language.
     *
     * @return the language
     */
    public String getLanguage() {
        return language;
    }
}