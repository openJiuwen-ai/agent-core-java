/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.visualization.Drawable;

/**
 * Interface for components that have an associated {@link Drawable} graph for visualization.
 * <p>
 * Used by loop components, sub-workflow components, and any component containing
 * a nested graph structure.
 * </p>
 * 
 * @since 0.1.7
 */
public interface HasDrawable {
    /**
     * getDrawable.
     * 
     * @return the result
     * @since 0.1.7
     */
    Drawable getDrawable();
}
