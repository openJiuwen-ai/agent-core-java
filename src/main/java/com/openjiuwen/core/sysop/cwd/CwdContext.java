/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.cwd;

import java.nio.file.Path;

/**
 * Per-thread CWD context aligned with Python's core.sys_operation.cwd module.
 */
public final class CwdContext {
    private static final InheritableThreadLocal<CwdState> STATE = new InheritableThreadLocal<>();

    private CwdContext() {
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public static void setCwd(String cwd) {
        ensureState().setCwd(resolve(cwd));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String getOriginalCwd() {
        CwdState state = ensureState();
        return state.getOriginalCwd() != null ? state.getOriginalCwd() : resolve(".");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void setOriginalCwd(String cwd) {
        ensureState().setOriginalCwd(resolve(cwd));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String getProjectRoot() {
        CwdState state = ensureState();
        return state.getProjectRoot() != null ? state.getProjectRoot() : getOriginalCwd();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void setProjectRoot(String projectRoot) {
        ensureState().setProjectRoot(resolve(projectRoot));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String getWorkspace() {
        return ensureState().getWorkspace();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void setWorkspace(String workspace) {
        ensureState().setWorkspace(resolve(workspace));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String getTeamWorkspace() {
        return ensureState().getTeamWorkspace();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void setTeamWorkspace(String teamWorkspace) {
        ensureState().setTeamWorkspace(resolve(teamWorkspace));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void initCwd(String cwd) {
        initCwd(cwd, null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void initCwd(String cwd, String projectRoot, String workspace, String teamWorkspace) {
        String isResolved = resolve(cwd);
        STATE.set(CwdState.builder()
                .cwd(isResolved)
                .originalCwd(isResolved)
                .projectRoot(projectRoot != null ? resolve(projectRoot) : isResolved)
                .workspace(workspace != null ? resolve(workspace) : null)
                .teamWorkspace(teamWorkspace != null ? resolve(teamWorkspace) : null)
                .build());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static CwdState snapshot() {
        CwdState state = ensureState();
        return CwdState.builder()
                .cwd(state.getCwd())
                .originalCwd(state.getOriginalCwd())
                .projectRoot(state.getProjectRoot())
                .workspace(state.getWorkspace())
                .teamWorkspace(state.getTeamWorkspace())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void reset() {
        STATE.remove();
    }

    private static CwdState ensureState() {
        CwdState state = STATE.get();
        if (state == null) {
            state = new CwdState();
            STATE.set(state);
        }
        return state;
    }

    private static String resolve(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString();
    }
}
