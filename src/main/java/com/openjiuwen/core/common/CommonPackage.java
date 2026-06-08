/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.schema.Param;

import java.util.List;

/**
 * Package bridge for the common package exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/common/__init__.py}.
 * </p>
 */
public final class CommonPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/common/__init__.py";
    public static final Class<BaseCard> BASE_CARD = BaseCard.class;
    public static final Class<Param> PARAM = Param.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("BaseCard", "Param");

    private CommonPackage() {
    }
}
