/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code PermissionConfig} in
 * {@code openjiuwen/harness/tools/shell/bash/_permission.py}.
 */
public class PermissionConfig {

    private PermissionMode mode = PermissionMode.AUTO;
    private List<Pattern> denyPatterns = new ArrayList<>();
    private List<Pattern> allowPatterns = new ArrayList<>();

    public PermissionMode getMode() {
        return mode;
    }

    public void setMode(PermissionMode mode) {
        this.mode = mode == null ? PermissionMode.AUTO : mode;
    }

    public List<Pattern> getDenyPatterns() {
        return denyPatterns;
    }

    public void setDenyPatterns(List<Pattern> denyPatterns) {
        this.denyPatterns = denyPatterns == null ? new ArrayList<>() : new ArrayList<>(denyPatterns);
    }

    public List<Pattern> getAllowPatterns() {
        return allowPatterns;
    }

    public void setAllowPatterns(List<Pattern> allowPatterns) {
        this.allowPatterns = allowPatterns == null ? new ArrayList<>() : new ArrayList<>(allowPatterns);
    }

    public static List<Pattern> compilePatterns(List<String> raw) {
        List<Pattern> patterns = new ArrayList<>();
        if (raw == null) {
            return patterns;
        }
        for (String value : raw) {
            patterns.add(Pattern.compile(value, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }
}
