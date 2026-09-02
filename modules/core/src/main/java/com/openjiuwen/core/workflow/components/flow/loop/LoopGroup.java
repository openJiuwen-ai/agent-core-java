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
 * @since 0.1.7
 */
public class LoopGroup extends com.openjiuwen.core.workflow.component.loop.LoopGroup {
    /**
     * addWorkflowComp.
     * 
     * @param id id
     * @param c c
     * @param in in
     * @param sin sin
     * @param isWait isWait
     * @param ab ab
     * @return the result
     * @since 0.1.7
     */
    public LoopGroup addWorkflowComp(String id, ComponentComposable c, Object in, Object sin, Boolean isWait, List ab) {
        super.addWorkflowComp(id, c, isWait, in, null, sin, null, conv(ab));
        return this;
    }

    /**
     * addWorkflowComp.
     * 
     * @param id id
     * @param c c
     * @param in in
     * @param sin sin
     * @param isWait isWait
     * @param ab ab
     * @return the result
     * @since 0.1.7
     */
    public LoopGroup addWorkflowComp(String id, Object c, Object in, Object sin, Boolean isWait, List ab) {
        return addWorkflowComp(id, wrap(c), in, sin, isWait, ab);
    }

    /**
     * addWorkflowComp.
     * 
     * @param id id
     * @param c c
     * @param in in
     * @return the result
     * @since 0.1.7
     */
    public LoopGroup addWorkflowComp(String id, ComponentComposable c, Object in) {
        super.addWorkflowComp(id, c, null, in, null, null, null, null);
        return this;
    }

    /**
     * addWorkflowComp.
     * 
     * @param id id
     * @param c c
     * @param in in
     * @return the result
     * @since 0.1.7
     */
    public LoopGroup addWorkflowComp(String id, Object c, Object in) {
        super.addWorkflowComp(id, wrap(c), null, in, null, null, null, null);
        return this;
    }

    /**
     * addWorkflowComp.
     * 
     * @param id id
     * @param c c
     * @return the result
     * @since 0.1.7
     */
    public LoopGroup addWorkflowComp(String id, ComponentComposable c) {
        super.addWorkflowComp(id, c, null, null, null, null, null, null);
        return this;
    }

    /**
     * addWorkflowComp.
     * 
     * @param id id
     * @param c c
     * @return the result
     * @since 0.1.7
     */
    public LoopGroup addWorkflowComp(String id, Object c) {
        super.addWorkflowComp(id, wrap(c), null, null, null, null, null, null);
        return this;
    }

    /**
     * wrap.
     * 
     * @param o o
     * @return the result
     * @since 0.1.7
     */
    private static ComponentComposable wrap(Object o) {
        if (o instanceof ComponentComposable c) {
            return c;
        }
        return new WorkflowComponent() {
            @Override
            public Object invoke(Object i, com.openjiuwen.core.session.NodeSessionApi s,
                    com.openjiuwen.core.context.ModelContext cx) {
                return i;
            }
        };
    }

    /**
     * conv.
     * 
     * @param a a
     * @return the result
     * @since 0.1.7
     */
    private static List conv(List a) {
        if (a == null) {
            return List.of();
        }
        List r = new ArrayList();
        for (Object x : a) {
            if (x instanceof ComponentAbility ca) {
                r.add(com.openjiuwen.core.workflow.component.ComponentAbility.valueOf(ca.name()));
            }
        }
        return r;
    }
}
