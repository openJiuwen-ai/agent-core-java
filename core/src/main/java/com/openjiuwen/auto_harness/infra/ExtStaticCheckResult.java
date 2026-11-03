/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.util.ArrayList;
import java.util.List;

/**
 * Static verification counts and errors for an extension.
 * <p>
 * Mirrors Python's {@code ExtStaticCheckResult} in
 * {@code openjiuwen/auto_harness/infra/runtime_extension_static_checks.py}.
 */
public class ExtStaticCheckResult {

    private List<String> errors = new ArrayList<>();
    private int railsCount;
    private int toolsCount;
    private int skillsCount;
    private int skillDirsCount;

    public ExtStaticCheckResult() {
    }

    public ExtStaticCheckResult(
            List<String> errors,
            int railsCount,
            int toolsCount,
            int skillsCount,
            int skillDirsCount
    ) {
        setErrors(errors);
        this.railsCount = railsCount;
        this.toolsCount = toolsCount;
        this.skillsCount = skillsCount;
        this.skillDirsCount = skillDirsCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
    }

    public int getRailsCount() {
        return railsCount;
    }

    public void setRailsCount(int railsCount) {
        this.railsCount = railsCount;
    }

    public int getToolsCount() {
        return toolsCount;
    }

    public void setToolsCount(int toolsCount) {
        this.toolsCount = toolsCount;
    }

    public int getSkillsCount() {
        return skillsCount;
    }

    public void setSkillsCount(int skillsCount) {
        this.skillsCount = skillsCount;
    }

    public int getSkillDirsCount() {
        return skillDirsCount;
    }

    public void setSkillDirsCount(int skillDirsCount) {
        this.skillDirsCount = skillDirsCount;
    }
}
