/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * 0.1.12-compatible prompt manager alias.
 *
 * <p>Mirrors Python's {@code PromptMgr} in
 * {@code openjiuwen/core/runner/resources_manager/prompt_manager.py}.</p>
 */
public class PromptMgr extends PromptManager {

    public String kind() {
        return "prompt";
    }
}
