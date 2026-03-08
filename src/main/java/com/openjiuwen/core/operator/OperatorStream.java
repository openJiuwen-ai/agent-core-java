/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.operator;

import java.util.Iterator;

/**
 * Iterator-like stream with an explicit close hook for early termination.
 *
 * @param <T> streamed chunk type
 */
public interface OperatorStream<T> extends Iterator<T>, AutoCloseable {

    @Override
    default void close() {
    }
}
