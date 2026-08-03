/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.visualization.Drawable;

/**
 * Exposes the drawable graph carried by workflow containers.
 *
 * <p>Mirrors Python's {@code BaseWorkflow.drawable} access in
 * {@code openjiuwen/core/workflow/_workflow.py}.</p>
 */
public interface HasDrawable {

    /**
     * Gets the drawable visualization state.
     *
     * @return drawable graph holder
     */
    Drawable getDrawable();
}
