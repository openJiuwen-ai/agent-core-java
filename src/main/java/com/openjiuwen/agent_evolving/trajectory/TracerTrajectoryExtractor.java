/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import com.openjiuwen.agent_evolving.trajectory.extractor.TrajectoryExtractor;

import java.util.Optional;

/**
 * Backward-compatible alias for {@link TrajectoryExtractor}.
 *
 * <p>Mirrors Python's {@code TracerTrajectoryExtractor} in
 * {@code openjiuwen.agent_evolving.trajectory.operation}, where the name is
 * imported as an alias of {@code TrajectoryExtractor}.</p>
 */
public class TracerTrajectoryExtractor extends TrajectoryExtractor {

    /**
     * Create extractor without resource manager.
     */
    public TracerTrajectoryExtractor() {
        super();
    }

    /**
     * Create extractor with optional resource manager.
     *
     * @param resourceManager Used to query tool metadata
     */
    public TracerTrajectoryExtractor(Object resourceManager) {
        super(resourceManager);
    }

    /**
     * Java compatibility adapter for callers that pass an execution spec.
     *
     * <p>Python's alias exposes {@code TrajectoryExtractor.extract(session, case_id)}.
     * Java trainer code already passes {@link ExecutionSpec}, so this adapter
     * delegates to the canonical extractor with {@code caseId} and then applies
     * the provided execution id.</p>
     *
     * @param session Agent session with tracer spans
     * @param execution Execution specification for this trajectory
     * @return Trajectory extracted by the canonical extractor
     */
    public Trajectory extract(Object session, ExecutionSpec execution) {
        String caseId = execution != null ? execution.getCaseId() : null;
        Trajectory trajectory = super.extract(session, Optional.ofNullable(caseId));
        if (execution != null) {
            trajectory.setExecutionId(execution.getExecutionId());
        }
        return trajectory;
    }
}
