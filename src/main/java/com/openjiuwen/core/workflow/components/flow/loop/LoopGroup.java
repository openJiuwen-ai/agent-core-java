/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.flow.loop;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.session.BaseSession;

import java.util.List;

/**
 * Compatibility facade for the loop group workflow component.
 *
 * <p>Mirrors Python's {@code LoopGroup} in
 * {@code openjiuwen/core/workflow/components/flow/loop.py}.</p>
 */
public class LoopGroup extends com.openjiuwen.core.workflow.component.loop.LoopGroup {

    public LoopGroup addWorkflowComp(String id, ComponentComposable component, Object inputsSchema) {
        return addWorkflowComp(id, component);
    }

    public LoopGroup addWorkflowComp(String id, Object component, Object inputsSchema) {
        return addWorkflowComp(id, wrap(component));
    }

    public LoopGroup addWorkflowComp(String id, ComponentComposable component, Object inputsSchema,
            Object streamInputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility) {
        return addWorkflowComp(id, component);
    }

    public LoopGroup addWorkflowComp(String id, Object component, Object inputsSchema,
            Object streamInputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility) {
        return addWorkflowComp(id, wrap(component));
    }

    public LoopGroup addWorkflowComp(String id, ComponentComposable component, Boolean waitForAll,
            Object inputsSchema, Object outputsSchema, Object streamInputsSchema,
            Object streamOutputsSchema, List<ComponentAbility> compAbility) {
        return addWorkflowComp(id, component);
    }

    public LoopGroup addWorkflowComp(String id, Object component, Boolean waitForAll,
            Object inputsSchema, Object outputsSchema, Object streamInputsSchema,
            Object streamOutputsSchema, List<ComponentAbility> compAbility) {
        return addWorkflowComp(id, wrap(component));
    }

    public LoopGroup addWorkflowComp(String id, ComponentComposable component) {
        super.addWorkflowComp(id, component);
        return this;
    }

    public LoopGroup addWorkflowComp(String id, Object component) {
        return addWorkflowComp(id, wrap(component));
    }

    private static ComponentComposable wrap(Object component) {
        if (component instanceof ComponentComposable composable) {
            return composable;
        }
        return new WorkflowComponent<Object, Object>() {
            @Override
            public Object invoke(Object inputs, BaseSession session, ModelContext context) {
                return inputs;
            }
        };
    }
}
