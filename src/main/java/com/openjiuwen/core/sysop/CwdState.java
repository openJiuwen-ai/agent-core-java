/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable CWD state container.
 * <p>
 * Shared by reference within one agent's tool calls.
 * A new instance is created per agent to provide inter-agent isolation.
 * <p>
 * Mirrors Python's {@code CwdState} dataclass from
 * {@code core/sys_operation/cwd.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CwdState {

    private String cwd;
    private String originalCwd;
    private String projectRoot;
    private String workspace;
    private String teamWorkspace;

    /**
     * Resolve the effective CWD using the three-layer fallback:
     * cwd → originalCwd → system default
     */
    public String getEffectiveCwd() {
        if (cwd != null && !cwd.isEmpty()) {
            return cwd;
        }
        if (originalCwd != null && !originalCwd.isEmpty()) {
            return originalCwd;
        }
        return System.getProperty("user.dir");
    }
}
