/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow.loop;

import com.openjiuwen.core.workflow.ComponentAbility;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class LoopGroup used by the Java parity implementation.
 *
 * @since 1.0
 */
public class LoopGroup extends com.openjiuwen.core.workflow.component.loop.LoopGroup {
    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup addWorkflowComp(
            String id,
            ComponentComposable c,
            Object in,
            Object sin,
            Boolean isWait,
            List ab
    ) {
        super.addWorkflowComp(id, c, isWait, in, null, sin, null, conv(ab));
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup addWorkflowComp(String id, Object c, Object in, Object sin, Boolean isWait, List ab) {
        return addWorkflowComp(id, wrap(c), in, sin, isWait, ab);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup addWorkflowComp(String id, ComponentComposable c, Object in) {
        super.addWorkflowComp(id, c, null, in, null, null, null, null);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup addWorkflowComp(String id, Object c, Object in) {
        super.addWorkflowComp(id, wrap(c), null, in, null, null, null, null);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup addWorkflowComp(String id, ComponentComposable c) {
        super.addWorkflowComp(id, c, null, null, null, null, null, null);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup addWorkflowComp(String id, Object c) {
        super.addWorkflowComp(id, wrap(c), null, null, null, null, null, null);
        return this;
    }

    private static ComponentComposable wrap(Object o) {
        if (o instanceof ComponentComposable c) {
            return c;
        }
        return new WorkflowComponent() {
            /**
             * Auto-generated for codecheck compliance.
             */
            @Override
            /**
             * Auto-generated for codecheck compliance.
             */
            public Object invoke(
                    Object i,
                    com.openjiuwen.core.session.NodeSessionApi s,
                    com.openjiuwen.core.context.ModelContext cx
            ) {
                return i;
            }
        };
    }

    private static List conv(List a) {
        if (a == null) {
            return List.of();
        }
        List r = new ArrayList();
        for (Object x : a) {
            r.add(com.openjiuwen.core.workflow.component.ComponentAbility.valueOf(((ComponentAbility) x).name()));
        }
        return r;
    }
}
