/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result returned after registering an ability.
 *
 * <p>Mirrors Python's {@code AddAbilityResult} in
 * {@code openjiuwen/core/single_agent/ability_manager.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddAbilityResult {
    private String name;
    private boolean added;
    private String reason = "";

    public AddAbilityResult() {
    }

    public AddAbilityResult(String name, boolean added, String reason) {
        this.name = name;
        this.added = added;
        this.reason = reason == null ? "" : reason;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAdded() {
        return added;
    }

    public void setAdded(boolean added) {
        this.added = added;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }
}
