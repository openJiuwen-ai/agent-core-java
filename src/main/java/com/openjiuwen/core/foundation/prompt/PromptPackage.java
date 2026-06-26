/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt;

import java.util.List;

/**
 * Package bridge for prompt facade exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.prompt} package facade in
 * {@code openjiuwen/core/foundation/prompt/__init__.py}.</p>
 */
public final class PromptPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/prompt/__init__.py";

    public static final List<String> PROMPT_TEMPLATE_CLASSES = List.of("PromptTemplate");

    public static final List<String> EXPORTED_SYMBOLS = PROMPT_TEMPLATE_CLASSES;

    private PromptPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }
}
