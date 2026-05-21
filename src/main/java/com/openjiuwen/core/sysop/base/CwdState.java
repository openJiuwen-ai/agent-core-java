/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * CWD state container.
 *
 * <p>Mirrors Python's {@code CwdState} in
 * {@code openjiuwen.core.sys_operation.cwd}.</p>
 *
 * <p>Three-layer CWD model:</p>
 * <ul>
 *   <li>Layer 1 -- project_root: project identity anchor (set once)</li>
 *   <li>Layer 2 -- original_cwd: session start point (worktree lifecycle)</li>
 *   <li>Layer 3 -- cwd: current working directory (updated after shell cmds)</li>
 * </ul>
 *
 * <p>Reading priority: cwd -> original_cwd -> system default</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwdState {

    /** Current working directory. */
    private String cwd;

    /** Original CWD at session start. */
    private String originalCwd;

    /** Project root directory. */
    private String projectRoot;

    /** Agent workspace root. */
    private String workspace;

    /** Shared team workspace root. */
    private String teamWorkspace;
}