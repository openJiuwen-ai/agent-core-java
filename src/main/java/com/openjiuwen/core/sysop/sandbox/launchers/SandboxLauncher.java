/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import com.openjiuwen.core.sysop.sandbox.gateway.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxStatus;

/**
 * Base interface for sandbox lifecycle management.
 * <p>
 * All methods except launch are optional (no-op by default) so that
 * implementors only override what their runtime actually supports.
 * <p>
 * <b>Important</b>: launch() is the only entry that returns a LaunchedSandbox
 * descriptor. pause(), resume(), and delete() all operate on that descriptor
 * but do not create new ones. The caller (ContainerManager) is responsible for
 * deciding which operation to invoke and persisting the descriptor.
 * <p>
 * Mirrors Python's {@code SandboxLauncher} in {@code sandbox/launchers/base.py}.
 */
public abstract class SandboxLauncher {

    /**
     * Start (or resume) a sandbox and return its descriptor.
     * <p>
     * Implementations are strongly encouraged to use sandboxId as the container
     * name/label so that a paused sandbox can be found and unpaused on the next
     * launch() call instead of being re-created.
     *
     * @param config           the launcher configuration (as Object for flexibility)
     * @param timeoutSeconds   timeout for the launch operation
     * @param isolationKey     optional isolation key for container naming
     * @return the LaunchedSandbox descriptor
     * @throws Exception if launch fails
     */
    public abstract LaunchedSandbox launch(Object config, int timeoutSeconds, String isolationKey) throws Exception;

    /**
     * Suspend the sandbox to preserve state without consuming compute.
     * <p>
     * Default implementation is no-op.
     *
     * @param sandboxId the sandbox identifier
     * @throws Exception if pause fails
     */
    public void pause(String sandboxId) throws Exception {
        // No-op by default
    }

    /**
     * Resume a previously paused sandbox.
     * <p>
     * Default implementation is no-op.
     *
     * @param sandboxId the sandbox identifier
     * @throws Exception if resume fails
     */
    public void resume(String sandboxId) throws Exception {
        // No-op by default
    }

    /**
     * Permanently destroy the sandbox and release its resources.
     * <p>
     * Default implementation is no-op.
     *
     * @param sandboxId the sandbox identifier
     * @throws Exception if delete fails
     */
    public void delete(String sandboxId) throws Exception {
        // No-op by default
    }

    /**
     * Check the current status of a sandbox.
     * <p>
     * Default implementation returns RUNNING.
     *
     * @param sandboxId the sandbox identifier
     * @return the current sandbox status
     * @throws Exception if check fails
     */
    public SandboxStatus checkStatus(String sandboxId) throws Exception {
        return SandboxStatus.RUNNING;
    }
}