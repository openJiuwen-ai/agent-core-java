/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.trajectory.TrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for trajectory collection during agent execution.
 * <p>
 * Only collects trajectories, does not trigger any evolution logic.
 * </p>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Observability and debugging: Record complete agent behavior trajectories</li>
 *   <li>Offline data collection: Accumulate data for subsequent offline training</li>
 *   <li>Behavior analysis: Write trajectories to storage for external system consumption</li>
 * </ul>
 *
 * <p>Bound to the evolution framework (inherits EvolutionRail), trajectory format
 * is fully consistent with the evolution path.</p>
 *
 * <p>Mirrors Python's {@code TrajectoryRail} in
 * {@code openjiuwen.harness.rails.evolution.trajectory_rail}.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * agent.addRail(new TrajectoryRail());  // Ready to use, no config needed
 * }</pre>
 *
 * <p>For custom trajectory storage:</p>
 * <pre>{@code
 * TrajectoryStore store = new FileTrajectoryStore(Path.of("/path/to/trajectories"));
 * agent.addRail(new TrajectoryRail(store));
 * }</pre>
 */
public class TrajectoryRail extends EvolutionRail {

    private static final Logger LOG = LoggerFactory.getLogger(TrajectoryRail.class);

    /** Priority for this rail (lower than security rails, higher than user rails). */
    public static final int PRIORITY = 10;

    private final TrajectoryStore trajectoryStore;

    /**
     * Create a TrajectoryRail with default InMemoryTrajectoryStore.
     */
    public TrajectoryRail() {
        this(null);
    }

    /**
     * Create a TrajectoryRail with a custom trajectory store.
     *
     * @param trajectoryStore Optional trajectory store. If null, uses InMemoryTrajectoryStore.
     */
    public TrajectoryRail(TrajectoryStore trajectoryStore) {
        super(trajectoryStore, null, false, EvolutionTriggerPoint.NONE, true);
        this.trajectoryStore = trajectoryStore != null ? trajectoryStore : new InMemoryTrajectoryStore();
    }

    /**
     * Get the trajectory store.
     *
     * @return the trajectory store
     */
    public TrajectoryStore getTrajectoryStore() {
        return trajectoryStore;
    }

    @Override
    public void init(Object agent) {
        LOG.info("[TrajectoryRail] Initialized with trajectory store: {}",
                trajectoryStore.getClass().getSimpleName());
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TrajectoryRail] Uninitialized");
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
