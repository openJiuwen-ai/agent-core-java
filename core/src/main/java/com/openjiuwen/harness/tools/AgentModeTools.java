/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.AgentMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helpers for plan-mode file lifecycle.
 *
 * <p>Mirrors Python's module helpers in
 * {@code openjiuwen/harness/tools/agent_mode_tools.py}.</p>
 */
public final class AgentModeTools {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> ADJECTIVES = List.of(
            "ancient", "blazing", "calm", "daring", "eager", "fierce", "gleaming", "happy", "icy",
            "jolly", "keen", "lively", "mighty", "noble", "open", "proud", "quiet", "rapid", "silent",
            "tall", "unique", "vivid", "warm", "xenial", "young", "zealous"
    );
    private static final List<String> VERBS = List.of(
            "brewing", "crafting", "designing", "exploring", "forging", "gathering", "hunting",
            "inspiring", "joining", "keeping", "learning", "making", "noting", "opening", "planning",
            "questing", "reading", "seeking", "testing", "using", "viewing", "writing", "yielding"
    );
    private static final List<String> NOUNS = List.of(
            "anchor", "bridge", "cloud", "delta", "ember", "harbor", "island", "jungle",
            "kernel", "lantern", "meadow", "nexus", "orbit", "phoenix", "quartz", "river",
            "summit", "tower", "union", "valley", "wave", "xenon", "yacht", "zenith"
    );

    private AgentModeTools() {
    }

    public static String generateWordSlug() {
        return ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size())) + "-"
                + VERBS.get(RANDOM.nextInt(VERBS.size())) + "-"
                + NOUNS.get(RANDOM.nextInt(NOUNS.size()));
    }

    public static Path resolvePlanFilePath(String workspaceRoot, String planSlug) throws IOException {
        Path plansDir = Path.of(workspaceRoot).resolve(".plans");
        Files.createDirectories(plansDir);
        return plansDir.resolve(planSlug + ".md");
    }

    public static String getOrCreatePlanSlug(String workspaceRoot) throws IOException {
        for (int i = 0; i < 20; i++) {
            String slug = generateWordSlug();
            if (!Files.exists(resolvePlanFilePath(workspaceRoot, slug))) {
                return slug;
            }
        }
        return generateWordSlug();
    }

    static String language(String language) {
        return "en".equals(language) ? "en" : "cn";
    }

    static String invalidModeMessage(String language, String mode) {
        return "Invalid mode '" + mode + "'. Supported modes: plan, normal.";
    }

    static String switchModeMessage(String language, AgentMode mode) {
        if (mode == AgentMode.PLAN) {
            return "Switched mode to plan.\nNext step: call enter_plan_mode to continue the plan workflow.";
        }
        return "Switched mode to normal.";
    }

    static String enterPlanMessage(String language, Path planPath, boolean existed) {
        return (existed ? "Plan file already exists at: " : "Plan file created at: ")
                + planPath
                + "\nContinue the 5-phase Plan workflow in your instructions, "
                + "initial understanding-design-review-final plan-end.";
    }

    static String exitPlanMessage(String language, Path planPath, String planText) {
        String planPathText = planPath == null ? "" : planPath.toString();
        if (planText == null || planText.strip().isEmpty()) {
            return "Plan mode ended. You can now exit the turn.\nPlan file: " + planPathText;
        }
        return "Plan mode ended. \nPlan file: " + planPathText + "\n\n## Plan:\n" + planText;
    }

    static AgentMode parseMode(Object value) {
        return AgentMode.fromValue(value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Runtime state adapter for plan-mode tools.
     */
    public interface PlanModeController {
        void switchMode(Object session, AgentMode mode);

        String getWorkspaceRoot();

        String getPlanSlug(Object session);

        void setPlanSlug(Object session, String slug);

        Path getPlanFilePath(Object session);

        void restoreModeAfterPlanExit(Object session);
    }

    /**
     * Mirrors Python's {@code SwitchModeTool} in
     * {@code openjiuwen/harness/tools/agent_mode_tools.py}.
     */
    public static class SwitchModeTool extends AbstractHarnessTool {
        private final PlanModeController controller;
        private final String language;

        public SwitchModeTool(PlanModeController controller, String language) {
            super(toolCard("switch_mode", "switch_mode", "Switch session runtime mode."));
            this.controller = controller;
            this.language = AgentModeTools.language(language);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String rawMode = stringValue(inputs == null ? null : inputs.get("mode")).trim().toLowerCase(Locale.ROOT);
            if (!"plan".equals(rawMode) && !"normal".equals(rawMode)) {
                return ToolOutput.failure(invalidModeMessage(language, rawMode));
            }
            AgentMode mode = parseMode(rawMode);
            if (controller != null) {
                controller.switchMode(kwargs == null ? null : kwargs.get("session"), mode);
            }
            return ToolOutput.success(Map.of(
                    "current_mode", mode.value(),
                    "message", switchModeMessage(language, mode)
            ));
        }
    }

    /**
     * Mirrors Python's {@code EnterPlanModeTool} in
     * {@code openjiuwen/harness/tools/agent_mode_tools.py}.
     */
    public static class EnterPlanModeTool extends AbstractHarnessTool {
        private final PlanModeController controller;
        private final String language;

        public EnterPlanModeTool(PlanModeController controller, String language) {
            super(toolCard("enter_plan_mode", "enter_plan_mode", "Create the plan file and return its path."));
            this.controller = controller;
            this.language = AgentModeTools.language(language);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            if (controller == null) {
                return "plan mode controller is not configured";
            }
            Object session = kwargs == null ? null : kwargs.get("session");
            String slug = controller.getPlanSlug(session);
            if (slug != null && !slug.isBlank()) {
                Path existing = resolvePlanFilePath(controller.getWorkspaceRoot(), slug);
                if (Files.exists(existing)) {
                    return enterPlanMessage(language, existing, true);
                }
            }
            String newSlug = getOrCreatePlanSlug(controller.getWorkspaceRoot());
            Path planPath = resolvePlanFilePath(controller.getWorkspaceRoot(), newSlug);
            if (!Files.exists(planPath)) {
                Files.writeString(planPath, "", StandardCharsets.UTF_8);
            }
            controller.setPlanSlug(session, newSlug);
            return enterPlanMessage(language, planPath, false);
        }
    }

    /**
     * Mirrors Python's {@code ExitPlanModeTool} in
     * {@code openjiuwen/harness/tools/agent_mode_tools.py}.
     */
    public static class ExitPlanModeTool extends AbstractHarnessTool {
        private final PlanModeController controller;
        private final String language;

        public ExitPlanModeTool(PlanModeController controller, String language) {
            super(toolCard("exit_plan_mode", "exit_plan_mode", "Read the plan file content and exit plan mode."));
            this.controller = controller;
            this.language = AgentModeTools.language(language);
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            if (controller == null) {
                return exitPlanMessage(language, null, "");
            }
            Object session = kwargs == null ? null : kwargs.get("session");
            Path planPath = controller.getPlanFilePath(session);
            String planText = planPath != null && Files.exists(planPath)
                    ? Files.readString(planPath, StandardCharsets.UTF_8)
                    : "";
            if (!planText.strip().isEmpty()) {
                controller.restoreModeAfterPlanExit(session);
            }
            return exitPlanMessage(language, planPath, planText);
        }
    }
}
