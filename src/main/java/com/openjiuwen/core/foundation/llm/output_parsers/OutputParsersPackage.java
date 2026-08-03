/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

/**
 * Package bridge for output parser exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.llm.output_parsers} in
 * {@code openjiuwen/core/foundation/llm/output_parsers/__init__.py}.</p>
 */
public final class OutputParsersPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/llm/output_parsers/__init__.py";
    public static final Class<JsonOutputParser> JSON_OUTPUT_PARSER = JsonOutputParser.class;
    public static final Class<MarkdownOutputParser> MARKDOWN_OUTPUT_PARSER = MarkdownOutputParser.class;

    private OutputParsersPackage() {
    }
}
