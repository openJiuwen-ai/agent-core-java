/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.cwd;

import java.nio.file.Path;

/**
 * Per-thread CWD context aligned with Python's core.sys_operation.cwd module.
 * 
 * @since 0.1.7
 */
public final class CwdContext {
    private static final InheritableThreadLocal<CwdState> STATE = new InheritableThreadLocal<>();

    /**
     * CwdContext.
     * 
     * @since 0.1.7
     */
    private CwdContext() {
    }

    /**
     * getCwd.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getCwd() {
        CwdState state = ensureState();
        if (state.getCwd() != null) {
            return state.getCwd();
        }
        if (state.getOriginalCwd() != null) {
            return state.getOriginalCwd();
        }
        return resolve(".");
    }

    /**
     * setCwd.
     * 
     * @param cwd cwd
     * @since 0.1.7
     */
    public static void setCwd(String cwd) {
        ensureState().setCwd(resolve(cwd));
    }

    /**
     * getOriginalCwd.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getOriginalCwd() {
        CwdState state = ensureState();
        return state.getOriginalCwd() != null ? state.getOriginalCwd() : resolve(".");
    }

    /**
     * setOriginalCwd.
     * 
     * @param cwd cwd
     * @since 0.1.7
     */
    public static void setOriginalCwd(String cwd) {
        ensureState().setOriginalCwd(resolve(cwd));
    }

    /**
     * getProjectRoot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getProjectRoot() {
        CwdState state = ensureState();
        return state.getProjectRoot() != null ? state.getProjectRoot() : getOriginalCwd();
    }

    /**
     * setProjectRoot.
     * 
     * @param projectRoot projectRoot
     * @since 0.1.7
     */
    public static void setProjectRoot(String projectRoot) {
        ensureState().setProjectRoot(resolve(projectRoot));
    }

    /**
     * getWorkspace.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getWorkspace() {
        return ensureState().getWorkspace();
    }

    /**
     * setWorkspace.
     * 
     * @param workspace workspace
     * @since 0.1.7
     */
    public static void setWorkspace(String workspace) {
        ensureState().setWorkspace(resolve(workspace));
    }

    /**
     * getTeamWorkspace.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getTeamWorkspace() {
        return ensureState().getTeamWorkspace();
    }

    /**
     * setTeamWorkspace.
     * 
     * @param teamWorkspace teamWorkspace
     * @since 0.1.7
     */
    public static void setTeamWorkspace(String teamWorkspace) {
        ensureState().setTeamWorkspace(resolve(teamWorkspace));
    }

    /**
     * initCwd.
     * 
     * @param cwd cwd
     * @since 0.1.7
     */
    public static void initCwd(String cwd) {
        initCwd(cwd, null, null, null);
    }

    /**
     * initCwd.
     * 
     * @param cwd cwd
     * @param projectRoot projectRoot
     * @param workspace workspace
     * @param teamWorkspace teamWorkspace
     * @since 0.1.7
     */
    public static void initCwd(String cwd, String projectRoot, String workspace, String teamWorkspace) {
        String isResolved = resolve(cwd);
        STATE.set(CwdState.builder().cwd(isResolved).originalCwd(isResolved)
                .projectRoot(projectRoot != null ? resolve(projectRoot) : isResolved)
                .workspace(workspace != null ? resolve(workspace) : null)
                .teamWorkspace(teamWorkspace != null ? resolve(teamWorkspace) : null).build());
    }

    /**
     * snapshot.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static CwdState snapshot() {
        CwdState state = ensureState();
        return CwdState.builder().cwd(state.getCwd()).originalCwd(state.getOriginalCwd())
                .projectRoot(state.getProjectRoot()).workspace(state.getWorkspace())
                .teamWorkspace(state.getTeamWorkspace()).build();
    }

    /**
     * reset.
     * 
     * @since 0.1.7
     */
    public static void reset() {
        STATE.remove();
    }

    /**
     * ensureState.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static CwdState ensureState() {
        CwdState state = STATE.get();
        if (state == null) {
            state = new CwdState();
            STATE.set(state);
        }
        return state;
    }

    /**
     * resolve.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static String resolve(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString();
    }
}
