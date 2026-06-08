/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mirrors Python's {@code TeamTrajectoryAggregator} in
 * {@code openjiuwen/agent_evolving/trajectory/aggregator.py}.
 */
public class TeamTrajectoryAggregator {

    private static final Set<String> COLLABORATIVE_TOOLS = Set.of(
            "view_task",
            "claim_task",
            "send_message",
            "workspace_meta"
    );
    private static final Set<String> CROSS_MEMBER_META_KEYS = Set.of(
            "invoke_id",
            "parent_invoke_id",
            "child_invokes"
    );
    private static final List<String> MEMBER_ROLE_META_KEYS = List.of("member_role", "role");
    private static final String LEADER_ROLE = "leader";

    private final TrajectoryStore store;
    private final String teamId;

    public TeamTrajectoryAggregator(TrajectoryStore store, String teamId) {
        this(store, null, teamId);
    }

    public TeamTrajectoryAggregator(Path trajectoriesDir, String teamId) {
        this(null, trajectoriesDir, teamId);
    }

    public TeamTrajectoryAggregator(TrajectoryStore store, Path trajectoriesDir, String teamId) {
        if (store != null) {
            this.store = store;
        } else if (trajectoriesDir != null) {
            this.store = new FileTrajectoryStore(trajectoriesDir);
        } else {
            throw new IllegalArgumentException("Either 'store' or 'trajectories_dir' must be provided");
        }
        this.teamId = Objects.requireNonNull(teamId, "teamId must not be null");
    }

    public TeamTrajectory aggregate(String sessionId) {
        return aggregate(sessionId, true);
    }

    public TeamTrajectory aggregate(String sessionId, boolean filterCollaborative) {
        List<Trajectory> trajectories = store.queryBySessionId(sessionId);
        if (trajectories.isEmpty()) {
            return emptyCombined(sessionId);
        }

        Map<String, Trajectory> members = memberTrajectoriesById(trajectories, filterCollaborative);
        if (members.isEmpty()) {
            return emptyCombined(sessionId);
        }

        return new TeamTrajectory(
                teamId,
                sessionId,
                buildCombinedTrajectory(members, teamId, sessionId),
                members
        );
    }

    public static Trajectory aggregateMemberTrajectories(List<Trajectory> trajectories,
                                                         String teamId,
                                                         String sessionId,
                                                         boolean filterCollaborative) {
        return buildCombinedTrajectory(
                memberTrajectoriesById(trajectories, filterCollaborative),
                teamId,
                sessionId
        );
    }

    public static Trajectory filterMemberTrajectory(Trajectory trajectory) {
        List<TrajectoryStep> filteredSteps = new ArrayList<>();
        List<TrajectoryStep> steps = trajectory.getSteps();
        if (steps != null) {
            for (TrajectoryStep step : steps) {
                if (isCollaborativeStep(step)) {
                    filteredSteps.add(step);
                }
            }
        }

        return new Trajectory(
                trajectory.getExecutionId(),
                trajectory.getSessionId(),
                trajectory.getSource(),
                filteredSteps,
                trajectory.getCost(),
                trajectory.getMeta()
        );
    }

    private TeamTrajectory emptyCombined(String sessionId) {
        Trajectory combined = Trajectory.builder()
                .executionId("team-" + teamId)
                .sessionId(sessionId)
                .source("online")
                .steps(List.of())
                .meta(Map.of("member_count", 0))
                .build();
        return new TeamTrajectory(teamId, sessionId, combined, Map.of());
    }

    private static Map<String, Trajectory> memberTrajectoriesById(List<Trajectory> trajectories,
                                                                  boolean filterCollaborative) {
        Map<String, Trajectory> members = new LinkedHashMap<>();
        if (trajectories == null) {
            return members;
        }

        for (Trajectory trajectory : trajectories) {
            String memberId = memberIdFor(trajectory);
            Trajectory processed = trajectory;
            if (filterCollaborative && !isLeaderTrajectory(trajectory, memberId)) {
                processed = filterMemberTrajectory(trajectory);
            }
            if (processed.getSteps() != null && !processed.getSteps().isEmpty()) {
                members.put(memberId, mergeMemberTrajectory(members.get(memberId), processed));
            }
        }
        return members;
    }

