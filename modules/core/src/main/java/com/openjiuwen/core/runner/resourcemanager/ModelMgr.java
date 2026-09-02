/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.llm.Model;

import java.util.function.Supplier;

/**
 * Manager for Model resource providers.
 * Mirrors Python's {@code ModelMgr} in {@code resources_manager/model_manager.py}.
 * 
 * @since 0.1.7
 */
public class ModelMgr extends AbstractManager<Model> {
    /**
     * addModel.
     * 
     * @param modelId modelId
     * @param model model
     * @since 0.1.7
     */
    public void addModel(String modelId, Supplier<Model> model) {
        registerResourceProvider(modelId, model);
    }

    /**
     * removeModel.
     * 
     * @param modelId modelId
     * @return the result
     * @since 0.1.7
     */
    public Supplier<? extends Model> removeModel(String modelId) {
        return unregisterResourceProvider(modelId);
    }

    /**
     * getModel.
     * 
     * @param modelId modelId
     * @return the result
     * @since 0.1.7
     */
    public Model getModel(String modelId) {
        return getResource(modelId);
    }
}
