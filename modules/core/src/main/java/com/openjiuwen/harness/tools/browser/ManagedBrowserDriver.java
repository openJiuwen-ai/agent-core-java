/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser;

import java.io.IOException;

/**
 * Public class ManagedBrowserDriver used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ManagedBrowserDriver {
    private final BrowserProfile profile;
    private Process process;
    private boolean isProcessOwned;

    /**
     * ManagedBrowserDriver.
     * 
     * @param profile profile
     * @since 0.1.7
     */
    public ManagedBrowserDriver(BrowserProfile profile) {
        this.profile = profile;
    }

    /**
     * start.
     * 
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public String start() throws IOException {
        if (isEndpointReady()) {
            isProcessOwned = false;
            return profile.getCdpUrl();
        }
        process = new ProcessBuilder("bash", "-lc", "sleep 60").start();
        isProcessOwned = true;
        return profile.getCdpUrl();
    }

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    public void stop() {
        if (process == null || !isProcessOwned) {
            return;
        }
        if (process.isAlive()) {
            process.destroy();
        }
    }

    /**
     * isEndpointReady.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEndpointReady() {
        return false;
    }

    /**
     * isProcessOwned.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isProcessOwned() {
        return isProcessOwned;
    }

    /**
     * process.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Process process() {
        return process;
    }
}
