/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.cwd;

import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.CwdState;

import java.nio.file.Path;

/**
 * Thin facade over {@link Cwd}; shares the same thread-local state.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/sys_operation/cwd.py} helpers
 * under the historical {@code CwdContext} name.</p>
 *
 * @since 0.1.7
 */
public final class CwdContext {

    private CwdContext() {
    }

    public static String getCwd() {
        return Cwd.getCwd();
    }

    public static void setCwd(String cwd) {
        Cwd.setCwd(cwd);
    }

    public static String getOriginalCwd() {
        return Cwd.getOriginalCwd();
    }

    public static void setOriginalCwd(String cwd) {
        Cwd.setOriginalCwd(cwd);
    }

    public static String getProjectRoot() {
        return Cwd.getProjectRoot();
    }

    public static void setProjectRoot(String root) {
        Cwd.setProjectRoot(root);
    }

    public static String getWorkspace() {
        return Cwd.getWorkspace();
    }

    public static void setWorkspace(String path) {
        Cwd.setWorkspace(path);
    }

    public static String getTeamWorkspace() {
        return Cwd.getTeamWorkspace();
    }

    public static void setTeamWorkspace(String path) {
        Cwd.setTeamWorkspace(path);
    }

    public static void initCwd(String cwd) {
        Cwd.initCwd(cwd, null, null, null);
    }

    public static void initCwd(String cwd, String projectRoot, String workspace, String teamWorkspace) {
        Cwd.initCwd(cwd, projectRoot, workspace, teamWorkspace);
    }

    public static CwdState snapshot() {
        return Cwd.getState();
    }

    public static String getTenantRoot() {
        return Cwd.getTenantRoot();
    }

    public static void setTenantRoot(String tenantRoot) {
        Cwd.setTenantRoot(tenantRoot);
    }

    public static boolean isWithinTenantRoot(Path path) {
        return Cwd.isWithinTenantRoot(path);
    }

    public static void reset() {
        Cwd.clear();
    }
}
