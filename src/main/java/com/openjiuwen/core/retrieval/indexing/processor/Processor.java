/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code Processor} in
 * {@code openjiuwen/core/retrieval/indexing/processor/base.py}.
 *
 * @param <O> async result type
 */
@FunctionalInterface
public interface Processor<O> {

    CompletableFuture<O> process(Object... args);
}
