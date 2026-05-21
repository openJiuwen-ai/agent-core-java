/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import java.util.concurrent.CompletableFuture;

/**
 * Base interface for code execution operations.
 */
public interface BaseCodeProvider {

    /**
     * Execute code in sandbox.
     *
     * @param code Code to execute.
     * @param language Programming language.
     * @return CompletableFuture with execution result.
     */
    CompletableFuture<AioProvider.ExecuteResult> executeCode(String code, String language);
}