    private static Trajectory buildCombinedTrajectory(Map<String, Trajectory> members,
                                                      String teamId,
                                                      String sessionId) {
        List<TrajectoryStep> allSteps = new ArrayList<>();
        int totalInput = 0;
        int totalOutput = 0;

        for (Trajectory trajectory : members.values()) {
            if (trajectory.getSteps() != null) {
                allSteps.addAll(trajectory.getSteps());
            }
            Map<String, Integer> cost = trajectory.getCost();
            if (cost != null) {
                totalInput += intValue(cost.get("input_tokens"));
                totalOutput += intValue(cost.get("output_tokens"));
            }
        }

        allSteps.sort((left, right) -> Long.compare(timeOrZero(left), timeOrZero(right)));

        Map<String, Integer> combinedCost = null;
        if (totalInput > 0 || totalOutput > 0) {
            combinedCost = new LinkedHashMap<>();
            combinedCost.put("input_tokens", totalInput);
            combinedCost.put("output_tokens", totalOutput);
        }

        return Trajectory.builder()
                .executionId("team-" + teamId)
                .sessionId(sessionId)
                .source("online")
                .steps(allSteps)
                .cost(combinedCost)
                .meta(Map.of("member_count", members.size()))
                .build();
    }

    private static boolean isLeaderTrajectory(Trajectory trajectory, String memberId) {
        Map<String, Object> meta = trajectory.getMeta();
        if (meta != null) {
            for (String key : MEMBER_ROLE_META_KEYS) {
                Object role = meta.get(key);
                if (role == null) {
                    continue;
                }
                return LEADER_ROLE.equals(asRoleValue(role));
            }
        }
        return LEADER_ROLE.equals(memberId);
    }

    private static String memberIdFor(Trajectory trajectory) {
        Map<String, Object> meta = trajectory.getMeta();
        if (meta != null && meta.get("member_id") != null) {
            return String.valueOf(meta.get("member_id"));
        }
        String executionId = trajectory.getExecutionId();
        if (executionId == null) {
            return "null";
        }
        return executionId.substring(0, Math.min(8, executionId.length()));
    }

    private static Trajectory mergeMemberTrajectory(Trajectory existing, Trajectory next) {
        if (existing == null) {
            return next;
        }

        if (next.getSteps().size() > existing.getSteps().size()
                && stepsArePrefix(existing.getSteps(), next.getSteps())) {
            return next;
        }
        if (existing.getSteps().size() > next.getSteps().size()
                && stepsArePrefix(next.getSteps(), existing.getSteps())) {
            return existing;
        }

        List<TrajectoryStep> mergedSteps = new ArrayList<>(existing.getSteps());
        mergedSteps.addAll(next.getSteps());

        return Trajectory.builder()
                .executionId(existing.getExecutionId())
                .sessionId(existing.getSessionId() != null ? existing.getSessionId() : next.getSessionId())
                .source(existing.getSource())
                .caseId(existing.getCaseId() != null ? existing.getCaseId() : next.getCaseId())
                .steps(mergedSteps)
                .cost(mergeCost(existing.getCost(), next.getCost()))
                .meta(mergeMeta(existing.getMeta(), next.getMeta()))
                .build();
    }

