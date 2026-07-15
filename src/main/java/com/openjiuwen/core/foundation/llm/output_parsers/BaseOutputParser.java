/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import java.util.Iterator;

/**
 * Base class for parsing LLM output into the desired format.
 * <p>
 * Mirrors Python's {@code BaseOutputParser} ABC.
 * 
 * @since 0.1.7
 */
public abstract class BaseOutputParser {
    /**
     * parse.
     * 
     * @param inputs inputs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract Object parse(Object inputs) throws Exception;

    /**
     * streamParse.
     * 
     * @param streamingInputs streamingInputs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract Iterator<Object> streamParse(Iterator<?> streamingInputs) throws Exception;
}
