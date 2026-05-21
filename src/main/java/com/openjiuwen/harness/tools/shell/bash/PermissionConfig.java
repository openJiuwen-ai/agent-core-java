/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration for the permission pipeline.
 *
 * <p>Mirrors Python's PermissionConfig in
 * {@code openjiuwen.harness.tools.shell.bash._permission}.
 */
public class PermissionConfig {

    private PermissionMode mode;
    private List<Pattern> denyPatterns;
    private List<Pattern> allowPatterns;

    public PermissionConfig() {
        this.mode = PermissionMode.AUTO;
        this.denyPatterns = new ArrayList<>();
        this.allowPatterns = new ArrayList<>();
    }

    public PermissionConfig(PermissionMode mode, List<Pattern> denyPatterns, List<Pattern> allowPatterns) {
        this.mode = mode;
        this.denyPatterns = denyPatterns != null ? denyPatterns : new ArrayList<>();
        this.allowPatterns = allowPatterns != null ? allowPatterns : new ArrayList<>();
    }

    public PermissionMode getMode() {
        return mode;
    }

    public void setMode(PermissionMode mode) {
        this.mode = mode;
    }

    public List<Pattern> getDenyPatterns() {
        return denyPatterns;
    }

    public void setDenyPatterns(List<Pattern> denyPatterns) {
        this.denyPatterns = denyPatterns != null ? denyPatterns : new ArrayList<>();
    }

    public List<Pattern> getAllowPatterns() {
        return allowPatterns;
    }

    public void setAllowPatterns(List<Pattern> allowPatterns) {
        this.allowPatterns = allowPatterns != null ? allowPatterns : new ArrayList<>();
    }

    public static List<Pattern> compilePatterns(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String p : raw) {
            patterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }
}