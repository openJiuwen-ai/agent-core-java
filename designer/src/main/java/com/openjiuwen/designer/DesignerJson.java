/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Shared Jackson mapper for designer graph and run documents.
 *
 * @since 2026-11-06
 */
public final class DesignerJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private DesignerJson() {
    }

    /**
     * Return the shared mapper.
     *
     * @return shared mapper, never null
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
