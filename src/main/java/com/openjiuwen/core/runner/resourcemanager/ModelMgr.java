/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * 0.1.12-compatible model manager alias.
 *
 * <p>Mirrors Python's {@code ModelMgr} in
 * {@code openjiuwen/core/runner/resources_manager/model_manager.py}.</p>
 */
public class ModelMgr extends ModelManager {

    public String kind() {
        return "model";
    }
}
