/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Real subprocess-backed launcher process.
 * <p>
 * Mirrors Python's {@code subprocess.Popen} process behavior used by launcher helpers.
 */
public final class ProcessLauncherProcess implements LauncherProcess {

    private final Process process;

    public ProcessLauncherProcess(Process process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    @Override
    public Integer poll() {
        if (process.isAlive()) {
            return null;
        }
        return process.exitValue();
    }

    @Override
    public void terminate() {
        process.destroy();
    }

    @Override
    public void waitFor(long timeoutMillis) throws InterruptedException, TimeoutException {
        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("process did not exit within " + timeoutMillis + "ms");
        }
    }

    @Override
    public void kill() {
        process.destroyForcibly();
    }
}
