/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;

/**
 * Loop component view exposing the loop group used for visualization.
 *
 * <p>Mirrors Python's {@code LoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public interface LoopComponent extends ComponentComposable {

    /**
     * Gets Python's {@code loop_group} property.
     *
     * @return drawable-owning loop group
     */
    HasDrawable getLoopGroup();
}
