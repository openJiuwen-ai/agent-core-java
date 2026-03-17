/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.visualization.Drawable;

/**
 * Interface for components that have an associated {@link Drawable} graph for visualization.
 *
 * <p>Used by loop components, sub-workflow components, and any component containing
 * a nested graph structure.</p>
 */
public interface HasDrawable {

    /**
     * Gets the drawable visualization graph for this component.
     *
     * @return the drawable instance
     */
    Drawable getDrawable();
}
