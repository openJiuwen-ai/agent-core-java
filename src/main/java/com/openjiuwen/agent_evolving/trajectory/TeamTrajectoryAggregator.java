/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Team trajectory aggregator.
 * <p>
 * Reads individual member trajectories from a shared store and
 * aggregates them into a combined view for team-level analysis.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.aggregator}.
 */
public class TeamTrajectoryAggregator {

    private final TrajectoryStore store;
    private final String teamId;

    /**
     * Create aggregator with TrajectoryStore.
     *
     * @param store Trajectory store
     * @param teamId Team identifier
     */
    public TeamTrajectoryAggregator(TrajectoryStore store, String teamId) {
        if (store == null) {
            throw new IllegalArgumentException("Either 'store' or 'trajectoriesDir' must be provided");
        }
        this.store = store;
        this.teamId = teamId;
    }

    /**
     * Create aggregator with a file-backed trajectory directory.
     *
     * @param trajectoriesDir Directory containing trajectory JSONL files
     * @param teamId Team identifier
     */
    public TeamTrajectoryAggregator(Path trajectoriesDir, String teamId) {
        this(createFileStore(trajectoriesDir), teamId);
    }

    /**
     * Preserve Python's constructor validation when no store is provided.
     *
     * @param teamId Team identifier
     */
    public TeamTrajectoryAggregator(String teamId) {
        throw new IllegalArgumentException("Either 'store' or 'trajectoriesDir' must be provided");
    }

    private static TrajectoryStore createFileStore(Path trajectoriesDir) {
        if (trajectoriesDir == null) {
            throw new IllegalArgumentException("Either 'store' or 'trajectoriesDir' must be provided");
        }
        return new FileTrajectoryStore(trajectoriesDir);
    }

    /**
     * Aggregated team trajectory for a single session.
     */
    public static class TeamTrajectory {
        private final String teamId;
        private final String sessionId;
        private final Trajectory combined;
        private final Map<String, Trajectory> members;

        public TeamTrajectory(String teamId, String sessionId, Trajectory combined, Map<String, Trajectory> members) {
            this.teamId = teamId;
            this.sessionId = sessionId;
            this.combined = combined;
            this.members = members;
        }

        public String getTeamId() {
            return teamId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Trajectory getCombined() {
            return combined;
        }

        public Map<String, Trajectory> getMembers() {
            return members;
        }
    }

    // Collaborative tool names -- reflect inter-member interaction behavior
    public static final Set<String> COLLABORATIVE_TOOLS = Set.of(
            "view_task",
            "claim_task",
            "send_message",
            "workspace_meta",
            "read_file",
            "write_file"
    );

    // Pure internal tools -- member's own work
    public static final Set<String> INTERNAL_TOOLS = Set.of(
            "bash",
            "python",
            "node",
            "edit",
            "grep",
            "glob",
            "web_search",
            "web_fetch"
    );

    // Cross-member interaction meta markers
    public static final Set<String> CROSS_MEMBER_META_KEYS = Set.of(
            "invoke_id",
            "parent_invoke_id",
            "child_invokes"
    );

    /**
     * Aggregate all member trajectories for the given session.
     *
     * @param sessionId Session to aggregate
     * @return TeamTrajectory with merged view
     */
    public TeamTrajectory aggregate(String sessionId) {
        return aggregate(sessionId, true);
    }

    /**
     * Aggregate all member trajectories for the given session.
     *
     * @param sessionId Session to aggregate
     * @param filterCollaborative If true, apply filterMemberTrajectory
     * @return TeamTrajectory with merged view
     */
    public TeamTrajectory aggregate(String sessionId, boolean filterCollaborative) {
        List<Trajectory> trajectories = store.queryBySessionId(sessionId);
        if (trajectories.isEmpty()) {
            return emptyCombined(sessionId);
        }

        Map<String, Trajectory> members = new LinkedHashMap<>();
        for (Trajectory traj : trajectories) {
            String mid = traj.getMeta() != null
                    ? (String) traj.getMeta().getOrDefault("member_id", executionPrefix(traj.getExecutionId()))
                    : executionPrefix(traj.getExecutionId());
            Trajectory processed = traj;
            if (filterCollaborative && !mid.equals("leader")) {
                processed = filterMemberTrajectory(traj);
            }
            if (processed.getSteps() != null && !processed.getSteps().isEmpty()) {
                members.put(mid, processed);
            }
        }

        if (members.isEmpty()) {
            return emptyCombined(sessionId);
        }

        Trajectory combined = merge(members, sessionId);
        return new TeamTrajectory(teamId, sessionId, combined, members);
    }

    private static String executionPrefix(String executionId) {
        if (executionId == null) {
            return "";
        }
        return executionId.substring(0, Math.min(8, executionId.length()));
    }

