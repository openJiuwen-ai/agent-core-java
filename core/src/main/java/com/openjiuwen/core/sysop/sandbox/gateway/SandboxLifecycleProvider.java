/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import java.util.concurrent.CompletableFuture;

/**
 * Provider contract for lifecycle operations on provider-managed sandboxes.
 *
 * @since 0.1.17
 */
public interface SandboxLifecycleProvider {

    /**
     * Returns the provider sandbox ID currently associated with this provider.
     *
     * @return the physical provider ID, or {@code null} if it has not been resolved
     * @since 0.1.17
     */
    String getSandboxId();

    /**
     * Returns the service base URL owning the current physical sandbox.
     *
     * @return service base URL
     * @since 0.1.17
     */
    String getSandboxBaseUrl();

    /**
     * Deletes the provider-managed sandbox with the supplied physical ID.
     *
     * @param sandboxId physical provider ID to delete
     * @return a future completed when deletion finishes
     * @since 0.1.17
     */
    CompletableFuture<Void> deleteSandbox(String sandboxId);
}
