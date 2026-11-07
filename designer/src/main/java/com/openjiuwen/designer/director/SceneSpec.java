/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

/**
 * One scene setting.
 *
 * @param settingId setting id
 * @param name display name
 * @param description visual description
 * @since 2026-11-06
 */
public record SceneSpec(String settingId, String name, String description) {

    public SceneSpec {
        settingId = settingId == null || settingId.isBlank() ? "set_1" : settingId.strip();
        name = name == null ? "Scene" : name.strip();
        description = description == null ? "" : description.strip();
    }
}