    /**
     * Merge all member trajectories into a combined view.
     */
    private Trajectory merge(Map<String, Trajectory> members, String sessionId) {
        List<TrajectoryStep> allSteps = new ArrayList<>();
        for (Trajectory traj : members.values()) {
            if (traj.getSteps() != null) {
                allSteps.addAll(traj.getSteps());
            }
        }

        // Sort by start_time_ms for temporal ordering
        allSteps.sort((a, b) -> {
            long aTime = a.getStartTimeMs() != null ? a.getStartTimeMs() : 0L;
            long bTime = b.getStartTimeMs() != null ? b.getStartTimeMs() : 0L;
            return Long.compare(aTime, bTime);
        });

        // Aggregate costs
        long totalInput = 0;
        long totalOutput = 0;
        for (Trajectory traj : members.values()) {
            if (traj.getCost() != null) {
                totalInput += ((Number) traj.getCost().getOrDefault("input_tokens", 0)).longValue();
                totalOutput += ((Number) traj.getCost().getOrDefault("output_tokens", 0)).longValue();
            }
        }

        Map<String, Integer> cost = null;
        if (totalInput > 0 || totalOutput > 0) {
            cost = new LinkedHashMap<>();
            cost.put("input_tokens", (int) totalInput);
            cost.put("output_tokens", (int) totalOutput);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("member_count", members.size());

        return new Trajectory(
                "team-" + teamId,
                sessionId,
                "online",
                allSteps,
                cost,
                meta
        );
    }

    /**
     * Return an empty combined trajectory.
     */
    private TeamTrajectory emptyCombined(String sessionId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("member_count", 0);

        Trajectory combined = new Trajectory(
                "team-" + teamId,
                sessionId,
                "online",
                Collections.emptyList(),
                null,
                meta
        );
        return new TeamTrajectory(teamId, sessionId, combined, Collections.emptyMap());
    }

    /**
     * Filter a member's trajectory to keep only collaboration-relevant steps.
     *
     * Retains steps that reflect inter-member behavior:
     * - Steps with cross-member meta keys
     * - Tool calls using collaborative tool names
     * - Skips pure internal LLM reasoning and internal tool calls
     *
     * @param trajectory Original trajectory
     * @return Filtered trajectory
     */
    public static Trajectory filterMemberTrajectory(Trajectory trajectory) {
        List<TrajectoryStep> filteredSteps = trajectory.getSteps() != null
                ? trajectory.getSteps().stream()
                    .filter(TeamTrajectoryAggregator::isCollaborativeStep)
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return new Trajectory(
                trajectory.getExecutionId(),
                trajectory.getSessionId(),
                trajectory.getSource(),
                filteredSteps,
                trajectory.getCost(),
                trajectory.getMeta()
        );
    }

    /**
     * Return true if the step reflects inter-member collaboration.
     */
    private static boolean isCollaborativeStep(TrajectoryStep step) {
        // 1. Cross-member invoke markers
        if (step.getMeta() != null) {
            for (String key : CROSS_MEMBER_META_KEYS) {
                if (step.getMeta().containsKey(key)) {
                    return true;
                }
            }
        }

        // 2. Tool steps: check tool name
        if ("tool".equals(step.getKind()) && step.getDetail() != null) {
            Object detail = step.getDetail();
            String toolName = "";
            if (detail instanceof ToolCallDetail) {
                toolName = ((ToolCallDetail) detail).getToolName();
            } else if (detail instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> detailMap = (Map<String, Object>) detail;
                Object toolNameObj = detailMap.get("tool_name");
                if (toolNameObj == null) {
                    toolNameObj = detailMap.get("toolName");
                }
                toolName = toolNameObj != null ? String.valueOf(toolNameObj) : "";
            }
            toolName = toolName.toLowerCase();

            if (COLLABORATIVE_TOOLS.contains(toolName)) {
                return true;
            }
            // Also keep any tool whose name suggests reading team skill files
            if (toolName.contains("read")) {
                String argsStr = "";
                if (detail instanceof ToolCallDetail) {
                    argsStr = String.valueOf(((ToolCallDetail) detail).getCallArgs());
                } else if (detail instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> detailMap = (Map<String, Object>) detail;
                    Object argsObj = detailMap.get("call_args");
                    if (argsObj == null) {
                        argsObj = detailMap.get("callArgs");
                    }
                    argsStr = argsObj != null ? String.valueOf(argsObj) : "";
                }
                if (argsStr.toLowerCase().contains("skill")) {
                    return true;
                }
            }
            // Internal tools: explicitly filter out
            if (INTERNAL_TOOLS.contains(toolName)) {
                return false;
            }
            // Unknown tools: keep them (conservative)
            return true;
        }

        // 3. LLM steps without cross-member markers: filter out
        return false;
    }
}
