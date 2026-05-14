/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Minimal process seam for launcher orchestration.
 * <p>
 * Mirrors the subset of Python subprocess process behavior used by launcher helpers.
 */
public interface LauncherProcess {

    Integer poll();

    void terminate();

    void waitFor(long timeoutMillis) throws InterruptedException, java.util.concurrent.TimeoutException;

    void kill();
}
