/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.web;

/**
 * Public interface WebHttpFetcher used by the Java parity implementation.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface WebHttpFetcher {
    WebHttpResponse fetch(String method, String url) throws Exception;
}
