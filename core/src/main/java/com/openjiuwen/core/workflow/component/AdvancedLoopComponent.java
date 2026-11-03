/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.loop.callback.LoopCallback;

/**
 * Advanced loop component view exposing the loop body used for visualization.
 *
 * <p>Mirrors Python's {@code AdvancedLoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public interface AdvancedLoopComponent extends ComponentComposable {

    /**
     * Gets Python's {@code body} property.
     *
     * @return drawable-owning loop body
     */
    HasDrawable getBody();

    /**
     * Registers a loop callback after construction.
     *
     * @param callback loop callback
     */
    void registerCallback(LoopCallback callback);
}
