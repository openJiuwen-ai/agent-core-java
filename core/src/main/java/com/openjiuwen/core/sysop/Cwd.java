/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import java.nio.file.Path;

/**
 * Mirrors Python's module-level cwd helpers in
 * {@code openjiuwen/core/sys_operation/cwd.py}.
 */
public final class Cwd {

    private static final InheritableThreadLocal<CwdState> CWD_STATE = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<String> TENANT_ROOT = new InheritableThreadLocal<>();

    private Cwd() {
    }

    public static String getCwd() {
        CwdState state = state();
        return state.getCwd() != null ? state.getCwd()
                : state.getOriginalCwd() != null ? state.getOriginalCwd()
                : System.getProperty("user.dir");
    }

    public static void setCwd(String cwd) {
        state().setCwd(resolve(cwd));
    }

    public static String getOriginalCwd() {
        CwdState state = state();
        return state.getOriginalCwd() != null ? state.getOriginalCwd() : System.getProperty("user.dir");
    }

    public static void setOriginalCwd(String cwd) {
        state().setOriginalCwd(resolve(cwd));
    }

    public static String getProjectRoot() {
        CwdState state = state();
        return state.getProjectRoot() != null ? state.getProjectRoot() : getOriginalCwd();
    }

    public static void setProjectRoot(String root) {
        state().setProjectRoot(resolve(root));
    }

    public static String getWorkspace() {
        return state().getWorkspace();
    }

    public static void setWorkspace(String path) {
        state().setWorkspace(resolve(path));
    }

    public static String getTeamWorkspace() {
        return state().getTeamWorkspace();
    }

    public static void setTeamWorkspace(String path) {
        state().setTeamWorkspace(resolve(path));
    }

    public static void initCwd(
            String cwd,
            String projectRoot,
            String workspace,
            String teamWorkspace
    ) {
        String resolved = resolve(cwd);
        CwdState state = new CwdState();
        state.setCwd(resolved);
        state.setOriginalCwd(resolved);
        state.setProjectRoot(projectRoot != null ? resolve(projectRoot) : resolved);
        state.setWorkspace(workspace != null ? resolve(workspace) : null);
        state.setTeamWorkspace(teamWorkspace != null ? resolve(teamWorkspace) : null);
        CWD_STATE.set(state);
    }

    public static void initCwd(String cwd) {
        initCwd(cwd, null, null, null);
    }

    public static void initCwd(String cwd, String projectRoot) {
        initCwd(cwd, projectRoot, null, null);
    }

    public static CwdState getState() {
        return state();
    }

    public static void setState(CwdState cwdState) {
        CWD_STATE.set(cwdState);
    }

    public static void clear() {
        CWD_STATE.remove();
        TENANT_ROOT.remove();
    }

    /**
     * getTenantRoot.
     *
     * @return the result
     * @since 0.1.7
     */
    public static String getTenantRoot() {
        return TENANT_ROOT.get();
    }

    /**
     * setTenantRoot.
     *
     * @param tenantRoot tenantRoot
     * @since 0.1.7
     */
    public static void setTenantRoot(String tenantRoot) {
        TENANT_ROOT.set(tenantRoot);
    }

    /**
     * isWithinTenantRoot.
     *
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static boolean isWithinTenantRoot(Path path) {
        String root = getTenantRoot();
        if (root == null) {
            return true;
        }
        Path normalized = path.toAbsolutePath().normalize();
        Path rootPath = Path.of(root).toAbsolutePath().normalize();
        return normalized.startsWith(rootPath);
    }

    private static String resolve(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    private static CwdState state() {
        CwdState current = CWD_STATE.get();
        if (current == null) {
            current = new CwdState();
            CWD_STATE.set(current);
        }
        return current;
    }
}
