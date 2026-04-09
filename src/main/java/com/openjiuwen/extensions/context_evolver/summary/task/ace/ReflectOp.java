/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace.update.ReflectOp}.
 *
 * <p>The Java port does not have an LLM-backed reflector wired into ServiceContext yet, so this
 * operation derives a deterministic reflection payload from the current trajectory and playbook.
 */
public class ReflectOp extends BaseOp {

    private final boolean useGroundTruth;

    public ReflectOp() {
        this(false);
    }

    public ReflectOp(boolean useGroundTruth) {
        this.useGroundTruth = useGroundTruth;
    }

    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = context.getString("matts", "none");
        if (!"none".equals(matts) && !"sequential".equals(matts)) {
            context.set("reflection", Map.of());
            return CompletableFuture.completedFuture(null);
        }

        List<?> rawTrajectories = context.getList("trajectories");
        if (rawTrajectories == null || rawTrajectories.isEmpty()) {
            context.set("reflection", Map.of());
            return CompletableFuture.completedFuture(null);
        }

        String query = context.getString("query", "");
        String trajectory = String.valueOf(rawTrajectories.get(0));
        Playbook playbook = context.get("playbook") instanceof Playbook existing ? existing : new Playbook();

        List<Map<String, Object>> candidateInsights = buildCandidateInsights(query, trajectory, context);
        Map<String, Object> reflection = new LinkedHashMap<>();
        reflection.put(
            "reasoning",
            "Derived reusable ACE playbook updates from the observed trajectory, API calls, and output format cues."
        );
        reflection.put(
            "error_identification",
            candidateInsights.isEmpty()
                ? "The trajectory did not expose a stable reusable step."
                : "The key reusable steps were not yet represented in the current playbook."
        );
        reflection.put(
            "root_cause_analysis",
            playbook.bullets().isEmpty()
                ? "The user playbook is empty, so successful steps need to be captured as new bullets."
                : "The playbook requires either a new bullet or a tag update for repeated successful guidance."
        );
        reflection.put(
            "correct_approach",
            candidateInsights.isEmpty()
                ? "Capture the smallest reusable strategy from the trajectory and keep it in the ACE playbook."
                : "Persist the reusable step as an ACE bullet and tag repeated guidance instead of duplicating it."
        );
        reflection.put(
            "key_insight",
            candidateInsights.isEmpty()
                ? ""
                : String.valueOf(candidateInsights.get(0).get("content"))
        );
        reflection.put("candidate_insights", candidateInsights);

        context.set("reflection", reflection);
        return CompletableFuture.completedFuture(null);
    }

    private List<Map<String, Object>> buildCandidateInsights(String query, String trajectory, RuntimeContext context) {
        List<Map<String, Object>> candidateInsights = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String tag = deriveTag(context);

        List<String> actionLines = AceUtils.extractPrefixedLines(trajectory, "ACTION:");
        if (!actionLines.isEmpty()) {
            String action = AceUtils.compactWhitespace(actionLines.get(0));
            addInsight(
                candidateInsights,
                seen,
                "apis_to_use_for_specific_information",
                "Use " + action + " to collect the authoritative task data before composing the final answer.",
                tag
            );
        }

        List<String> observationKeys = AceUtils.extractObservationKeys(trajectory);
        if (!observationKeys.isEmpty()) {
            addInsight(
                candidateInsights,
                seen,
                "output_format_and_validation",
                "Read the returned API fields directly (" + String.join(", ", observationKeys)
                    + ") and preserve the requested output format in the final answer.",
                tag
            );
        }

        String fallbackSection = AceUtils.guessSection(query, trajectory);
        String fallbackInsight = buildFallbackInsight(query, trajectory);
        if (!fallbackInsight.isBlank()) {
            addInsight(candidateInsights, seen, fallbackSection, fallbackInsight, tag);
        }

        return candidateInsights;
    }

    private void addInsight(
            List<Map<String, Object>> target,
            Set<String> seen,
            String section,
            String content,
            String tag
    ) {
        String normalized = AceUtils.normalizeForMatch(content);
        if (normalized.isBlank() || !seen.add(normalized)) {
            return;
        }
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("section", section);
        insight.put("content", content);
        insight.put("tag", tag);
        target.add(insight);
    }

    private String buildFallbackInsight(String query, String trajectory) {
        String compactQuery = AceUtils.compactWhitespace(query);
        String compactTrajectory = AceUtils.compactWhitespace(trajectory);
        if (compactTrajectory.isBlank()) {
            return "";
        }

        String summary = compactTrajectory;
        int assistantIndex = compactTrajectory.lastIndexOf("ASSISTANT:");
        if (assistantIndex >= 0) {
            summary = compactTrajectory.substring(assistantIndex + "ASSISTANT:".length()).trim();
        }
        if (summary.length() > 180) {
            summary = summary.substring(0, 180).trim() + "...";
        }

        if (compactQuery.isBlank()) {
            return "Capture the successful reusable step from the trajectory: " + summary;
        }
        return "For tasks like \"" + compactQuery
            + "\", preserve the proven successful step from the trajectory and verify it against observed API output: "
            + summary;
    }

    private String deriveTag(RuntimeContext context) {
        Object labelValue = context.get("label");
        if (labelValue instanceof List<?> labels && !labels.isEmpty() && labels.get(0) instanceof Boolean firstLabel) {
            return firstLabel ? "helpful" : "harmful";
        }

        Object scoreValue = context.get("score");
        if (scoreValue instanceof List<?> scores && !scores.isEmpty() && scores.get(0) instanceof Number firstScore) {
            return firstScore.doubleValue() < 0 ? "harmful" : "helpful";
        }

        return useGroundTruth ? "helpful" : "helpful";
    }
}
