/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code BaseOutputParser} in
 * {@code openjiuwen/core/foundation/llm/output_parsers/output_parser.py}.
 */
public abstract class BaseOutputParser {

    public abstract CompletableFuture<Object> parse(Object inputs);

    public abstract Iterator<Object> streamParse(Iterator<?> streamingInputs);
}
