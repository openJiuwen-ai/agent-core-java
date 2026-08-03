/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputParsersPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals(
                "openjiuwen/core/foundation/llm/output_parsers/__init__.py",
                OutputParsersPackage.PYTHON_MODULE);
        assertEquals(JsonOutputParser.class, OutputParsersPackage.JSON_OUTPUT_PARSER);
        assertEquals(MarkdownOutputParser.class, OutputParsersPackage.MARKDOWN_OUTPUT_PARSER);
    }
}
