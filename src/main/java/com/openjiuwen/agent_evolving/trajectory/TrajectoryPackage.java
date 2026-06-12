/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import com.openjiuwen.agent_evolving.trajectory.extractor.TrajectoryExtractor;

import java.util.List;

/**
 * Package bridge for trajectory exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/agent_evolving/trajectory/__init__.py}.
 * </p>
 */
public final class TrajectoryPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/trajectory/__init__.py";
    public static final Class<LLMCallDetail> LLM_CALL_DETAIL = LLMCallDetail.class;
    public static final Class<StepKind> STEP_KIND = StepKind.class;
    public static final Class<ToolCallDetail> TOOL_CALL_DETAIL = ToolCallDetail.class;
    public static final Class<Trajectory> TRAJECTORY = Trajectory.class;
    public static final Class<TrajectoryStep> TRAJECTORY_STEP = TrajectoryStep.class;
    public static final Class<UpdateKey> UPDATE_KEY = UpdateKey.class;
    public static final Class<Updates> UPDATES = Updates.class;
    public static final Class<TrajectoryBuilder> TRAJECTORY_BUILDER = TrajectoryBuilder.class;
    public static final Class<TrajectoryExtractor> TRAJECTORY_EXTRACTOR = TrajectoryExtractor.class;
    public static final Class<TrajectoryExtractor> TRACER_TRAJECTORY_EXTRACTOR = TrajectoryExtractor.class;
    public static final Class<TrajectoryStore> TRAJECTORY_STORE = TrajectoryStore.class;
    public static final Class<InMemoryTrajectoryStore> IN_MEMORY_TRAJECTORY_STORE = InMemoryTrajectoryStore.class;
    public static final Class<FileTrajectoryStore> FILE_TRAJECTORY_STORE = FileTrajectoryStore.class;
    public static final Class<TeamTrajectory> TEAM_TRAJECTORY = TeamTrajectory.class;
    public static final Class<TeamTrajectoryAggregator> TEAM_TRAJECTORY_AGGREGATOR = TeamTrajectoryAggregator.class;
    public static final Class<InMemoryTrajectoryRegistry> IN_MEMORY_TRAJECTORY_REGISTRY =
            InMemoryTrajectoryRegistry.class;
    public static final Class<MemberTrajectorySnapshot> MEMBER_TRAJECTORY_SNAPSHOT =
            MemberTrajectorySnapshot.class;
    public static final Class<TrajectorySink> TRAJECTORY_SINK = TrajectorySink.class;
    public static final Class<TrajectorySource> TRAJECTORY_SOURCE = TrajectorySource.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "LLMCallDetail",
            "StepKind",
            "ToolCallDetail",
            "Trajectory",
            "TrajectoryStep",
            "UpdateKey",
            "Updates",
            "TrajectoryBuilder",
            "TrajectoryExtractor",
            "TracerTrajectoryExtractor",
            "TrajectoryStore",
            "InMemoryTrajectoryStore",
            "FileTrajectoryStore",
            "TeamTrajectory",
            "TeamTrajectoryAggregator",
            "aggregate_member_trajectories",
            "filter_member_trajectory",
            "InMemoryTrajectoryRegistry",
            "MemberTrajectorySnapshot",
            "TrajectorySink",
            "TrajectorySource"
    );

    private TrajectoryPackage() {
    }

    public static Trajectory aggregateMemberTrajectories(List<Trajectory> trajectories,
                                                         String teamId,
                                                         String sessionId,
                                                         boolean filterCollaborative) {
        return TeamTrajectoryAggregator.aggregateMemberTrajectories(
                trajectories,
                teamId,
                sessionId,
                filterCollaborative
        );
    }

    public static Trajectory filterMemberTrajectory(Trajectory trajectory) {
        return TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);
    }
}
