/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import com.openjiuwen.agent_evolving.trajectory.extractor.TrajectoryExtractor;

/**
 * Trajectory operations module.
 * <p>
 * This class provides backward compatibility alias for TrajectoryExtractor.
 * <p>
 * Mirrors Python's {@code operation} module in
 * {@code openjiuwen.agent_evolving.trajectory.operation}.
 * <p>
 * Note: In Python, this module uses import alias for backward compatibility.
 * In Java, this class serves as an alias/wrapper for TrajectoryExtractor.
 */
public class TrajectoryOperation {

    /**
     * Get the TrajectoryExtractor class for backward compatibility.
     * 
     * @return TrajectoryExtractor class
     */
    public static Class<?> getExtractorClass() {
        return TrajectoryExtractor.class;
    }

    /**
     * Create a new TrajectoryExtractor instance.
     * 
     * @return New TrajectoryExtractor instance
     */
    public static TrajectoryExtractor createExtractor() {
        return new TrajectoryExtractor();
    }

    // Alias class for backward compatibility
    // In Python: from extractor import TrajectoryExtractor as TracerTrajectoryExtractor
    // In Java: Use TrajectoryExtractor directly or via this wrapper
    
    /**
     * Alias type for backward compatibility.
     * Use TrajectoryExtractor directly in new code.
     */
    public static final Class<?> TracerTrajectoryExtractor = TrajectoryExtractor.class;
}