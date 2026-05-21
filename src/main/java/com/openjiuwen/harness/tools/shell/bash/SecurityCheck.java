/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

/**
 * Result of a tool-layer security check.
 *
 * <p>Mirrors Python's SecurityCheck in
 * {@code openjiuwen.harness.tools.shell.bash._security}.
 */
public class SecurityCheck {

    private final boolean blocked;
    private final String reason;
    private final String warning;

    public SecurityCheck(boolean blocked, String reason, String warning) {
        this.blocked = blocked;
        this.reason = reason;
        this.warning = warning;
    }

    public SecurityCheck(boolean blocked, String reason) {
        this.blocked = blocked;
        this.reason = reason;
        this.warning = null;
    }

    public SecurityCheck(boolean blocked) {
        this.blocked = blocked;
        this.reason = null;
        this.warning = null;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getReason() {
        return reason;
    }

    public String getWarning() {
        return warning;
    }
}