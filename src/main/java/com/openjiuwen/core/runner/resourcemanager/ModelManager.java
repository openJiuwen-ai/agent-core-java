/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.session.tracer.TracerDecorator;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Model resource manager.
 *
 * <p>Mirrors Python's {@code ModelMgr} in
 * {@code openjiuwen/core/runner/resources_manager/model_manager.py}.</p>
 */
public class ModelManager extends AbstractManager<Object> {

    public void addModel(String modelId, Supplier<?> model) {
        registerResourceProvider(modelId, model);
    }

    public Supplier<?> removeModel(String modelId) {
        return unregisterResourceProvider(modelId);
    }

    public CompletionStage<Object> getModel(String modelId) {
        return getModel(modelId, null);
    }

    public CompletionStage<Object> getModel(String modelId, Object session) {
        return getResource(modelId).thenApply(model -> TracerDecorator.decorateModelWithTrace(model, session));
    }
}