    private static Map<String, Object> mergeMeta(Map<String, Object> first, Map<String, Object> second) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (first != null) {
            merged.putAll(first);
        }
        if (second != null) {
            merged.putAll(second);
        }
        return merged;
    }

    private static Map<String, Integer> mergeCost(Map<String, Integer> first, Map<String, Integer> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return null;
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        mergeCostEntries(merged, first);
        mergeCostEntries(merged, second);
        return merged;
    }

    private static void mergeCostEntries(Map<String, Integer> target, Map<String, Integer> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.put(entry.getKey(), target.getOrDefault(entry.getKey(), 0) + intValue(entry.getValue()));
        }
    }

    private static boolean stepsArePrefix(List<TrajectoryStep> prefix, List<TrajectoryStep> steps) {
        if (prefix.size() > steps.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!stepEquals(prefix.get(i), steps.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean stepEquals(TrajectoryStep first, TrajectoryStep second) {
        return Objects.equals(first.getKind(), second.getKind())
                && Objects.equals(first.getOperatorId(), second.getOperatorId())
                && Objects.equals(first.getAgentId(), second.getAgentId())
                && Objects.equals(first.getRole(), second.getRole())
                && Objects.equals(first.getNodeId(), second.getNodeId())
                && Objects.deepEquals(first.getInputs(), second.getInputs())
                && Objects.deepEquals(first.getOutputs(), second.getOutputs())
                && Objects.deepEquals(first.getError(), second.getError())
                && Objects.equals(first.getStartTimeMs(), second.getStartTimeMs())
                && Objects.equals(first.getEndTimeMs(), second.getEndTimeMs())
                && Objects.equals(first.getReward(), second.getReward())
                && Objects.equals(first.getPromptTokenIds(), second.getPromptTokenIds())
                && Objects.equals(first.getCompletionTokenIds(), second.getCompletionTokenIds())
                && Objects.deepEquals(first.getLogprobs(), second.getLogprobs())
                && Objects.equals(first.getMeta(), second.getMeta())
                && detailEquals(first.getDetail(), second.getDetail());
    }

    private static boolean detailEquals(Object first, Object second) {
        if (first instanceof ToolCallDetail leftTool && second instanceof ToolCallDetail rightTool) {
            return Objects.equals(leftTool.getToolName(), rightTool.getToolName())
                    && Objects.deepEquals(leftTool.getCallArgs(), rightTool.getCallArgs())
                    && Objects.deepEquals(leftTool.getCallResult(), rightTool.getCallResult())
                    && Objects.equals(leftTool.getToolDescription(), rightTool.getToolDescription())
                    && Objects.equals(leftTool.getToolSchema(), rightTool.getToolSchema())
                    && Objects.equals(leftTool.getToolCallId(), rightTool.getToolCallId());
        }
        if (first instanceof LLMCallDetail leftLlm && second instanceof LLMCallDetail rightLlm) {
            return Objects.equals(leftLlm.getModel(), rightLlm.getModel())
                    && Objects.equals(leftLlm.getMessages(), rightLlm.getMessages())
                    && Objects.deepEquals(leftLlm.getResponse(), rightLlm.getResponse())
                    && Objects.equals(leftLlm.getTools(), rightLlm.getTools())
                    && Objects.equals(leftLlm.getUsage(), rightLlm.getUsage())
                    && Objects.equals(leftLlm.getMeta(), rightLlm.getMeta());
        }
        return Objects.deepEquals(first, second);
    }

    private static boolean isCollaborativeStep(TrajectoryStep step) {
        Map<String, Object> meta = step.getMeta();
        if (meta != null) {
            for (String key : CROSS_MEMBER_META_KEYS) {
                if (meta.containsKey(key)) {
                    return true;
                }
            }
        }

        if (!"tool".equals(step.getKind()) || step.getDetail() == null) {
            return false;
        }

        String toolName = toolName(step.getDetail()).toLowerCase(Locale.ROOT);
        return COLLABORATIVE_TOOLS.contains(toolName) || isTeamSkillFileAccess(step.getDetail(), toolName);
    }

    private static boolean isTeamSkillFileAccess(Object detail, String toolName) {
        if (!toolName.contains("read") && !toolName.contains("write")) {
            return false;
        }
        String args = String.valueOf(callArgs(detail)).toLowerCase(Locale.ROOT);
        return args.contains("skill");
    }

    private static String toolName(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail && toolCallDetail.getToolName() != null) {
            return toolCallDetail.getToolName();
        }
        return "";
    }

    private static Object callArgs(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail) {
            return toolCallDetail.getCallArgs();
        }
        return null;
    }

    private static String asRoleValue(Object role) {
        return String.valueOf(role).toLowerCase(Locale.ROOT);
    }

    private static long timeOrZero(TrajectoryStep step) {
        return step.getStartTimeMs() != null ? step.getStartTimeMs() : 0L;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
