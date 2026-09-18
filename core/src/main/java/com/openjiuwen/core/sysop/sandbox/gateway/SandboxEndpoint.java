/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

/**
 * Mirrors Python's {@code SandboxEndpoint} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 *
 * @param baseUrl sandbox base URL
 * @param sandboxId sandbox identifier
 */
public record SandboxEndpoint(String baseUrl, String sandboxId) {

    /** Bean-style accessor for legacy nested providers. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Bean-style accessor for legacy nested providers. */
    public String getSandboxId() {
        return sandboxId;
    }

    /** Legacy builder used by compatibility tests and older call sites. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String baseUrl;
        private String sandboxId;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder sandboxId(String sandboxId) {
            this.sandboxId = sandboxId;
            return this;
        }

        public SandboxEndpoint build() {
            return new SandboxEndpoint(baseUrl, sandboxId);
        }
    }
}
