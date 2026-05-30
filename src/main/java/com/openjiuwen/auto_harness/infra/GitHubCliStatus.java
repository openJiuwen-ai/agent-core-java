/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

/**
 * Result of the GitHub CLI preflight.
 * <p>
 * Mirrors Python's {@code GitHubCliStatus} in {@code openjiuwen.auto_harness.infra.github_cli}.
 */
public class GitHubCliStatus {

    private boolean available;
    private boolean authenticated;
    private boolean installedNow;
    private String path = "";

    public GitHubCliStatus() {
    }

    public GitHubCliStatus(boolean available, boolean authenticated, boolean installedNow, String path) {
        this.available = available;
        this.authenticated = authenticated;
        this.installedNow = installedNow;
        this.path = path == null ? "" : path;
    }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
    public boolean isInstalledNow() { return installedNow; }
    public void setInstalledNow(boolean installedNow) { this.installedNow = installedNow; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
