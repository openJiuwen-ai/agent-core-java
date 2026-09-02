/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.web;

/**
 * Public interface WebHttpFetcher used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface WebHttpFetcher {
    /**
     * fetch.
     * 
     * @param method method
     * @param url url
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    WebHttpResponse fetch(String method, String url) throws Exception;
}
