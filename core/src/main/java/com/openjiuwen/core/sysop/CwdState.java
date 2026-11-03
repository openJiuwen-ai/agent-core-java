/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code CwdState} in
 * {@code openjiuwen/core/sys_operation/cwd.py}.
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
}
