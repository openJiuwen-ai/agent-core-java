/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import java.util.Iterator;

/**
 * Base class for parsing LLM output into the desired format.
 * <p>
 * Mirrors Python's {@code BaseOutputParser} ABC.
 */
public abstract class BaseOutputParser {

    /**
     * Parse LLM output.
     *
     * @param inputs the assistant message or its content string
     * @return parsed result
     * @throws Exception if parsing fails
     */
    public abstract Object parse(Object inputs) throws Exception;

    /**
     * Parse streaming LLM output.
     *
     * @param streamingInputs an iterator of AssistantMessageChunk or String
     * @return an iterator of parsed fragments
     * @throws Exception if parsing fails
     */
    public abstract Iterator<Object> streamParse(Iterator<?> streamingInputs) throws Exception;
}
