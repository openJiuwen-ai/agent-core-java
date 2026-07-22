/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * TodoStorageProvider.
 *
 * @since 0.1.7
 */
public interface TodoStorageProvider {

    /**
     * typeName.
     *
     * @return the result
     * @since 0.1.7
     */
    String typeName();

    /**
     * create.
     *
     * @param conf conf
     * @return the result
     * @since 0.1.7
     */
    TodoStorage create(Map<String, Object> conf);
}
