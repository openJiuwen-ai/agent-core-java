  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.extensions.context_evolver.service;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class ReasoningBankSummarizeMemoryOp extends BaseOp {

    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<?> rawTrajectories = context.getList("trajectories");
        if (rawTrajectories == null || rawTrajectories.isEmpty()) {
            context.set("memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        List<String> trajectories = new ArrayList<>();
        for (Object rawTrajectory : rawTrajectories) {
            String normalized = SummaryFlowSupport.normalizeTrajectory(rawTrajectory);
            if (!normalized.isBlank()) {
                trajectories.add(normalized);
            }
        }
        if (trajectories.isEmpty()) {
            context.set("memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        String query = context.getString("query", "");
        String userId = context.getString("user_id", "default");
        String matts = context.getString("matts", "none");
        boolean parallelMode = "parallel".equalsIgnoreCase(matts) || "combined".equalsIgnoreCase(matts);

        if (parallelMode && trajectories.size() < 2) {
            context.set("memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        ReasoningBankMemory memory = new ReasoningBankMemory();
        memory.setWorkspaceId(userId);
        memory.setQuery(query);

        if (parallelMode) {
            memory.setLabel(null);
            memory.setMemory(buildParallelItems(query, trajectories, context));
        } else {
            boolean label = resolveLabel(context, trajectories.get(0), 0);
            memory.setLabel(label);
            context.set("label", List.of(label));
            memory.setMemory(buildSingleItems(query, trajectories.get(0), label));
        }

        context.set("memories", memory.getMemory().isEmpty() ? List.of() : List.of(memory));
        return CompletableFuture.completedFuture(null);
    }

    private List<ReasoningBankMemoryItem> buildSingleItems(String query, String trajectory, boolean success) {
        List<ReasoningBankMemoryItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        List<String> actionLines = SummaryFlowSupport.actionLines(trajectory);
        String tool = SummaryFlowSupport.firstToolName(SummaryFlowSupport.toolNames(trajectory), actionLines);
        if (!actionLines.isEmpty()) {
            String action = SummaryFlowSupport.limit(actionLines.get(0), 160);
            addItem(
                items,
                seen,
                success
                    ? "Use " + defaultToolName(tool, "the authoritative action") + " before answering"
                    : "Re-evaluate " + defaultToolName(tool, "the first action") + " before answering",
                success
                    ? "Prefer the action path that directly surfaces the required evidence."
                    : "A failed run needs an earlier pivot or verification step.",
                success
                    ? "Start with " + action + " to collect the authoritative evidence before drafting the final answer."
                    : "The trajectory stalled after " + action
                        + ". Validate the returned data and switch tools or filters when the evidence is incomplete.",
                3
            );
        }

        List<String> observationKeys = SummaryFlowSupport.observationKeys(trajectory);
        if (!observationKeys.isEmpty()) {
            String keys = String.join(", ", observationKeys);
            addItem(
                items,
                seen,
                "Ground the answer in observed fields",
                success
                    ? "Read returned fields directly before composing the final answer."
                    : "Check whether the observed fields really support the answer before continuing.",
                success
                    ? "Inspect the returned fields (" + keys + ") and map them directly into the requested output format."
                    : "Inspect the returned fields (" + keys + ") and stop the current plan if they do not support the requested answer.",
                3
            );
        }

        String summary = SummaryFlowSupport.assistantSummary(trajectory);
        addItem(
            items,
            seen,
            success ? "Capture the reusable successful step" : "Capture the failure lesson",
            success
                ? "Store the final reusable reasoning step as a memory item."
                : "Store the failed pattern as a prevention rule.",
            summary.isBlank()
                ? "For tasks like \"" + fallbackQueryHint(query)
                    + "\", preserve the smallest reusable reasoning step from the trajectory."
                : (success
                    ? "For tasks like \"" + fallbackQueryHint(query)
                        + "\", preserve this reusable successful step: " + summary
                    : "For tasks like \"" + fallbackQueryHint(query)
                        + "\", capture this lesson and avoid repeating it: " + summary),
            3
        );

        return items;
    }

    private List<ReasoningBankMemoryItem> buildParallelItems(
            String query,
            List<String> trajectories,
            RuntimeContext context
    ) {
        List<ReasoningBankMemoryItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> successTools = new LinkedHashSet<>();
        Set<String> failureTools = new LinkedHashSet<>();
        Set<String> successKeys = new LinkedHashSet<>();
        Set<String> failureKeys = new LinkedHashSet<>();
        boolean sawSuccess = false;
        boolean sawFailure = false;
        String bestTrajectory = trajectories.get(0);
        String worstTrajectory = trajectories.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        double worstScore = Double.POSITIVE_INFINITY;
        List<?> rawScores = context.getList("score");

        for (int index = 0; index < trajectories.size(); index++) {
            String trajectory = trajectories.get(index);
            boolean success = resolveLabel(context, trajectory, index);
            double score = SummaryFlowSupport.scoreAt(rawScores, index, success ? 1.0d : 0.0d);
            if (score > bestScore) {
                bestScore = score;
                bestTrajectory = trajectory;
            }
            if (score < worstScore) {
                worstScore = score;
                worstTrajectory = trajectory;
            }

            String tool = SummaryFlowSupport.firstToolName(
                SummaryFlowSupport.toolNames(trajectory),
                SummaryFlowSupport.actionLines(trajectory)
            );
            List<String> keys = SummaryFlowSupport.observationKeys(trajectory);
            if (success) {
                sawSuccess = true;
                if (!tool.isBlank()) {
                    successTools.add(tool);
                }
                successKeys.addAll(keys);
            } else {
                sawFailure = true;
                if (!tool.isBlank()) {
                    failureTools.add(tool);
                }
                failureKeys.addAll(keys);
            }
        }

        if (!successTools.isEmpty()) {
            addItem(
                items,
                seen,
                "Reuse the successful tool path",
                "Prefer actions that directly expose the needed evidence across trajectories.",
                "Across the stronger trajectories, " + String.join(", ", successTools)
                    + " surfaced the data needed to answer the task. Reuse that path before drafting the final response.",
                5
            );
        }

        if (!failureTools.isEmpty()) {
            addItem(
                items,
                seen,
                "Pivot away from low-signal tool paths",
                "Do not repeat trajectories that fail to surface usable evidence.",
                "Lower-quality trajectories stalled around " + String.join(", ", failureTools)
                    + ". When that happens, change the tool, filter, or verification step instead of repeating the dead end.",
                5
            );
        }

        String bestTool = SummaryFlowSupport.firstToolName(
            SummaryFlowSupport.toolNames(bestTrajectory),
            SummaryFlowSupport.actionLines(bestTrajectory)
        );
        String worstTool = SummaryFlowSupport.firstToolName(
            SummaryFlowSupport.toolNames(worstTrajectory),
            SummaryFlowSupport.actionLines(worstTrajectory)
        );
        String comparativeContent = "Compare multiple trajectories for the same query and keep the path that exposes directly usable evidence.";
        if (!bestTool.isBlank() || !worstTool.isBlank()) {
            comparativeContent += " The stronger run relied on " + defaultToolName(bestTool, "a better tool path")
                + " while the weaker run stalled around " + defaultToolName(worstTool, "a weaker tool path") + ".";
        }
        if (!successKeys.isEmpty()) {
            comparativeContent += " The successful runs grounded the answer in fields such as "
                + String.join(", ", successKeys) + ".";
        } else if (!failureKeys.isEmpty()) {
            comparativeContent += " The weaker runs never exposed enough fields to support a confident answer.";
        }
        if (sawSuccess || sawFailure) {
            addItem(
                items,
                seen,
                "Contrast successful and failed trajectories",
                "Use cross-trajectory comparison to isolate the transferable difference.",
                comparativeContent,
                5
            );
        }

        if (items.isEmpty()) {
            items.addAll(buildSingleItems(query, trajectories.get(0), true));
        }
        return items.size() > 5 ? new ArrayList<>(items.subList(0, 5)) : items;
    }

    private boolean resolveLabel(RuntimeContext context, String trajectory, int index) {
        List<?> labels = context.getList("label");
        if (labels != null && index < labels.size() && labels.get(index) instanceof Boolean label) {
            return label;
        }
        List<?> scores = context.getList("score");
        if (scores != null && index < scores.size() && scores.get(index) instanceof Number score) {
            return score.doubleValue() >= 1.0d;
        }
        Boolean feedbackLabel = SummaryFlowSupport.feedbackLabel(trajectory);
        if (feedbackLabel != null) {
            return feedbackLabel;
        }
        return inferSuccess(trajectory);
    }

    private boolean inferSuccess(String trajectory) {
        Boolean feedbackLabel = SummaryFlowSupport.feedbackLabel(trajectory);
        if (feedbackLabel != null) {
            return feedbackLabel;
        }
        String normalized = " " + SummaryFlowSupport.normalizeForMatch(trajectory) + " ";
        if (normalized.contains(" status failure ")
            || normalized.contains(" failed ")
            || normalized.contains(" error ")
            || normalized.contains(" unable ")
            || normalized.contains(" cannot ")
            || normalized.contains(" could not ")
            || normalized.contains(" no result ")) {
            return false;
        }
        if (normalized.contains(" status success ")
            || normalized.contains(" successfully ")
            || normalized.contains(" completed ")
            || normalized.contains(" works ")
            || normalized.contains(" correct ")) {
            return true;
        }
        return !SummaryFlowSupport.assistantSummary(trajectory).isBlank();
    }

    private void addItem(
            List<ReasoningBankMemoryItem> items,
            Set<String> seen,
            String title,
            String description,
            String content,
            int maxItems
    ) {
        if (items.size() >= maxItems) {
            return;
        }
        String normalizedKey = SummaryFlowSupport.normalizeForMatch(title + " " + description + " " + content);
        if (normalizedKey.isBlank() || !seen.add(normalizedKey)) {
            return;
        }
        items.add(new ReasoningBankMemoryItem(
            SummaryFlowSupport.limit(title, 120),
            SummaryFlowSupport.limit(description, 180),
            SummaryFlowSupport.limit(content, 420)
        ));
    }

    private String defaultToolName(String tool, String fallback) {
        return tool == null || tool.isBlank() ? fallback : tool;
    }

    private String fallbackQueryHint(String query) {
        String hint = SummaryFlowSupport.queryHint(query);
        return hint.isBlank() ? "similar tasks" : hint;
    }
}
