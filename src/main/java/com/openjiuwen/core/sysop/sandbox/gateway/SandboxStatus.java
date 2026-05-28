/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

/**
 * Sandbox lifecycle status enum.
 * <p>
 * Mirrors Python's {@code SandboxStatus} enum in {@code sandbox/gateway/sandbox_store.py}.
 */
public enum SandboxStatus {
    /** Sandbox is actively running and accepting requests. */
    RUNNING("running"),
    
    /** Sandbox is paused, preserving state but not consuming compute. */
    PAUSED("paused"),
    
    /** Sandbox has been killed or stopped. */
    KILLED("killed");

    private final String value;

    SandboxStatus(String value) {
        this.value = value;
    }

    /**
     * Get the string value of this status.
     *
     * @return the status string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string value to SandboxStatus.
     *
     * @param value the string value
     * @return the matching SandboxStatus, or null if not found
     */
    public static SandboxStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SandboxStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}