// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * LLM输出解析器抽象基类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/output_parsers/output_parser.py
 * 
 * @param <T> 解析结果类型
 */
public abstract class BaseOutputParser<T> {

    /**
     * 异步解析LLM输出。
     * 
     * @param input 输入（可以是字符串或AssistantMessage）
     * @return 解析结果的CompletableFuture
     */
    public abstract CompletableFuture<T> parse(Object input);

    /**
     * 流式解析LLM输出。
     * 
     * @param streamingInputs 流式输入迭代器
     * @return 解析结果迭代器
     */
    public abstract Iterator<T> streamParse(Iterator<?> streamingInputs);
}

