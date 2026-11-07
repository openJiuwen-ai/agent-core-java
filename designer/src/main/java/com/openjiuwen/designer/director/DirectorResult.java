/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import com.openjiuwen.designer.schema.DesignerGraph;

/**
 * Result of running the director chain.
 *
 * @param graph authored graph
 * @param analysis structured analysis
 * @param brief approved brief
 * @param storyboard approved storyboard
 * @since 2026-11-06
 */
public record DirectorResult(
        DesignerGraph graph,
        ScriptAnalysis analysis,
        String brief,
        String storyboard) {
}
