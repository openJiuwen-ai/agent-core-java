  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.llm.Model;

import java.util.function.Supplier;

/**
 * Manager for Model resource providers.
 * Mirrors Python's {@code ModelMgr} in {@code resources_manager/model_manager.py}.
 */
public class ModelMgr extends AbstractManager<Model> {

    public void addModel(String modelId, Supplier<Model> model) {
        registerResourceProvider(modelId, model);
    }

    public Supplier<? extends Model> removeModel(String modelId) {
        return unregisterResourceProvider(modelId);
    }

    public Model getModel(String modelId) {
        return getResource(modelId);
    }
}
