/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.gateway;

/**
 * Mirrors Python's {@code SandboxEndpoint} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 *
 * @param baseUrl sandbox base URL
 * @param sandboxId sandbox identifier
 */
public record SandboxEndpoint(String baseUrl, String sandboxId) {
}
