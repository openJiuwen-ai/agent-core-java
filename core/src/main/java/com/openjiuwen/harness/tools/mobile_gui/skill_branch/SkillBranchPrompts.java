/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt builders for mobile-GUI skill-consult branches.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/mobile_gui/skill_branch/prompts.py}.</p>
 */
public final class SkillBranchPrompts {

    private SkillBranchPrompts() {
    }

    public static String buildStage1SystemMessage(int maxImages) {
        return """
                You are Stage 1 of a temporary skill consultation branch for one Android GUI step.
                Decide whether reference images from the skill documentation are needed before planner reasoning.

                Rules:
                - Do NOT return tool calls, coordinate actions, or read_file.
                - The CURRENT device screenshot (attached) is authoritative for live UI state.
                - Skill text and the image manifest are supplemental references only.
                - Load reference images only when text plus the live screenshot are likely insufficient.
                - Request at most %d images total.

                Output format:
                - Return ONLY one code block containing exactly one LOAD_SKILL_IMAGES({...}) call.
                - JSON fields:
                  - visual_reference_needed: true or false
                  - why_not_text_only: explain the decision
                  - requests: list of {image_id, reason} using exact image_id values from the manifest
                - When visual_reference_needed is false, requests must be [].

                Example without images:
                ```python
                LOAD_SKILL_IMAGES({
                  "visual_reference_needed": false,
                  "why_not_text_only": "The skill describes a stable menu path; the live screenshot is enough.",
                  "requests": []
                })
                ```

                Example with images:
                ```python
                LOAD_SKILL_IMAGES({
                  "visual_reference_needed": true,
                  "why_not_text_only": "The task depends on recognizing a specific UI layout shown in the skill figures.",
                  "requests": [
                    {"image_id": "github_landing_page", "reason": "Need the expected repository landing layout."}
                  ]
                })
                ```
                """.formatted(maxImages).trim();
    }

    public static String buildStage1UserMessage(
            String instruction,
            String skillName,
            String skillText,
            List<SkillImageEntry> manifest,
            String previousSteps
    ) {
        String manifestText = SkillBranchManifest.formatManifestForPrompt(manifest);
        List<String> sections = new ArrayList<>();
        sections.add("Decide whether to load skill reference images for this step.");
        sections.add("User instruction: " + defaultString(instruction));
        sections.add("Previous steps (last N assistant steps with tool calls/results only; task is User instruction above):");
        sections.add(defaultString(previousSteps).isEmpty() ? "(no previous steps)" : previousSteps);
        sections.add("Skill name: " + defaultString(skillName));
        sections.add("Available reference images (image_id, alt, path):");
        sections.add(manifestText);
        sections.add("Skill markdown (text only):");
        sections.add(defaultString(skillText));
        return String.join("\n\n", sections);
    }

    public static String buildStage2SystemMessage() {
        return """
                You are Stage 2 of a temporary skill consultation branch for one Android GUI step.
                Return a structured planner summary for the CURRENT device state. Do NOT return GUI actions.

                Rules:
                - The CURRENT device screenshot is authoritative.
                - Skill text and any loaded reference images are documentation only, never coordinate templates.
                - If Stage 1 chose no visual references, do not invent image-based assumptions.
                - Reference images show example states, not the live device screen.

                Output format:
                - Return ONLY one code block with a single JSON object containing:
                  - skill_applicability: effective | ineffective | uncertain
                  - subgoal: short local milestone
                  - plan: 2-4 key actions/checks grounded in the current screen
                  - do_not_do: likely wrong path to avoid
                  - fallback_if_no_progress: concrete alternate route
                  - expected_state: visible cues the main agent should aim for next
                  - completion_scope: local_only | needs_verification | maybe_complete
                """.trim();
    }

    public static String buildStage2UserMessage(
            String instruction,
            String skillName,
            String skillText,
            String stage1Decision,
            String selectedImageSummary,
            String previousSteps
    ) {
        return String.join(
                "\n\n",
                List.of(
                        "Return planner JSON only for the CURRENT screenshot.",
                        "User instruction: " + defaultString(instruction),
                        "Previous steps (last N assistant steps with tool calls/results only; task is User instruction above):",
                        defaultString(previousSteps).isEmpty() ? "(no previous steps)" : previousSteps,
                        "Skill name: " + defaultString(skillName),
                        "Stage-1 decision: " + defaultString(stage1Decision),
                        "Selected reference images: " + defaultString(selectedImageSummary),
                        "Skill markdown:",
                        defaultString(skillText)
                )
        );
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
