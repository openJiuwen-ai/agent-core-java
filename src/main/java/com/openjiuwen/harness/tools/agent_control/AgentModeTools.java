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

    // Agent modes
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_PLAN = "plan";

    // Messages
    private static final Map<String, String> ENTER_PLAN_EXISTS_MSG = Map.of(
        "en", "Plan file already exists at: {plan_path}\nYou can read it and make incremental edits.",
        "cn", "计划文件已存在，路径：{plan_path}\n你可以阅读计划文件然后做增量修改。"
    );

    private static final Map<String, String> ENTER_PLAN_CREATED_MSG = Map.of(
        "en", "Plan file created at: {plan_path}\nContinue the Plan workflow.",
        "cn", "计划文件已创建于：{plan_path}\n请按照Plan工作流继续制定计划。"
    );

    private static final Map<String, String> EXIT_PLAN_MSG = Map.of(
        "en", "Plan mode ended. Plan file: {plan_path}",
        "cn", "规划模式已结束。计划文件：{plan_path}"
    );

    private static final Map<String, String> SWITCH_MODE_INVALID_MSG = Map.of(
        "en", "Invalid mode '{mode}'. Supported modes: plan, normal.",
        "cn", "无效模式 '{mode}'。支持模式：normal、plan。"
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
     * <p>
     * Mirrors Python's {@code resolve_plan_file_path}.
     */
    public static Path resolvePlanFilePath(String workspaceRoot, String planSlug) {
        if (workspaceRoot == null || workspaceRoot.isEmpty()) {
            workspaceRoot = System.getProperty("user.dir");
        }
        Path workspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
        String fileName = planSlug + ".md";
        return workspace.resolve(".plans").resolve(fileName);
    }

    /**
     * Get or create a unique plan slug.
     * <p>
     * Mirrors Python's {@code get_or_create_plan_slug}.
     */
    public static String getOrCreatePlanSlug(String workspaceRoot) {
        Path plansDir = Path.of(workspaceRoot).resolve(".plans");
        if (!plansDir.toFile().exists()) {
            plansDir.toFile().mkdirs();
        }
        for (int i = 0; i < 20; i++) {
            String slug = generateWordSlug();
            Path planFile = plansDir.resolve(slug + ".md");
            if (!planFile.toFile().exists()) {
                return slug;
            }
        }
        return generateWordSlug();
    }

    /**
     * Create plan tools for the given agent.
     * <p>
     * Mirrors Python's tool factory methods.
     */
    public static List<Object> createPlanTools(Object agentRef, String language) {
        List<Object> tools = new ArrayList<>();
        tools.add(new SwitchModeTool(agentRef, language));
        tools.add(new EnterPlanModeTool(agentRef, language));
        tools.add(new ExitPlanModeTool(agentRef, language));
        return tools;
    }

    /**
     * Switch mode tool.
     * <p>
     * Mirrors Python's {@code SwitchModeTool}.
     */
    public static class SwitchModeTool {
        private final Object agentRef;
        private final String language;

        public SwitchModeTool(Object agentRef, String language) {
            this.agentRef = agentRef;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String mode = inputs.getOrDefault("mode", "").toString().trim().toLowerCase();
            String lang = language.equals("en") ? "en" : "cn";

            if (!mode.equals(MODE_PLAN) && !mode.equals(MODE_NORMAL)) {
                return ToolOutput.error(SWITCH_MODE_INVALID_MSG.get(lang).replace("{mode}", mode));
            }

            // Get current mode from agent
            String previousMode = getAgentMode(agentRef);
            setAgentMode(agentRef, mode);

            String message = mode.equals(MODE_PLAN) ?
                "Switched mode to plan. Next step: call enter_plan_mode." :
                "Switched mode to normal.";

            return ToolOutput.success(Map.of(
                "previous_mode", previousMode,
                "current_mode", mode,
                "message", message
            ));
        }
    }

    /**
     * Enter plan mode tool.
     * <p>
     * Mirrors Python's EnterPlanMode logic.
     */
    public static class EnterPlanModeTool {
        private final Object agentRef;
        private final String language;

        public EnterPlanModeTool(Object agentRef, String language) {
            this.agentRef = agentRef;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String lang = language.equals("en") ? "en" : "cn";
            String workspaceRoot = getWorkspaceRoot(agentRef);
            String planSlug = inputs.containsKey("plan_slug") ?
                inputs.get("plan_slug").toString() : getOrCreatePlanSlug(workspaceRoot);

            Path planPath = resolvePlanFilePath(workspaceRoot, planSlug);
            boolean exists = planPath.toFile().exists();

            String message;
            if (exists) {
                message = ENTER_PLAN_EXISTS_MSG.get(lang).replace("{plan_path}", planPath.toString());
            } else {
                // Create empty plan file
                planPath.getParent().toFile().mkdirs();
                try {
                    planPath.toFile().createNewFile();
                } catch (java.io.IOException e) {
                    // Ignore - file creation is optional
                }
                message = ENTER_PLAN_CREATED_MSG.get(lang).replace("{plan_path}", planPath.toString());
            }

            setAgentPlanSlug(agentRef, planSlug);

            return ToolOutput.success(Map.of(
                "plan_path", planPath.toString(),
                "plan_slug", planSlug,
                "exists", exists,
                "message", message
            ));
        }
    }

    /**
     * Exit plan mode tool.
     * <p>
     * Mirrors Python's ExitPlanMode logic.
     */
    public static class ExitPlanModeTool {
        private final Object agentRef;
        private final String language;

        public ExitPlanModeTool(Object agentRef, String language) {
            this.agentRef = agentRef;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String lang = language.equals("en") ? "en" : "cn";
            String workspaceRoot = getWorkspaceRoot(agentRef);
            String planSlug = getAgentPlanSlug(agentRef);

            if (planSlug == null || planSlug.isEmpty()) {
                return ToolOutput.error("No plan slug set. Call enter_plan_mode first.");
            }

            Path planPath = resolvePlanFilePath(workspaceRoot, planSlug);
            String message = EXIT_PLAN_MSG.get(lang).replace("{plan_path}", planPath.toString());

            // Read plan content if exists
            String content = "";
            if (planPath.toFile().exists()) {
                try {
                    content = java.nio.file.Files.readString(planPath);
                } catch (Exception e) {
                    LOG.debug("Failed to read plan file", e);
                }
            }

            // Reset plan slug
            setAgentPlanSlug(agentRef, null);
            setAgentMode(agentRef, MODE_NORMAL);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("plan_path", planPath.toString());
            result.put("message", message);
            if (!content.isEmpty()) {
                result.put("plan_content", content);
            }

            return ToolOutput.success(result);
        }
    }

    // Helper methods for agent state access
    private static String getAgentMode(Object agent) {
        try {
            if (agent instanceof com.openjiuwen.harness.DeepAgent da) {
                return da.getCurrentMode();
            }
        } catch (Exception e) {
            LOG.debug("getAgentMode failed", e);
        }
        return MODE_NORMAL;
    }

    private static void setAgentMode(Object agent, String mode) {
        try {
            if (agent instanceof com.openjiuwen.harness.DeepAgent da) {
                da.setCurrentMode(mode);
            }
        } catch (Exception e) {
            LOG.debug("setAgentMode failed", e);
        }
    }

    private static String getWorkspaceRoot(Object agent) {
        try {
            if (agent instanceof com.openjiuwen.harness.DeepAgent da) {
                com.openjiuwen.harness.workspace.Workspace ws = da.getWorkspace();
                if (ws != null) {
                    return ws.getRootPath();
                }
            }
        } catch (Exception e) {
            LOG.debug("getWorkspaceRoot failed", e);
        }
        return System.getProperty("user.dir");
    }

    private static String getAgentPlanSlug(Object agent) {
        try {
            if (agent instanceof com.openjiuwen.harness.DeepAgent da) {
                return da.getPlanSlug();
            }
        } catch (Exception e) {
            LOG.debug("getAgentPlanSlug failed", e);
        }
        return null;
    }

    private static void setAgentPlanSlug(Object agent, String slug) {
        try {
            if (agent instanceof com.openjiuwen.harness.DeepAgent da) {
                da.setPlanSlug(slug);
            }
        } catch (Exception e) {
            LOG.debug("setAgentPlanSlug failed", e);
        }
    }

    /**
     * Tool output wrapper.
     */
    @Data
    @Builder
    public static class ToolOutput {
        private boolean success;
        private Map<String, Object> data;
        private String error;

        public static ToolOutput success(Map<String, Object> data) {
            return ToolOutput.builder().success(true).data(data).build();
        }

        public static ToolOutput error(String error) {
            return ToolOutput.builder().success(false).error(error).build();
        }
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