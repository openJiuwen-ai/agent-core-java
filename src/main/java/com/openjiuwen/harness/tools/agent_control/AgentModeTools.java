/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.agent_control;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent mode tools for switching runtime mode and managing plan files.
 *
 * <p>Mirrors Python's {@code agent_mode_tools} module in
 * {@code openjiuwen.harness.tools.agent_control.agent_mode_tools}.
 */
public final class AgentModeTools {

    private static final Logger LOG = LoggerFactory.getLogger(AgentModeTools.class);

    // Word lists for slug generation (adjective-verb-noun)
    private static final List<String> ADJECTIVES = List.of(
            "ancient", "blazing", "calm", "daring", "eager",
            "fierce", "gleaming", "happy", "icy", "jolly",
            "keen", "lively", "mighty", "noble", "open",
            "proud", "quiet", "rapid", "silent", "tall",
            "unique", "vivid", "warm", "xenial", "young", "zealous"
    );

    private static final List<String> VERBS = List.of(
            "brewing", "crafting", "designing", "exploring", "forging",
            "gathering", "hunting", "inspiring", "joining", "keeping",
            "learning", "making", "noting", "opening", "planning",
            "questing", "reading", "seeking", "testing", "using",
            "viewing", "writing", "yielding"
    );

    private static final List<String> NOUNS = List.of(
            "anchor", "bridge", "cloud", "delta", "ember",
            "falcon", "galaxy", "harbor", "island", "jungle",
            "kernel", "lantern", "meadow", "nexus", "orbit",
            "phoenix", "quartz", "river", "summit", "tower",
            "union", "valley", "wave", "xenon", "yacht", "zenith"
    );

    private AgentModeTools() {
    }

    /**
     * Generate a random adjective-verb-noun slug.
     *
     * <p>Uses secure random for generation.
     */
    public static String generateWordSlug() {
        String adj = ADJECTIVES.get(ThreadLocalRandom.current().nextInt(ADJECTIVES.size()));
        String verb = VERBS.get(ThreadLocalRandom.current().nextInt(VERBS.size()));
        String noun = NOUNS.get(ThreadLocalRandom.current().nextInt(NOUNS.size()));
        return adj + "-" + verb + "-" + noun;
    }

    /**
     * Resolve plan file path from workspace root and slug.
     */
    public static Path resolvePlanFilePath(String workspaceRoot, String planSlug) {
        if (workspaceRoot == null || workspaceRoot.isEmpty()) {
            workspaceRoot = System.getProperty("user.dir");
        }
        Path workspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
        String fileName = planSlug + ".md";
        return workspace.resolve(".sisyphus").resolve("plans").resolve(fileName);
    }

    /**
     * Agent mode tool output.
     */
    @Data
    @Builder
    public static class ModeSwitchOutput {
        private boolean success;
        private String previousMode;
        private String currentMode;
        private String planFile;
        private String message;
    }
}